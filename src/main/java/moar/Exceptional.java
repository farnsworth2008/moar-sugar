package moar;
import static java.lang.Math.random;
import static moar.JsonUtil.debug;
import static moar.JsonUtil.info;
import static moar.JsonUtil.trace;
import static moar.JsonUtil.warn;
import static org.slf4j.LoggerFactory.getLogger;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
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

  public static RuntimeException asRuntimeException(final Throwable e) {
    if (e instanceof RuntimeException) {
      return (RuntimeException) e;
    }
    return new RuntimeException(e);
  }

  public static void closeQuietly(final AutoCloseable closeable) {
    if (closeable != null) {
      swallow(() -> closeable.close());
    }
  }

  public static final <T> T expect(final Callable<T> c) {
    try {
      return c.call();
    } catch (final Exception e) {
      warn(LOG, e.getMessage(), e);
      return null;
    }
  }

  public static final void expect(final Exceptionable r) {
    expect(() -> {
      r.run();
      return null;
    });
  }

  public static final boolean expect(final Object o) {
    try {
      require(o);
      return true;
    } catch (final Throwable e) {
      warn(LOG, "expect", $(1));
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

  public static boolean isThrowable(final Object o) {
    return o instanceof Throwable;
  }

  /**
   * Resolve a number of args and return the first nonNull result
   */
  public static Object nonNull(final Object... args) {
    for (final Object arg : args) {
      if (arg != null) {
        final Object value = require(() -> resolve(arg));
        if (value != null) {
          return value;
        }
      }
    }
    throw new NullPointerException();
  }

  public static <T> T nonNullOr(final Object arg, final Callable<T> c) {
    return (T) nonNull(arg, c);
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

  public static final <T> T require(final Callable<T> c) {
    try {
      return c.call();
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

  public static final void require(final Exceptionable r) {
    require(() -> {
      r.run();
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
      throw new StringMessageException(message);
    }
  }

  /**
   * Evaluate an object that may be a callable or future.
   * <p>
   * If the object is a callable make the call and return the result otherwise return the value.
   * <p>
   * Evaluation will recurse until a value (or null) that is not a callable or a future is found.
   *
   * @param valueOrCallable
   * @return
   * @throws Exception
   */
  @SuppressWarnings("rawtypes")
  public static Object resolve(Object valueOrCallable) throws Exception {
    if (valueOrCallable instanceof Callable) {
      valueOrCallable = resolve(((Callable) valueOrCallable).call());
    }
    if (valueOrCallable instanceof Future) {
      valueOrCallable = resolve(((Future) valueOrCallable).get());
    }
    return valueOrCallable;
  }

  public static <T> T retryable(final int triesAllowed, final long retryWaitMs, final Callable<T> call)
      throws Exception {
    Exception last = null;
    int tries = 0;
    while (tries++ < triesAllowed) {
      try {
        last = null;
        return call.call();
      } catch (final RetryableException e) {
        last = e;
        Thread.sleep(retryWaitMs + (long) (random() * retryWaitMs));
        debug(LOG, "retryable", tries, e.getMessage());
      } finally {
        if (last == null && tries > 1) {
          info(LOG, "retryable-success-with-tries", tries);
        }
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

  public static <T> Two<T, Throwable> safely(final Callable<T> callable) {
    try {
      return new Two<>(callable.call(), null);
    } catch (final Throwable e) {
      return new Two<>(null, e);
    }
  }

  public static Throwable safely(final Exceptionable run) {
    return safely(() -> {
      run.run();
      return null;
    }).getTwo();
  }

  public static <T> T swallow(final Callable<T> c) {
    try {
      return c.call();
    } catch (final Throwable e) {
      // Yum, that was tasty
      return null;
    }
  }

  public static void swallow(final Exceptionable r) {
    swallow(() -> {
      r.run();
      return null;
    });
  }

  public static void warnFor(final Logger log, final Throwable exception) {
    if (exception instanceof FutureListException) {
      final FutureListException futureListException = (FutureListException) exception;
      for (final Two<Object, Exception> result : futureListException.getResults()) {
        if (result.getTwo() != null) {
          warnFor(log, result.getTwo());
        }
      }
    } else {
      warn(log, "from async future", exception);
    }
  }
}
