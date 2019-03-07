package moar.sugar;

import static moar.sugar.Sugar.safely;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Yet another Date Utility
 *
 * @author Mark Farnsworth
 */
public class MoarDateUtil {

  public static SafeResult<LocalDate> parse8601Date(String string) {
    return safely(() -> OffsetDateTime.parse(string).toLocalDate());
  }

  public static LocalDate toLocalDate(Date date) {
    return toLocalDate(date, ZoneId.systemDefault());
  }

  public static LocalDate toLocalDate(Date date, ZoneId timeZone) {
    return date == null ? null : date.toInstant().atZone(timeZone).toLocalDate();
  }

}
