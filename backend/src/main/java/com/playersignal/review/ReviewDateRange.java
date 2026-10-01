package com.playersignal.review;

import com.playersignal.shared.ApiException;
import java.time.*;
import java.time.format.DateTimeParseException;

/** Inclusive calendar dates interpreted at UTC midnight; SQL uses an exclusive upper bound. */
public record ReviewDateRange(Instant start, Instant endExclusive) {
    public static ReviewDateRange parse(String from, String to) {
        LocalDate start = date(from), end = date(to);
        if (start != null && end != null && start.isAfter(end)) throw invalid();
        return new ReviewDateRange(start == null ? null : start.atStartOfDay(ZoneOffset.UTC).toInstant(),
            end == null ? null : end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }
    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
        try {
            LocalDate result = LocalDate.parse(value);
            if (result.getYear() < 1) throw invalid();
            return result;
        } catch (DateTimeParseException e) { throw invalid(); }
    }
    private static ApiException invalid() {
        return new ApiException(400, "INVALID_DATE_RANGE", "Use valid dates in YYYY-MM-DD format (years 0001–9999), with From on or before To. Dates use UTC.");
    }
}
