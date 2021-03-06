package moar.sugar;

import static java.lang.Math.min;
import static java.net.URLEncoder.encode;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.UTF_8;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.swallow;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import com.google.common.base.CharMatcher;

/**
 * Yet another String Utility
 *
 * @author Mark Farnsworth
 */
public class MoarStringUtil {

  public static void appendLinesToFile(File file, String... lines) {
    require(() -> {
      try (Writer fw = new FileWriter(file, true)) {
        try (PrintWriter pw = new PrintWriter(fw)) {
          for (String line : lines) {
            pw.println(line);
          }
        }
      }
    });
  }

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

  public static String lastWord(String s) {
    String[] split = s.split("\\s");
    String part = split[split.length - 1];
    return part.trim();
  }

  public static String leftTruncate(String text, int maxLen, String truncatedSuffix) {
    if (text.length() > maxLen) {
      int trimedSize = maxLen - truncatedSuffix.length();
      text = truncatedSuffix + text.substring(text.length() - trimedSize - 1);
    }
    return text;
  }

  public static String middleTruncate(String text, int len1, int maxLen, String truncatedSuffix) {
    int len = text.length();
    if (len <= maxLen) {
      return text;
    }
    int len2 = maxLen - len1;
    String text1 = truncate(text, len1);
    String text2 = len <= len1 ? "" : leftTruncate(text, len2, truncatedSuffix);
    return text1 + text2;
  }

  public static String readStringFromFile(File file) {
    return swallow(() -> {
      try (InputStream is = new FileInputStream(file)) {
        return streamToString(is);
      }
    });
  }

  /**
   * Simple way to read a string from a resource.
   *
   * @param clz
   *   Class for accessing the resource system.
   * @param name
   *   Name of stream in resource system.
   * @return String result
   */
  public static String readStringFromResource(Class<?> clz, String name) {
    return require(() -> {
      try (InputStream in = clz.getResourceAsStream(name)) {
        return IOUtils.toString(in, defaultCharset());
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
    return swallow(() -> IOUtils.toString(is, defaultCharset()));
  }

  /**
   * Get a string representation for a throwable with message and stack trace.
   *
   * @param thrown
   * @return string of stack trace
   */
  public static String throwableToString(Throwable thrown) {
    return require(() -> {
      try (StringWriter sw = new StringWriter()) {
        sw.append(thrown.getMessage() + "\n");
        try (PrintWriter pw = new PrintWriter(sw)) {
          thrown.printStackTrace(pw);
        }
        return sw.toString();
      }
    });
  }

  public static List<String> toLineList(String statusOutput) {
    return swallow(() -> {
      List<String> lineList = new ArrayList<>();
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

  public static String truncate(String text, int maxLen, String truncatedSuffix) {
    if (text.length() > maxLen) {
      int trimedSize = min(maxLen - truncatedSuffix.length(), text.length());
      text = text.substring(0, trimedSize) + truncatedSuffix;
    }
    return text;
  }

  public static String urlEncode(String postalAddress) {
    return require(() -> encode(postalAddress, UTF_8.toString()));
  }

  public static void writeStringToFile(File file, String string) {
    require(() -> {
      try (FileOutputStream out = new FileOutputStream(file)) {
        out.write(string.getBytes());
      }
    });
  }

}
