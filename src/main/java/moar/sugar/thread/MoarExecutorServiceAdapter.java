package moar.sugar.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * {@link ExecutorService} with {@link MoarAsyncProvider} interface.
 *
 * @author Mark Farnsworth
 */
public class MoarExecutorServiceAdapter
    implements
    MoarAsyncProvider {

  private final ExecutorService executorService;

  /**
   * Create an adapter for the supplied service.
   *
   * @param executorService
   *   The actual executor service.
   */
  public MoarExecutorServiceAdapter(ExecutorService executorService) {
    this.executorService = executorService;
  }

  /**
   * Shutdown.
   */
  @Override
  public void shutdown() {
    executorService.shutdown();
  }

  @Override
  public <T> Future<T> submit(Callable<T> call) {
    return executorService.submit(call);
  }

}