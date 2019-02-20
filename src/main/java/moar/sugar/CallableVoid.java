package moar.sugar;

import java.util.concurrent.Callable;

/**
 * A task that does not return a result and may throw an exception.
 * <p>
 * Java standard library includes {@link Callable} and {@link Runnable} as
 * interfaces for generic operations that can be used for lambda's. The standard
 * library includes {@link Exception} as part of {@link Callable#call} but not
 * on {@link Runnable#run}. This makes it difficult to use common patterns for
 * lambda that return results and ones that do not.
 * <p>
 * This task provides a call method that returns void and throws
 * {@link Exception}. It allows lamda's that no not return values to follow the
 * same pattern as ones that use {@link Callable}.
 *
 * @author Mark Farnsworth
 */
public interface CallableVoid {
  /**
   * Executes a call, or throws an exception if unable to do so.
   *
   * @throws Exception
   *   if unable to compute a result
   */
  void call() throws Exception;
}
