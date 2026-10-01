package com.playersignal.analysis;

import com.playersignal.game.GameRepository;
import com.playersignal.shared.ApiException;
import com.playersignal.shared.DatabaseJobLock;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {
    public record Status(boolean available, String provider, String model, String promptVersion, int maxReviewsPerRun,
                         AnalysisRepository.Counts counts, AnalysisRepository.Run latestRun) {}
    private final DataSource dataSource;
    private final GameRepository games;
    private final AnalysisRepository repository;
    private final ReviewAnalyzer analyzer;
    private final AnalysisSettings settings;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Semaphore capacity = new Semaphore(1);
    public AnalysisService(DataSource dataSource, GameRepository games, AnalysisRepository repository, ReviewAnalyzer analyzer, AnalysisSettings settings) {
        this.dataSource = dataSource; this.games = games; this.repository = repository; this.analyzer = analyzer; this.settings = settings;
    }
    public Status status(UUID gameId) {
        games.get(gameId);
        return new Status(analyzer.available(), AnalysisSettings.PROVIDER, settings.model(), AnalysisSettings.PROMPT_VERSION,
                settings.maxReviews(), repository.counts(gameId), repository.latest(gameId));
    }
    public AnalysisRepository.Run start(UUID gameId, boolean retryFailed) {
        var game = games.get(gameId);
        if (!analyzer.available()) throw new ApiException(503, "ANALYSIS_NOT_CONFIGURED", "Configure ANALYSIS_PROVIDER=openai and OPENAI_API_KEY on the backend. The separate demo works without a key.");
        if (!capacity.tryAcquire()) throw new ApiException(409, "ANALYSIS_BUSY", "An analysis is already running. Wait and retry.");
        DatabaseJobLock lock = null;
        AnalysisRepository.Run run = null;
        try {
            lock = DatabaseJobLock.acquire(dataSource, game.steamAppId());
            if (lock == null) throw new ApiException(409, "ANALYSIS_BUSY", "This game's analysis is already running.");
            repository.recover(lock.jdbc, gameId);
            List<AnalysisRepository.Source> candidates = repository.candidates(gameId, retryFailed);
            run = repository.createRun(gameId);
            var jobLock = lock; var jobRun = run;
            worker.execute(() -> process(jobRun, candidates, jobLock));
            return run;
        } catch (Exception error) {
            if (run != null) repository.finish(run.id(), "FAILED", "Could not start analysis.");
            if (lock != null) try { lock.close(); } catch (Exception closeError) { LoggerFactory.getLogger(getClass()).error("Analysis lock cleanup failed", closeError); }
            capacity.release();
            if (error instanceof ApiException api) throw api;
            throw new ApiException(503, "ANALYSIS_UNAVAILABLE", "Could not start analysis. Retry later.");
        }
    }
    private void process(AnalysisRepository.Run run, List<AnalysisRepository.Source> sources, DatabaseJobLock lock) {
        try (lock) {
            for (var source : sources) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                UUID id = repository.begin(lock.jdbc, source, run.id());
                String skip = AnalysisPolicy.skipReason(source.text());
                if (skip != null) { complete(lock, id, run.id(), "SKIPPED", null, null, skip, null); continue; }
                var cached = repository.cached(lock.jdbc, run.gameId(), source);
                if (cached != null) {
                    var output = new ReviewAnalyzer.Output(cached.result().validate(source.text()), cached.responseModel(), 0, 0);
                    complete(lock, id, run.id(), "SUCCEEDED", output, cached.id(), null, null); continue;
                }
                for (int attempt = 1; attempt <= 3; attempt++) {
                    repository.attempt(lock.jdbc, id);
                    try {
                        var output = analyzer.analyze(new ReviewAnalyzer.Input(source.text(), source.language()));
                        output.classification().validate(source.text());
                        complete(lock, id, run.id(), "SUCCEEDED", output, null, null, null);
                        break;
                    } catch (AnalysisFailure error) {
                        String message = error.code + ": " + error.getMessage();
                        if (attempt < 3 && error.retryable) {
                            repository.retryError(lock.jdbc, id, message);
                            Thread.sleep((long) settings.retryDelayMs() << (attempt - 1));
                        } else {
                            complete(lock, id, run.id(), "FAILED", null, null, null, message);
                            if (error.stopRun) { repository.finish(run.id(), "FAILED", message); return; }
                            break;
                        }
                    }
                }
            }
            var result = repository.getRun(run.id());
            String status = result.failed() > 0 ? "COMPLETED_WITH_ERRORS" : repository.counts(run.gameId()).pending() > 0 ? "PARTIAL" : "COMPLETED";
            repository.finish(run.id(), status, null);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            LoggerFactory.getLogger(getClass()).error("Analysis failed runId={} type={}", run.id(), error.getClass().getSimpleName());
            repository.interrupt(run.id());
            // A subsequent start/startup recovers any in-flight row under the database lock.
        } finally { capacity.release(); }
    }
    private void complete(DatabaseJobLock lock, UUID id, UUID runId, String status, ReviewAnalyzer.Output output,
                          UUID cachedFrom, String skipReason, String error) throws java.sql.SQLException {
        lock.connection.setAutoCommit(false);
        try {
            repository.complete(lock.jdbc, id, runId, status, output, cachedFrom, skipReason, error);
            lock.connection.commit();
        } catch (RuntimeException | java.sql.SQLException failure) { lock.connection.rollback(); throw failure; }
        finally { lock.connection.setAutoCommit(true); }
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        for (UUID gameId : repository.runningGames()) {
            try (var lock = DatabaseJobLock.acquire(dataSource, games.get(gameId).steamAppId())) {
                if (lock != null) repository.recover(lock.jdbc, gameId);
            } catch (Exception error) { LoggerFactory.getLogger(getClass()).error("Analysis recovery failed gameId={}", gameId); }
        }
    }
    @PreDestroy public void shutdown() { worker.shutdownNow(); }
}
