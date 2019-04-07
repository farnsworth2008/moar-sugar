package moar.sugar.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

final class MoarDirectAsyncProvider
    implements
    MoarAsyncProvider {
  private static ListeningExecutorService directExecutorService = MoreExecutors.newDirectExecutorService();
  @Override
  public void close() throws Exception {
  }
  @Override
  public void shutdown() {
  }
  @Override
  public <T> Future<T> submit(Callable<T> c) {
    return directExecutorService.submit(c);
  }
}