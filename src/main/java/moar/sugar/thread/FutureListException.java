package moar.sugar.thread;

import java.util.List;
import moar.sugar.MoarLogger;
import moar.sugar.SafeResult;

/**
 * @author Mark Farnsworth
 */
public class FutureListException
    extends
    RuntimeException {

  private static void doWarnFor(final MoarLogger log,
      final Throwable exception) {
    if (exception instanceof FutureListException) {
      final FutureListException futureListException
          = (FutureListException) exception;
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

  public FutureListException(final List<SafeResult<Object>> results) {
    this.results = results;
  }

  public List<SafeResult<Object>> getResults() {
    return results;
  }

  public void warn(final MoarLogger log) {
    doWarnFor(log, this);
  }

}
