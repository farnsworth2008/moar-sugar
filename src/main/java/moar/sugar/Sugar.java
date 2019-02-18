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
  public static RuntimeException asRuntimeException(final Throwable e) {
    if (e instanceof RuntimeException) {
      return (RuntimeException) e;
    }
    return new RuntimeException(e);
  }

  public static void closeQuietly(final AutoCloseable closeable) {
    swallow(() -> closeable.close());
  }

  /**
   * Return the code location of an callstack offset (i.e. $(1) is the Class and
   * Line of the caller).
   */
  public static final String codeLocationAt(final int offset) {
    final StackTraceElement[] stackTrace
        = Thread.currentThread().getStackTrace();
    int pos = -1;
    for (final StackTraceElement item : stackTrace) {
      final String className = item.getClassName();
      if (!className.startsWith("moar.")) {
        final int dotPos = className.lastIndexOf('.');
        if (dotPos > 0) {
          pos++;
          if (pos == offset) {
            final int lineNumber = item.getLineNumber();
            return className.substring(dotPos + 1) + ":" + lineNumber;
          }
        }
      }
    }
    return null;
  }

  public static boolean has(final Object o) {
    try {
      if (o == null || isEmptyList(o) || isEmptyString(o)) {
        return false;
      }
      return true;
    } catch (final Exception e) {
      return false;
    }
  }

  @SuppressWarnings("rawtypes")
  public static boolean isEmptyList(final Object o) {
    return o instanceof List && ((List) o).isEmpty();
  }

  public static boolean isEmptyString(final Object o) {
    return o instanceof String && ((String) o).isEmpty();
  }

  @SafeVarargs
  public static <T> T nonNull(final T... args) {
    for (final T arg : args) {
      if (arg != null) {
        return arg;
      }
    }
    throw new NullPointerException();
  }

  public static final <T> T require(final Callable<T> c) {
    try {
      return c.call();
    } catch (final Exception e) {
      throw asRuntimeException(e);
    }
  }

  public static final void require(final CallableVoid r) {
    require(() -> {
      r.call();
      return null;
    });
  }

  public static final void require(Object o) {
    if (isEmptyList(o) || isEmptyString(o)) {
      o = null;
    }
    if (o == null) {
      throw new NullPointerException();
    }
  }

  public static final void require(final String message, final boolean test) {
    if (!test) {
      throw new MoarException(message);
    }
  }

  public static <T> T retryable(final int tries, final Callable<T> call)
      throws Exception {
    return retryable(tries, 1000, call);
  }

  @SuppressWarnings("null")
  public static <T> T retryable(final int triesAllowed, final long retryWaitMs,
      final Callable<T> call) throws Exception {
    Exception last = null;
    int tries = 0;
    while (tries++ < triesAllowed) {
      try {
        last = null;
        return call.call();
      } catch (final RetryableException e) {
        last = e;
        sleep(retryWaitMs + (long) (random() * retryWaitMs));
      }
    }
    if (last instanceof RetryableException) {
      throw asRuntimeException(last);
    }
    throw last;
  }

  public static void retryable(final int tries, final long retryWaitMs,
      final Runnable run) {
    require(() -> {
      retryable(tries, retryWaitMs, () -> {
        run.run();
        return null;
      });
    });
  }

  public static void retryable(final int tries, final Runnable run) {
    retryable(tries, 1000, run);
  }

  public static <T> SafeResult<T> safely(final Callable<T> callable) {
    try {
      return new SafeResult<>(callable.call(), null);
    } catch (final Throwable e) {
      return new SafeResult<>(null, e);
    }
  }

  public static Throwable safely(final CallableVoid run) {
    return safely(() -> {
      run.call();
      return null;
    }).thrown();
  }

  public static String stackTraceAsString(final Throwable thrown) {
    return require(() -> {
      try (StringWriter sw = new StringWriter()) {
        try (PrintWriter pw = new PrintWriter(sw)) {
          thrown.printStackTrace(pw);
        }
        return sw.toString();
      }
    });
  }

  public static <T> T swallow(final Callable<T> call) {
    SafeResult<T> result = safely(call);
    return result.get();
  }

  public static void swallow(final CallableVoid run) {
    safely(run);
  }

  public static Date toUtilDate(int year, int month, int dayOfMonth) {
    return toUtilDate(LocalDate.of(year, month, dayOfMonth));
  }

  public static Date toUtilDate(LocalDate birthDate) {
    return Date
        .from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }
}
