package com.playersignal.steam;

import com.playersignal.game.GameRepository;
import com.playersignal.review.ReviewRepository;
import com.playersignal.shared.ApiException;
import com.playersignal.shared.DatabaseJobLock;
import jakarta.annotation.PreDestroy;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {
    private static final Logger LOG = LoggerFactory.getLogger(IngestionService.class);
    private final com.playersignal.usage.UsageService usage;
    private final DataSource dataSource;
    private final GameRepository games;
    private final ReviewRepository reviews;
    private final IngestionRepository runs;
    private final SteamClient steam;
    private final int maxPages;
    private final int pageDelayMillis;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Semaphore capacity = new Semaphore(2);

    public IngestionService(DataSource dataSource, GameRepository games, ReviewRepository reviews,
                            IngestionRepository runs, SteamClient steam, com.playersignal.usage.UsageService usage,
                            @Value("${playersignal.ingestion.max-pages:10}") int maxPages,
                            @Value("${playersignal.ingestion.page-delay-ms:500}") int pageDelayMillis) {
        if (maxPages < 1 || maxPages > 1000 || pageDelayMillis < 0) throw new IllegalArgumentException("Invalid ingestion limits");
        this.usage=usage;this.dataSource = dataSource; this.games = games; this.reviews = reviews;
        this.runs = runs; this.steam = steam; this.maxPages = maxPages; this.pageDelayMillis = pageDelayMillis;
    }
    public IngestionRepository.Run start(UUID gameId) {
        var game = games.get(gameId);
        if (!capacity.tryAcquire()) throw new ApiException(409, "IMPORT_BUSY", "Two imports are already running. Wait and retry.");
        DatabaseJobLock lock = null;
        IngestionRepository.Run run = null;
        try {
            lock = DatabaseJobLock.acquire(dataSource, -game.steamAppId());
            if (lock == null) throw new ApiException(409, "SYNC_IN_PROGRESS", "This game already has an import running.");
            usage.reserve(usage.workspaceForGame(gameId),gameId,null,"SYNC",null);
            runs.failInterrupted(gameId);
            run = runs.create(gameId);
            DatabaseJobLock workerLock = lock;
            IngestionRepository.Run workerRun = run;
            workers.execute(() -> importPages(workerRun, game.steamAppId(), workerLock));
            return run;
        } catch (Exception error) {
            if (run != null) runs.finish(run.id(), "FAILED", "Could not start the import. Retry later.");
            if (lock != null) try { lock.close(); } catch (Exception closeError) { LOG.error("Lock cleanup failed", closeError); }
            capacity.release();
            if (error instanceof ApiException api) throw api;
            throw new ApiException(503, "IMPORT_UNAVAILABLE", "Could not start the import. Retry later.");
        }
    }
    private void importPages(IngestionRepository.Run run, long appId, DatabaseJobLock lock) {
        try (lock) {
            String cursor = run.nextCursor();
            var seen = new HashSet<String>(); seen.add(cursor);
            for (int page = 0; page < maxPages; page++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                var source = steam.reviews(appId, cursor);
                if (source.reviews().isEmpty()) { runs.finish(run.id(), "COMPLETED", null); return; }
                if (source.cursor() == null || source.cursor().isBlank() || !seen.add(source.cursor()))
                    throw new ApiException(502, "STEAM_CURSOR_STALLED", "Steam repeated its pagination cursor. Retry later; saved reviews are still available.");
                lock.connection.setAutoCommit(false);
                try {
                    int[] counts = reviews.upsert(lock.jdbc, run.gameId(), source.reviews());
                    runs.progress(lock.jdbc, run.id(), source.reviews().size(), counts[0], counts[1], source.cursor());
                    lock.connection.commit();
                } catch (Exception error) { lock.connection.rollback(); throw error; }
                finally { lock.connection.setAutoCommit(true); }
                cursor = source.cursor();
                if (page + 1 < maxPages) Thread.sleep(pageDelayMillis);
            }
            runs.finish(run.id(), "PARTIAL", null);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.error("Steam import failed runId={} gameId={}", run.id(), run.gameId(), error);
            runs.finish(run.id(), "FAILED", error instanceof ApiException ? error.getMessage() :
                    "Import interrupted or storage unavailable. Retry to resume from the last saved page.");
        } finally { capacity.release(); }
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        for (UUID gameId : runs.runningGames()) {
            try (var lock = DatabaseJobLock.acquire(dataSource, -games.get(gameId).steamAppId())) {
                if (lock != null) runs.failInterrupted(gameId);
            } catch (Exception error) { LOG.error("Import recovery failed gameId={}", gameId, error); }
        }
    }
    @PreDestroy public void shutdown() { workers.shutdownNow(); }
}
