package moar.sugar;

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

  private static Object[] unpack(final Object[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof String) {
        try {
          args[i] = moarJson.getJsonParser().parse((String) args[i]);
        } catch (final RuntimeException e) {
          // swallow
        }
      }
    }
    return args;
  }

  final Logger log;

  public MoarLogger(Class<?> clz) {
    log = Logger.getLogger(clz.getName());
  }

  public void debug(Object... args) {
    log(Level.FINE, args);
  }

  public void error(Object... args) {
    log(Level.SEVERE, args);
  }

  public void info(Object... args) {
    log(Level.INFO, args);
  }

  public void log(Level level, Object... args) {
    final Object lastArg = args[args.length - 1];
    if (lastArg instanceof Throwable) {
      args = Arrays.copyOf(args, args.length - 1);
      log.log(level, moarJson.toJsonSafely(codeLocationAt(1), unpack(args)),
          (Throwable) lastArg);
    } else {
      args = Arrays.copyOf(args, args.length - 1);
      log.log(level, moarJson.toJsonSafely(codeLocationAt(1), unpack(args)));
    }
  }

  public void trace(Object... args) {
    log(Level.FINEST, args);
  }

  public void warn(Object... args) {
    log(Level.WARNING, args);
  }

}
