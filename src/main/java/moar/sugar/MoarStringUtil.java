package moar.sugar;

import static java.lang.String.join;
import com.google.common.base.CharMatcher;

/**
 * Yet another String Utility
 *
 * @author Mark Farnsworth
 */
public class MoarStringUtil {
  public static String asString(final Object o) {
    return o == null ? null : o.toString();
  }

  public static String cleanWithOnly(final CharMatcher matcher,
      final String dirty) {
    final StringBuilder cleanBuilder = new StringBuilder();
    for (int i = 0; i < dirty.length(); i++) {
      char c = dirty.charAt(i);
      if (matcher.matches(c)) {
        cleanBuilder.append(c);
      } else {
        c = ' ';
      }
    }
    final String clean = cleanBuilder.toString();
    return clean;
  }

  public static String cleanWithOnlyAscii(final String dirty) {
    return cleanWithOnly(CharMatcher.ascii(), dirty);
  }

  public static String toLowerCase(final String string) {
    return string == null ? null : string.toLowerCase();
  }

  public static String toSnakeCase(final String string) {
    final String[] nameParts = string.split("(?=\\p{Upper})");
    return join("_", nameParts).toLowerCase();
  }

  public static String toUpperCase(final String string) {
    return string == null ? null : string.toUpperCase();
  }

  public static String truncate(final String string, final int size) {
    return string.substring(0, Math.min(string.length(), size));
  }

}
