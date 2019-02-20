package moar.sugar;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static moar.sugar.Sugar.codeLocationAt;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Yet another java logger.
 * <p>
 * This logger is populated with methods that work on var args to log using JSON
 * representations.
 *
 * @author Mark Farnsworth
 */
public class MoarLogger {
  private static MoarJson moarJson = MoarJson.getMoarJson();

  private static Object[] unpack(Object[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof String) {
        try {
          args[i] = moarJson.getJsonParser().parse((String) args[i]);
        } catch (RuntimeException e) {
          // swallow
        }
      }
    }
    return args;
  }

  Logger log;

  /**
   * Create logger for class.
   *
   * @param clz
   *   Class used to name the logger.
   */
  public MoarLogger(Class<?> clz) {
    log = Logger.getLogger(clz.getName());
  }

  /**
   * Log a message using {@link Level#FINE}
   *
   * @param args
   *   Message
   */
  public void debug(Object... args) {
    log(FINE, args);
  }

  /**
   * Log a message using {@link Level#SEVERE}
   *
   * @param args
   *   Message
   */
  public void error(Object... args) {
    log(SEVERE, args);
  }

  /**
   * Log a message using {@link Level#INFO}
   *
   * @param args
   *   Message
   */
  public void info(Object... args) {
    log(INFO, args);
  }

  void log(Level level, Object... args) {
    Object lastArg = args[args.length - 1];
    if (lastArg instanceof Throwable) {
      args = Arrays.copyOf(args, args.length - 1);
      log.log(level, moarJson.toJsonSafely(codeLocationAt(1), unpack(args)), (Throwable) lastArg);
    } else {
      args = Arrays.copyOf(args, args.length - 1);
      log.log(level, moarJson.toJsonSafely(codeLocationAt(1), unpack(args)));
    }
  }

  /**
   * Log a message using {@link Level#FINEST}
   *
   * @param args
   *   Message
   */
  public void trace(Object... args) {
    log(FINEST, args);
  }

  /**
   * Log a message using {@link Level#WARNING}
   *
   * @param args
   *   Message
   */
  public void warn(Object... args) {
    log(WARNING, args);
  }

}
