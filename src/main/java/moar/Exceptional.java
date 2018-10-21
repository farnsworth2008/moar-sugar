package moar;
import static java.lang.Math.random;
import static moar.JsonUtil.debug;
import static moar.JsonUtil.trace;
import static org.slf4j.LoggerFactory.getLogger;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;

public class Exceptional {
  private static final Logger LOG = getLogger(Exceptional.class);

  /**
   * Return the code location of an callstack offset (i.e. $(1) is the Class and Line of the caller).
   */
  public static final String $(final int offset) {
    final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
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

  public static RuntimeException asRuntimeException(final Exception e) {
    if (e instanceof RuntimeException) {
      return (RuntimeException) e;
    }
    return new RuntimeException(e);
  }

  public static final void expect(final Exceptionable r) {
    try {
      r.run();
    } catch (final Exception e) {
      debug(LOG, e.getMessage(), e);
    }
  }

  public static final <T> T expect(final ExceptionableCall c) {
    try {
      return (T) c.call();
    } catch (final Exception e) {
      debug(LOG, e.getMessage(), e);
      return null;
    }
  }

  public static final boolean expect(final Object o) {
    try {
      require(o);
      return true;
    } catch (final Exception e) {
      debug(LOG, "expect", $(1));
      return false;
    }
  }

  public static boolean has(final Object o) {
    try {
      if (o == null || isEmptyList(o) || isEmptyString(o)) {
        return false;
      }
      return true;
    } catch (final Exception e) {
      trace(LOG, "has", $(1));
      return false;
    }
  }

  @SuppressWarnings("rawtypes")
  private static boolean isEmptyList(final Object o) {
    return o instanceof List && ((List) o).isEmpty();
  }

  private static boolean isEmptyString(final Object o) {
    return o instanceof String && ((String) o).isEmpty();
  }

  public static <T> T nullOr(final Object test, final Callable<T> callable) {
    return test == null ? null : require(() -> callable.call());
  }

  public static <T> T quietly(final Callable<T> call) {
    try {
      return call.call();
    } catch (final Exception e) {
      debug(LOG, e.getClass().getSimpleName(), e.getMessage());
      return null;
    }
  }

  public static void quietly(final Exceptionable r) {
    quietly(() -> {
      r.run();
      return null;
    });
  }

  public static final void require(final Exceptionable r) {
    require(() -> {
      r.run();
      return null;
    });
  }

  public static final <T> T require(final ExceptionableCall c) {
    try {
      return (T) c.call();
    } catch (final FutureListException e) {
      int i = 0;
      for (final Two<Object, Exception> result : e.getResults()) {
        debug(LOG, "FutureListException #" + i++, result.getOne(), result.getTwo());
      }
      debug(LOG, "require", e);
      throw e;
    } catch (final Exception e) {
      throw asRuntimeException(e);
    }
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
      throw new StringMessageException(message);
    }
  }

  public static <T> T retryable(int tries, final long retryWaitMs, final Callable<T> call) throws Exception {
    Exception last = null;
    while (tries-- > 0) {
      try {
        return call.call();
      } catch (final NonRetryableException e) {
        throw e;
      } catch (final Exception e) {
        last = e;
        Thread.sleep(retryWaitMs + (long) (random() * retryWaitMs));
        debug(LOG, "retryable", tries, e.getMessage());
      }
    }
    throw last;
  }

  public static void retryable(final int tries, final long retryWaitMs, final Runnable r) {
    require(() -> {
      retryable(tries, retryWaitMs, () -> {
        r.run();
        return null;
      });
    });
  }

  public static <T> Two<T, Exception> safely(final ExceptionableCall callable) {
    try {
      return new Two<>((T) callable.call(), null);
    } catch (final Exception e) {
      return new Two<>(null, e);
    }
  }

  public static void swallow(final Exceptionable r) {
    swallow(() -> {
      r.run();
      return null;
    });
  }

  public static <T> T swallow(final ExceptionableCall c) {
    try {
      return (T) c.call();
    } catch (final Exception e) {
      // Yum, that was tasty
      return null;
    }
  }

}
