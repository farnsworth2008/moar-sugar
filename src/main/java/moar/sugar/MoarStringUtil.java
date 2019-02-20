package moar.sugar;

import static java.lang.Math.min;
import static java.lang.String.join;
import com.google.common.base.CharMatcher;

/**
 * Yet another String Utility
 *
 * @author Mark Farnsworth
 */
public class MoarStringUtil {

  @SuppressWarnings("javadoc")
  public static String asString(Object o) {
    return o == null ? null : o.toString();
  }

  /**
   * Replace targeted characters with blanks.
   *
   * @param matcher
   * @param dirty
   *   Input string that may have content that matches the matcher.
   * @return String with characters that match the matcher replaced with space.
   */
  public static String cleanWithOnly(CharMatcher matcher, String dirty) {
    StringBuilder cleanBuilder = new StringBuilder();
    for (int i = 0; i < dirty.length(); i++) {
      char c = dirty.charAt(i);
      if (matcher.matches(c)) {
        cleanBuilder.append(c);
      } else {
        c = ' ';
      }
    }
    String clean = cleanBuilder.toString();
    return clean;
  }

  /**
   * Clean a string so it only contains ASCII chars.
   *
   * @param dirty
   * @return string
   */
  public static String cleanWithOnlyAscii(String dirty) {
    return cleanWithOnly(CharMatcher.ascii(), dirty);
  }

  /**
   * @param string
   * @return lower case string.
   */
  public static String toLowerCase(String string) {
    return string == null ? null : string.toLowerCase();
  }

  /**
   * @param string
   * @return snake case string.
   */
  public static String toSnakeCase(String string) {
    if (string == null) {
      return null;
    }
    String[] nameParts = string.split("(?=\\p{Upper})");
    return join("_", nameParts).toLowerCase();
  }

  /**
   * @param string
   * @return upper case string.
   */
  public static String toUpperCase(String string) {
    return string == null ? null : string.toUpperCase();
  }

  /**
   * @param string
   * @param size
   * @return truncated string
   */
  public static String truncate(String string, int size) {
    if (string == null) {
      return null;
    }
    return string.substring(0, min(string.length(), size));
  }

}
