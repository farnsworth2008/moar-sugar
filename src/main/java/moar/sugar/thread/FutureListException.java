package moar.sugar.thread;

import java.util.List;
import moar.sugar.MoarLogger;
import moar.sugar.SafeResult;

/**
 * Exception to signal an issue that occurred when running async code.
 * 
 * @author Mark Farnsworth
 */
public class FutureListException
    extends
    RuntimeException {

  private static void doWarnFor(MoarLogger log, Throwable exception) {
    if (exception instanceof FutureListException) {
      FutureListException futureListException = (FutureListException) exception;
      for (SafeResult<Object> result : futureListException.getResults()) {
        if (result.thrown() != null) {
          doWarnFor(log, result.thrown());
        }
      }
    } else {
      log.debug("doh!", exception);
    }
  }

  private final List<SafeResult<Object>> results;

  FutureListException(List<SafeResult<Object>> results) {
    this.results = results;
  }

  /**
   * @return List of results.
   */
  public List<SafeResult<Object>> getResults() {
    return results;
  }

  /**
   * Write to logger using warn level.
   * 
   * @param log
   *   logger to write content to.
   */
  public void warn(MoarLogger log) {
    doWarnFor(log, this);
  }

}
