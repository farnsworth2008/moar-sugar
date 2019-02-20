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

  /**
   * Get a RuntimeException from an Exception.
   *
   * @param e
   *   an exception that *might* be a runtime exception.
   * @return A RuntimeException
   */
  public static RuntimeException asRuntimeException(Throwable e) {
    if (e instanceof RuntimeException) {
      return (RuntimeException) e;
    }
    return new RuntimeException(e);
  }

  /**
   * Close with swallowing.
   *
   * @param closeable
   */
  public static void closeQuietly(AutoCloseable closeable) {
    swallow(() -> closeable.close());
  }

  /**
   * Return the code location of an callstack offset (i.e. $(1) is the Class and
   * Line of the caller).
   *
   * @param offset
   * @return code location as class name with line number.
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

  /**
   * Truthy test
   *
   * @param object
   * @return True for a value that is truthy
   */
  public static boolean has(Object object) {
    try {
      if (object == null || isEmptyList(object) || isEmptyString(object)) {
        return false;
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * @param object
   * @return true if the object is an empty list
   */
  @SuppressWarnings("rawtypes")
  public static boolean isEmptyList(Object object) {
    return object instanceof List && ((List) object).isEmpty();
  }

  /**
   * @param object
   * @return true if object is an empty string
   */
  public static boolean isEmptyString(Object object) {
    return object instanceof String && ((String) object).isEmpty();
  }

  /**
   * @param args
   * @return first non null argument
   */
  @SafeVarargs
  public static <T> T nonNull(T... args) {
    for (T arg : args) {
      if (arg != null) {
        return arg;
      }
    }
    throw new NullPointerException();
  }

  /**
   * Require a call to succeed.
   *
   * @param call
   * @return result
   * @throws RuntimeException
   *   if call fails.
   */
  public static <T> T require(Callable<T> call) {
    try {
      return call.call();
    } catch (Exception e) {
      throw asRuntimeException(e);
    }
  }

  /**
   * Require a call to succeed.
   *
   * @param call
   * @throws RuntimeException
   *   if call fails.
   */
  public static void require(CallableVoid call) {
    require(() -> {
      call.call();
      return null;
    });
  }

  /**
   * Require a test to pass.
   *
   * @param message
   * @param test
   */
  public static void require(String message, boolean test) {
    if (!test) {
      throw new MoarException(message);
    }
  }

  /**
   * Require an object to exist
   *
   * @param object
   * @return object
   */
  public static <T> T require(T object) {
    if (isEmptyList(object) || isEmptyString(object)) {
      object = null;
    }
    if (object == null) {
      throw new NullPointerException();
    }
    return object;
  }

  /**
   * Retry a call if needed.
   *
   * @param tries
   *   number of tries
   * @param call
   * @return result
   * @throws Exception
   */
  public static <T> T retryable(int tries, Callable<T> call) throws Exception {
    return retryable(tries, 1000, call);
  }

  /**
   * Retry a call if needed.
   *
   * @param tries
   * @param call
   */
  public static void retryable(int tries, CallableVoid call) {
    retryable(tries, 1000, call);
  }

  /**
   * Retry a call if needed.
   *
   * @param triesAllowed
   * @param retryWaitMs
   * @param call
   * @return result
   * @throws Exception
   */
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

  /**
   * Retry a call if needed.
   *
   * @param tries
   * @param retryWaitMs
   * @param call
   */
  public static void retryable(int tries, long retryWaitMs, CallableVoid call) {
    require(() -> {
      retryable(tries, retryWaitMs, () -> {
        call.call();
        return null;
      });
    });
  }

  /**
   * Execute a call without throwing an exception.
   *
   * @param call
   * @return safe result
   */
  public static <T> SafeResult<T> safely(Callable<T> call) {
    try {
      return new SafeResult<>(call.call(), null);
    } catch (Throwable e) {
      return new SafeResult<>(null, e);
    }
  }

  /**
   * Execute a call without throwing an exception.
   *
   * @param call
   * @return safe result
   */
  public static Throwable safely(CallableVoid call) {
    return safely(() -> {
      call.call();
      return null;
    }).thrown();
  }

  /**
   * Get a string representation for a stack trace.
   *
   * @param thrown
   * @return string of stack trace
   */
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

  /**
   * Execute a call return null on exceptions.
   *
   * @param call
   * @return result or null
   */
  public static <T> T swallow(Callable<T> call) {
    SafeResult<T> result = safely(call);
    return result.get();
  }

  /**
   * Execute a call, do not throw exceptions.
   *
   * @param call
   */
  public static void swallow(CallableVoid call) {
    safely(call);
  }

  /**
   * Create a date using three ints.
   *
   * @param year
   * @param month
   * @param dayOfMonth
   * @return date
   */
  public static Date toUtilDate(int year, int month, int dayOfMonth) {
    return toUtilDate(LocalDate.of(year, month, dayOfMonth));
  }

  /**
   * Create a date from a local date without a ton of cruf.
   *
   * @param localDate
   * @return date
   */
  public static Date toUtilDate(LocalDate localDate) {
    return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }
}
