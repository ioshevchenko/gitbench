package gitbench.annotate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** The date of an annotated line, as the IntelliJ DateFormatUtil.formatPrettyDate: Today, Yesterday, or the date. */
public final class PrettyDate {
  /** The date format of the Git Log table. */
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private PrettyDate() {
  }

  public static String format(Instant time, Clock clock) {
    LocalDate date = time.atZone(clock.getZone()).toLocalDate();
    LocalDate today = LocalDate.now(clock);
    if (date.equals(today)) {
      return "Today";
    }
    if (date.equals(today.minusDays(1))) {
      return "Yesterday";
    }
    return DATE.format(date);
  }
}
