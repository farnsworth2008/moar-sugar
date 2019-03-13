// @formatter:off
package moar.ansi;

import static java.lang.ThreadLocal.withInitial;
import moar.sugar.PropertyAccessor;

/**
 * @author Mark Farnsworth
 */
@SuppressWarnings("javadoc")
public enum Ansi {

  BLACK("\033[0;30m"),
  RED("\033[0;31m"),
  GREEN("\033[0;32m"),
  YELLOW("\033[0;33m"),
  BLUE("\033[0;34m"),
  PURPLE("\033[0;35m"),
  CYAN("\033[0;36m"),
  WHITE("\033[0;37m"),

  // Bold
  BLACK_BOLD("\033[1;30m"),
  RED_BOLD("\033[1;31m"),
  GREEN_BOLD("\033[1;32m"),
  YELLOW_BOLD("\033[1;33m"),
  BLUE_BOLD("\033[1;34m"),
  PURPLE_BOLD("\033[1;35m"),
  CYAN_BOLD("\033[1;36m"),
  WHITE_BOLD("\033[1;37m"),

  // Underline
  BLACK_UNDERLINED("\033[4;30m"),
  RED_UNDERLINED("\033[4;31m"),
  GREEN_UNDERLINED("\033[4;32m"),
  YELLOW_UNDERLINED("\033[4;33m"),
  BLUE_UNDERLINED("\033[4;34m"),
  PURPLE_UNDERLINED("\033[4;35m"),
  CYAN_UNDERLINED("\033[4;36m"),
  WHITE_UNDERLINED("\033[4;37m"),

  // Background
  BLACK_BACKGROUND("\033[40m"),
  RED_BACKGROUND("\033[41m"),
  GREEN_BACKGROUND("\033[42m"),
  YELLOW_BACKGROUND("\033[43m"),
  BLUE_BACKGROUND("\033[44m"),
  PURPLE_BACKGROUND("\033[45m"),
  CYAN_BACKGROUND("\033[46m"),
  WHITE_BACKGROUND("\033[47m"),

  // High Intensity
  BLACK_BRIGHT("\033[0;90m"),
  RED_BRIGHT("\033[0;91m"),
  GREEN_BRIGHT("\033[0;92m"),
  YELLOW_BRIGHT("\033[0;93m"),
  BLUE_BRIGHT("\033[0;94m"),
  PURPLE_BRIGHT("\033[0;95m"),
  CYAN_BRIGHT("\033[0;96m"),
  WHITE_BRIGHT("\033[0;97m"),

  BLACK_BOLD_BRIGHT("\033[1;90m"),
  RED_BOLD_BRIGHT("\033[1;91m"),
  GREEN_BOLD_BRIGHT("\033[1;92m"),
  YELLOW_BOLD_BRIGHT("\033[1;93m"),
  BLUE_BOLD_BRIGHT("\033[1;94m"),
  PURPLE_BOLD_BRIGHT("\033[1;95m"),
  CYAN_BOLD_BRIGHT("\033[1;96m"),
  WHITE_BOLD_BRIGHT("\033[1;97m"),

  BLACK_BACKGROUND_BRIGHT("\033[0;100m"),
  RED_BACKGROUND_BRIGHT("\033[0;101m"),
  GREEN_BACKGROUND_BRIGHT("\033[0;102m"),
  YELLOW_BACKGROUND_BRIGHT("\033[0;103m"),
  BLUE_BACKGROUND_BRIGHT("\033[0;104m"),
  PURPLE_BACKGROUND_BRIGHT("\033[0;105m"),
  CYAN_BACKGROUND_BRIGHT("\033[0;106m"),
  WHITE_BACKGROUND_BRIGHT("\033[0;107m");

  private static final PropertyAccessor props = new PropertyAccessor("moar.ansi");

  private static ThreadLocal<Boolean> enabled = withInitial(() -> props.getBoolean("enabled", false));

  public static String blue(Object object) {
    return BLUE.apply(object);
  }

  public static String cyanBold(Object object) {
    return CYAN_BOLD.apply(object);
  }

  public static boolean enabled() {
    return enabled.get();
  }

  public static Boolean enabled(Boolean newValue) {
    try {
      return enabled.get();
    } finally {
      if(newValue != null) {
        enabled.set(newValue);
      }
    }
  }

  public static String green(Object object) {
    return GREEN.apply(object);
  }

  public static String greenBold(Object object) {
    return GREEN_BOLD.apply(object);
  }

  public static String purple(Object object) {
    return PURPLE.apply(object);
  }

  public static String purpleBold(Object object) {
    return PURPLE_BOLD.apply(object);
  }

  public static String red(Object object) {
    return RED.apply(object);
  }

  public static Object redBold(String object) {
    return RED_BOLD.apply(object);
  }

  public static String white(Object object) {
    return WHITE.apply(object);
  }

  public static String yellow(Object object) {
    return YELLOW.apply(object);
  }

  private final String RESET = "\033[0m";

  private final String code;

  Ansi(String code) {
    this.code = code;
  }

  public String apply(Object object) {
    return enabled.get() ? code + object + RESET : object.toString();
  }
}
