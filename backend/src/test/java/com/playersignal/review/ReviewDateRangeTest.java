package com.playersignal.review;
import com.playersignal.shared.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReviewDateRangeTest {
 @Test void usesUtcBoundariesAcrossLeapDaysAndDst(){var r=ReviewDateRange.parse("2024-02-29","2024-03-31");assertThat(r.start().toString()).isEqualTo("2024-02-29T00:00:00Z");assertThat(r.endExclusive().toString()).isEqualTo("2024-04-01T00:00:00Z");}
 @Test void supportsOpenEndedAndEmptyRanges(){assertThat(ReviewDateRange.parse("",null).start()).isNull();assertThat(ReviewDateRange.parse(null,"2026-10-01").endExclusive().toString()).isEqualTo("2026-10-02T00:00:00Z");assertThat(ReviewDateRange.parse("2026-10-01",null).endExclusive()).isNull();}
 @Test void rejectsInvalidDatesAndReversedRanges(){for(String date:new String[]{"2026-02-29","2026-13-01","2026-1-01","0000-01-01","2026-10-01T00:00:00Z","10000-01-01"})assertThatThrownBy(()->ReviewDateRange.parse(date,null)).isInstanceOf(ApiException.class);assertThatThrownBy(()->ReviewDateRange.parse("2026-10-02","2026-10-01")).isInstanceOf(ApiException.class);}
}
