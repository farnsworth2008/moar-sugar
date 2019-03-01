package moar.sugar;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static java.lang.Math.min;
import static java.lang.String.join;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.swallow;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import org.apache.commons.io.IOUtils;
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
   *   Matcher for characters that should be replaced with space.
   * @param dirty
   *   Input string that may have content that matches the matcher.
   * @return String with characters that match the matcher replaced with space.
   */
  public static String cleanWithOnly(CharMatcher matcher, String dirty) {
    return replaceChars(matcher, dirty, ' ');
  }

  /**
   * Clean a string so it only contains ASCII chars.
   *
   * @param string
   *   String of content to be cleaned.
   * @return string
   */
  public static String cleanWithOnlyAscii(String string) {
    return string == null ? null : cleanWithOnly(CharMatcher.ascii(), string);
  }

  public static String fileContentsAsString(File ignoreFile) {
    return require(() -> {
      try (InputStream is = new FileInputStream(ignoreFile)) {
        return streamToString(is);
      }
    });
  }

  /**
   * Replace targeted characters with blanks.
   *
   * @param matcher
   *   Matcher for characters that should be replaced with space.
   * @param dirty
   *   Input string that may have content that matches the matcher.
   * @param replacement
   *   Replacement character.
   * @return String with characters that match the matcher replaced with space.
   */
  private static String replaceChars(CharMatcher matcher, String string, char replacement) {
    if (string == null) {
      return null;
    }
    StringBuilder cleanBuilder = new StringBuilder();
    for (int i = 0; i < string.length(); i++) {
      char c = string.charAt(i);
      if (matcher.matches(c)) {
        cleanBuilder.append(c);
      } else {
        c = replacement;
      }
    }
    return cleanBuilder.toString();
  }

  public static String streamToString(InputStream is) {
    return swallow(() -> IOUtils.toString(is, Charset.defaultCharset()));
  }

  public static String toCamelCase(String name) {
    String camelName = UPPER_CAMEL.to(LOWER_CAMEL, name);
    return camelName;
  }

  public static ArrayList<String> toLineList(String statusOutput) {
    return swallow(() -> {
      var lineList = new ArrayList<String>();
      try (ByteArrayInputStream in = new ByteArrayInputStream(statusOutput.getBytes())) {
        try (Reader isr = new InputStreamReader(in)) {
          try (BufferedReader br = new BufferedReader(isr)) {
            String line;
            do {
              line = br.readLine();
              if (line == null) {
                break;
              }
              lineList.add(line);
            } while (true);
          }
        }
      }
      return lineList;
    });
  }

  /**
   * @param string
   *   String of content for toLowerCase
   * @return lower case string.
   */
  public static String toLowerCase(String string) {
    return string == null ? null : string.toLowerCase();
  }

  /**
   * Convert a string to snake case (i.e. snake_case)
   *
   * @param string
   *   String to be processed.
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
   * To lower case (or null).
   *
   * @param string
   *   String to be processed.
   * @return upper case string.
   */
  public static String toUpperCase(String string) {
    return string == null ? null : string.toUpperCase();
  }

  /**
   * @param string
   *   String to be processed.
   * @param size
   *   Size.
   * @return truncated string
   */
  public static String truncate(String string, int size) {
    if (string == null) {
      return null;
    }
    return string.substring(0, min(string.length(), size));
  }

}
