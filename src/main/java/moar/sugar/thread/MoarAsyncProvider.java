package moar.sugar.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Interface to define an Async provider for MoarThreadSugar.
 *
 * @author Mark Farnsworth
 */
public interface MoarAsyncProvider {
  /**
   * Shutdown the provider
   */
  void shutdown();

  /**
   * Contract for submitting an async request.
   *
   * @param call
   * @return A future
   */
  <T> Future<T> submit(Callable<T> call);
}