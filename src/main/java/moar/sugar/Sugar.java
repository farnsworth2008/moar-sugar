package moar.sugar;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Math.random;
import static java.lang.System.setErr;
import static java.lang.System.setOut;
import static java.lang.Thread.sleep;
import static org.apache.commons.io.IOUtils.copy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
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

  public static final Float MIN_FLOAT = Float.MIN_VALUE;
  public static final Float MAX_FLOAT = Float.MAX_VALUE;

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

  public static ByteArrayOutputStream bos() {
    return new ByteArrayOutputStream();
  }

  /**
   * Close with swallowing.
   *
   * @param closeable
   *   Something that will be closed.
   */
  public static void closeQuietly(AutoCloseable closeable) {
    swallow(() -> closeable.close());
  }

  /**
   * Return the code location of an callstack offset (i.e. $(1) is the Class and
   * Line of the caller).
   *
   * @param offset
   *   The offset in terms of stack depth.
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

  public static boolean deleteDir(File dir) {
    if (!dir.exists()) {
      return true;
    }
    File[] files = dir.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.isDirectory()) {
          deleteDir(f);
        } else {
          f.delete();
        }
      }
    }
    return dir.delete();
  }

  private static ExecuteResult doExec(String command, String input, File dir) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder();
    builder.directory(dir);
    builder.command("bash", "-c", command);
    Process process = builder.start();
    try (OutputStream os = process.getOutputStream()) {
      os.write(input.getBytes());
    }
    String response;
    int exitCode;
    try (InputStreamReader reader = new InputStreamReader(process.getInputStream())) {
      try (StringWriter writer = new StringWriter()) {
        copy(reader, writer);
        exitCode = process.waitFor();
        response = writer.toString();
      }
    }
    return new ExecuteResult(exitCode, response);
  }

  public static ExecuteResult exec(String command, File dir) {
    return exec(command, "", dir);
  }

  public static ExecuteResult exec(String command, String input) {
    return exec(command, input, null);
  }

  public static ExecuteResult exec(String command, String input, File dir) {
    return require(() -> doExec(command, input, dir));
  }

  /**
   * Truthy test
   *
   * @param object
   *   A reference that may be null, empty, false or actually something.
   * @return True for a value that is truthy
   */
  public static Boolean has(Object object) {
    return nonNull(safely(() -> {
      if (null == object || FALSE == object || isEmptyList(object) || isEmptyString(object)) {
        return FALSE;
      }
      return TRUE;
    }).get(), FALSE);
  }

  /**
   * True if the object is an empty list.
   *
   * @param object
   *   An object that might be an empty list.
   * @return true if the object is an empty list
   */
  @SuppressWarnings("rawtypes")
  public static Boolean isEmptyList(Object object) {
    return object instanceof List && ((List) object).isEmpty();
  }

  /**
   * @param object
   *   An object that might be an empty string.
   * @return true if object is an empty string
   */
  public static Boolean isEmptyString(Object object) {
    return object instanceof String && ((String) object).isEmpty();
  }

  /**
   * Get the first non null argument.
   *
   * @param args
   *   Arguments
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
   * Require a test to pass.
   *
   * @param test
   *   test that must be true.
   */
  public static void require(Boolean test) {
    if (!has(test)) {
      throw new MoarException("Required test failed");
    }
  }

  /**
   * Require a call to succeed.
   *
   * @param call
   *   Call to make.
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
   * @param calls
   *   Calls to make.
   * @throws RuntimeException
   *   if call fails.
   */
  public static void require(CallableVoid... calls) {
    for (CallableVoid call : calls) {
      require(() -> {
        call.call();
        return null;
      });
    }
  }

  /**
   * Retry a call if needed.
   *
   * @param tries
   *   number of tries
   * @param call
   *   Call to run
   * @return result
   * @throws Exception
   *   Exception thrown
   */
  public static <T> RetryResult<T> retry(int tries, Callable<T> call) throws Exception {
    return retry(tries, 1000, call);
  }

  /**
   * Retry a call if needed.
   *
   * @param tries
   * @param call
   * @throws Exception
   */
  public static int retry(int tries, CallableVoid call) {
    return retry(tries, 1000, call);
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
  public static <T> RetryResult<T> retry(int triesAllowed, long retryWaitMs, Callable<T> call) {
    return require(() -> {
      Exception last = null;
      int tries = 0;
      while (tries++ < triesAllowed) {
        try {
          last = null;
          return new RetryResult<>(tries - 1, call.call());
        } catch (RetryableException e) {
          last = e;
          sleep(retryWaitMs + (long) (random() * retryWaitMs));
        }
      }
      throw last;
    });
  }

  /**
   * Retry a call if needed.
   *
   * @param tries
   * @param retryWaitMs
   * @param call
   * @throws Exception
   */
  public static int retry(int tries, long retryWaitMs, CallableVoid call) {
    return retry(tries, retryWaitMs, () -> {
      call.call();
      return null;
    }).getRetryCount();
  }

  /**
   * Make any exception thrown from the call retryable.
   *
   * @param call
   *   Call to make.
   * @return result
   * @throws RetryableException
   *   Exception from the call as a {@link RetryableException}.
   */
  public static <T> T retryable(Callable<T> call) throws RetryableException {
    try {
      return call.call();
    } catch (RetryableException e) {
      throw e;
    } catch (Exception e) {
      throw new RetryableException(e);
    }
  }

  /**
   * Make any exception thrown from the call retryable.
   *
   * @param call
   *   Call to make.
   * @throws RetryableException
   *   Exception from the call as a {@link RetryableException}.
   */
  public static void retryable(CallableVoid call) throws RetryableException {
    retryable(() -> {
      call.call();
      return null;
    });
  }

  /**
   * Make any exception thrown from the call retryable and retry as needed.
   *
   * @param call
   *   Call to make.
   * @throws RetryableException
   *   Exception from the call as a {@link RetryableException}.
   */
  public static int retryable(int tries, CallableVoid call) throws RetryableException {
    return retry(tries, () -> retryable(call));
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
  public static SafeResult<Void> safely(CallableVoid call) {
    return safely(() -> {
      call.call();
      return null;
    });
  }

  /**
   * Make a call while suppressing System.out and System.err.
   *
   * @param call
   *   Call to make while output is suppressed.
   * @return Result
   * @throws Exception
   * @throws IOException
   */
  public static <T> SilentResult<T> silently(Callable<T> call) {
    System.err.flush();
    PrintStream priorErr = System.err;
    return require(() -> {
      try (ByteArrayOutputStream bosErr = bos()) {
        setErr(new PrintStream(bosErr));
        return withRedirectedErr(call, bosErr);
      } finally {
        setErr(priorErr);
      }
    });
  }

  /**
   * Make a call while suppressing System.out and System.err.
   *
   * @param call
   *   Call to make while output is suppressed.
   * @return Result
   * @throws Exception
   * @throws IOException
   */
  public static void silently(CallableVoid call) throws Exception, IOException {
    silently(() -> {
      call.call();
      return null;
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

  public static Float valueOfFloat(String string) {
    return Float.valueOf(string);
  }

  private static <T> SilentResult<T> withRedirectedErr(Callable<T> call, ByteArrayOutputStream bosErr)
      throws Exception, IOException {
    System.out.flush();
    PrintStream prior = System.out;
    try (ByteArrayOutputStream bos = bos()) {
      setOut(new PrintStream(bos));
      T callResult = call.call();
      return new SilentResult<>(callResult, bos.toByteArray(), bosErr.toByteArray());
    } finally {
      setOut(prior);
    }
  }

}
