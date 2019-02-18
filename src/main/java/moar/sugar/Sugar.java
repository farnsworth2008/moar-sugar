package moar.sugar;

import static java.lang.Math.random;
import static java.lang.Thread.sleep;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Static Methods that help reduce the code volume associated with common
 * operations.
 *
 * @author Mark Farnsworth
 */
public class Sugar {
  public static RuntimeException asRuntimeException(Throwable e) {
    if (e instanceof RuntimeException) {
      return (RuntimeException) e;
    }
    return new RuntimeException(e);
  }

  public static void closeQuietly(AutoCloseable closeable) {
    swallow(() -> closeable.close());
  }

  /**
   * Return the code location of an callstack offset (i.e. $(1) is the Class and
   * Line of the caller).
   */
  public static String codeLocationAt(int offset) {
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    int pos = -1;
    for (StackTraceElement item : stackTrace) {
      String className = item.getClassName();
      if (!className.startsWith("moar.")) {
        int dotPos = className.lastIndexOf('.');
        if (dotPos > 0) {
          pos++;
          if (pos == offset) {
            int lineNumber = item.getLineNumber();
            return className.substring(dotPos + 1) + ":" + lineNumber;
          }
        }
      }
    }
    return null;
  }

  public static boolean has(Object o) {
    try {
      if (o == null || isEmptyList(o) || isEmptyString(o)) {
        return false;
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @SuppressWarnings("rawtypes")
  public static boolean isEmptyList(Object o) {
    return o instanceof List && ((List) o).isEmpty();
  }

  public static boolean isEmptyString(Object o) {
    return o instanceof String && ((String) o).isEmpty();
  }

  @SafeVarargs
  public static <T> T nonNull(T... args) {
    for (T arg : args) {
      if (arg != null) {
        return arg;
      }
    }
    throw new NullPointerException();
  }

  public static <T> T require(Callable<T> c) {
    try {
      return c.call();
    } catch (Exception e) {
      throw asRuntimeException(e);
    }
  }

  public static void require(CallableVoid r) {
    require(() -> {
      r.call();
      return null;
    });
  }

  public static void require(Object o) {
    if (isEmptyList(o) || isEmptyString(o)) {
      o = null;
    }
    if (o == null) {
      throw new NullPointerException();
    }
  }

  public static void require(String message, boolean test) {
    if (!test) {
      throw new MoarException(message);
    }
  }

  public static <T> T retryable(int tries, Callable<T> call) throws Exception {
    return retryable(tries, 1000, call);
  }

  @SuppressWarnings("null")
  public static <T> T retryable(int triesAllowed, long retryWaitMs, Callable<T> call) throws Exception {
    Exception last = null;
    int tries = 0;
    while (tries++ < triesAllowed) {
      try {
        last = null;
        return call.call();
      } catch (RetryableException e) {
        last = e;
        sleep(retryWaitMs + (long) (random() * retryWaitMs));
      }
    }
    if (last instanceof RetryableException) {
      throw asRuntimeException(last);
    }
    throw last;
  }

  public static void retryable(int tries, long retryWaitMs, Runnable run) {
    require(() -> {
      retryable(tries, retryWaitMs, () -> {
        run.run();
        return null;
      });
    });
  }

  public static void retryable(int tries, Runnable run) {
    retryable(tries, 1000, run);
  }

  public static <T> SafeResult<T> safely(Callable<T> callable) {
    try {
      return new SafeResult<>(callable.call(), null);
    } catch (Throwable e) {
      return new SafeResult<>(null, e);
    }
  }

  public static Throwable safely(CallableVoid run) {
    return safely(() -> {
      run.call();
      return null;
    }).thrown();
  }

  public static String stackTraceAsString(Throwable thrown) {
    return require(() -> {
      try (StringWriter sw = new StringWriter()) {
        try (PrintWriter pw = new PrintWriter(sw)) {
          thrown.printStackTrace(pw);
        }
        return sw.toString();
      }
    });
  }

  public static <T> T swallow(Callable<T> call) {
    SafeResult<T> result = safely(call);
    return result.get();
  }

  public static void swallow(CallableVoid run) {
    safely(run);
  }

  public static Date toUtilDate(int year, int month, int dayOfMonth) {
    return toUtilDate(LocalDate.of(year, month, dayOfMonth));
  }

  public static Date toUtilDate(LocalDate birthDate) {
    return Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }
}
