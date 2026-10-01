package com.playersignal.comparison;

import com.playersignal.game.GameRepository;
import com.playersignal.review.ReviewDateRange;
import com.playersignal.overview.OverviewService;
import com.playersignal.shared.ApiException;
import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ComparisonService {
    public record Window(LocalDate from,LocalDate to,long reviews,long recommended,Double recommendationRate) {}
    public record Comparison(GameRepository.Game game,LocalDate date,int days,Instant calculatedAt,long imported,
        Window before,Window after,Double reviewChangePercent,Double recommendationChangePoints) {}
    private final JdbcTemplate jdbc;private final GameRepository games;
    public ComparisonService(JdbcTemplate jdbc,GameRepository games){this.jdbc=jdbc;this.games=games;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Comparison get(UUID game,String date,int days,Instant now){
        var selected=games.get(game);OverviewService.validateDays(days);
        var range=ReviewDateRange.parse(date,date);
        if(range.start()==null)throw new ApiException(400,"INVALID_DATE","Choose a comparison date.");
        LocalDate pivot=range.start().atZone(ZoneOffset.UTC).toLocalDate(),start=pivot.minusDays(days),end=pivot.plusDays(days);
        if(start.getYear()<1||end.getYear()>9999)throw new ApiException(400,"INVALID_DATE","Comparison dates are outside the supported range.");
        if(end.atStartOfDay(ZoneOffset.UTC).toInstant().isAfter(now))throw new ApiException(400,"INCOMPLETE_PERIOD","Choose an earlier date: both comparison windows must contain complete UTC days.");
        var counts=jdbc.queryForObject("""
            SELECT count(*) FILTER(WHERE created_at_steam<?),count(*) FILTER(WHERE created_at_steam<? AND voted_up),
            count(*) FILTER(WHERE created_at_steam>=?),count(*) FILTER(WHERE created_at_steam>=? AND voted_up)
            FROM playersignal.review WHERE game_id=? AND created_at_steam>=? AND created_at_steam<?
            """,(rs,n)->new long[]{rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4)},
            pivot.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),pivot.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),pivot.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),pivot.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),game,start.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),end.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime());
        long imported=jdbc.queryForObject("SELECT count(*) FROM playersignal.review WHERE game_id=?",Long.class,game);
        var metrics=OverviewService.metrics(counts[2],counts[3],counts[0],counts[1]);
        return new Comparison(selected,pivot,days,now,imported,
            new Window(start,pivot.minusDays(1),counts[0],counts[1],metrics.previousPositiveRatio()),
            new Window(pivot,end.minusDays(1),counts[2],counts[3],metrics.positiveRatio()),metrics.reviewChangePercent(),metrics.positiveChangePoints());
    }
}
