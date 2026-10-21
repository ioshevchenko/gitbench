package gitbench.annotate;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PrettyDateTest {
  private static final ZoneId ZONE = ZoneOffset.ofHours(3);

  private static String format(LocalDateTime date, LocalDateTime now) {
    return PrettyDate.format(date.atZone(ZONE).toInstant(), Clock.fixed(now.atZone(ZONE).toInstant(), ZONE));
  }

  @Test
  void today() {
    assertThat(format(LocalDateTime.of(2026, 10, 22, 0, 5), LocalDateTime.of(2026, 10, 22, 23, 0))).isEqualTo("Today");
  }

  @Test
  void yesterday() {
    assertThat(format(LocalDateTime.of(2026, 10, 21, 23, 59), LocalDateTime.of(2026, 10, 22, 0, 1))).isEqualTo("Yesterday");
  }

  @Test
  void yesterdayAcrossNewYear() {
    assertThat(format(LocalDateTime.of(2025, 12, 31, 12, 0), LocalDateTime.of(2026, 1, 1, 9, 0))).isEqualTo("Yesterday");
  }

  @Test
  void olderDateIsDayMonthYear() {
    assertThat(format(LocalDateTime.of(2026, 10, 9, 2, 41), LocalDateTime.of(2026, 10, 22, 9, 0))).isEqualTo("09.10.2026");
  }
}
