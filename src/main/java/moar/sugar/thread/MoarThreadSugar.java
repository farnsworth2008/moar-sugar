package moar.sugar.thread;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static moar.sugar.Sugar.codeLocationAt;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.safely;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import moar.sugar.CallableVoid;
import moar.sugar.MoarJson;
import moar.sugar.MoarLogger;
import moar.sugar.PropertyAccessor;
import moar.sugar.SafeResult;

/**
 * Thread Management In software, time is money!
 * <p>
 * The unit of cost is a millisecond. Methods here help track cost with the
 * ability to roll up activities that involve multiple threads.
 */
public class MoarThreadSugar {
  private static MoarLogger LOG = new MoarLogger(MoarThreadSugar.class);
  private static PropertyAccessor prop = new PropertyAccessor(MoarThreadSugar.class.getName());
  private static boolean asyncEnabled = prop.getBoolean("async", true);
  private static long TRACE_COST_LIMIT = prop.getLong("traceCostLimit", 10 * 1000L);
  private static ThreadLocal<MoarThreadActivity> threadActivity = new ThreadLocal<>();
  private static ThreadLocal<Boolean> threadIsAsync = new ThreadLocal<>();
  private static boolean trackCosts = prop.getBoolean("trackCosts", true);
  private static boolean trackDetailCosts = prop.getBoolean("trackDetailCosts", true);
  private static MoarAsyncProvider directAsyncProvider = new MoarDirectAsyncProvider();
  private static MoarJson moarJson = MoarJson.getMoarJson();

  /**
   * @return Vector of futures.
   */
  public static Vector<Future<Object>> $() {
    return $(Object.class);
  }

  /**
   * Execute calls on the current thread with tracking.
   *
   * @param calls
   *   Calls to execute
   * @return The result of the last call.
   * @throws Exception
   *   Exception thrown in call
   */
  @SafeVarargs
  public static <T> T $(Callable<T>... calls) throws Exception {
    T last = null;
    for (Callable<T> call : calls) {
      last = $(codeLocationAt(1), call);
    }
    return last;
  }

  /**
   * Call with tracking.
   *
   * @param calls
   *   Calls to execute
   */
  public static void $(CallableVoid... calls) {
    for (CallableVoid call : calls) {
      $(codeLocationAt(1), call);
    }
  }

  /**
   * Create a vector of futures for a class.
   *
   * @param clz
   *   Class for future results.
   * @return Vector of futures
   */
  public static <T> Vector<Future<T>> $(Class<T> clz) {
    return new Vector<>();
  }

  /**
   * Wrap an {@link ExecutorService} for {@link MoarThreadSugar}.
   *
   * @param service
   *   Service used for async tasks.
   * @return An async provider that can be used for other calls.
   */
  public static MoarAsyncProvider $(ExecutorService service) {
    return new MoarAsyncProvider() {
      @Override
      public void close() throws Exception {
        shutdown();
      }

      @Override
      public void shutdown() {
        service.shutdown();
      }

      @Override
      public <T> Future<T> submit(Callable<T> call) {
        return service.submit(call);
      }
    };
  }

  /**
   * Get result from the future
   *
   * @param future
   *   Future.
   * @return result.
   */
  public static <T> SafeResult<T> $(Future<T> future) {
    return safely(() -> future.get());
  }

  /**
   * Create a service using a fixed number of threads.
   *
   * @param threads
   *   Number of threads to use.
   * @return Adapter for a service.
   */
  public static MoarAsyncProvider $(int threads) {
    return $(newFixedThreadPool(threads));
  }

  /**
   * Schedule the calls for the future.
   *
   * @param provider
   *   Provider for async functionality.
   * @param futures
   *   Vector of futures.
   * @param calls
   *   Calls to make.
   */
  public static void $(MoarAsyncProvider provider, Vector<Future<Object>> futures, CallableVoid... calls) {
    for (CallableVoid call : calls) {
      $(provider, futures, () -> {
        call.call();
        return null;
      });
    }
  }

  /**
   * Schedule the call for the future.
   *
   * @param provider
   *   Provider for async scheduling.
   * @param futures
   *   Vector of futures.
   * @param calls
   *   Calls to make.
   */
  @SafeVarargs
  public static <T> void $(MoarAsyncProvider provider, Vector<Future<T>> futures, Callable<T>... calls) {
    if (futures == null) {
      throw new NullPointerException("futures");
    }
    for (Callable<T> call : calls) {
      MoarThreadActivity parentActivity = threadActivity.get();
      try {
        Future<T> future = resolve(provider).submit(() -> {
          threadIsAsync.set(true);
          MoarThreadActivity priorActivity = threadActivity.get();
          try {
            threadActivity.set(parentActivity);
            return call.call();
          } finally {
            threadActivity.set(priorActivity);
            threadIsAsync.set(false);
          }
        });
        futures.add(future);
      } catch (Throwable t) {
        // Ignore the exception and attempt to run on our thread.
        futures.add(CompletableFuture.completedFuture(require(() -> call.call())));
      }
    }
  }

  /**
   * Track calls.
   *
   * @param desc
   *   Description for the call.
   * @param calls
   *   The calls
   * @return Result The result of the last call.
   * @throws Exception
   *   Exception thrown by the call.
   */
  @SafeVarargs
  public static <T> T $(String desc, Callable<T>... calls) throws Exception {
    T last = null;
    for (Callable<T> call : calls) {
      if (!trackDetailCosts) {
        last = call.call();
      }
      if (threadActivity.get() == null) {
        last = call.call();
      }
      long clock = currentTimeMillis();
      try {
        last = call.call();
      } finally {
        long cost = currentTimeMillis() - clock;
        accumulate(desc, cost);
        if (cost < TRACE_COST_LIMIT) {
          LOG.trace(cost, desc);
        } else {
          LOG.debug(cost, desc);
        }
      }
    }
    return last;
  }

  /**
   * Run something
   *
   * @param desc
   * @param calls
   */
  public static void $(String desc, CallableVoid... calls) {
    for (CallableVoid call : calls) {
      require(() -> {
        if (!trackDetailCosts) {
          call.call();
          return;
        }
        $(desc, () -> {
          call.call();
          return null;
        });
      });
    }
  }

  /**
   * Complete a batch of futures
   * <p>
   * Wait for all the futures to return. If any futures have exceptions a single
   * {@link FutureListException} is thrown with the results of the batch.
   *
   * @param futures
   * @return list of results
   * @throws Exception
   */
  public static <T> List<T> $(Vector<Future<T>> futures) throws Exception {
    return $("futures " + codeLocationAt(1), () -> {
      List<T> resultList = new ArrayList<>();
      List<SafeResult<Object>> resultWithExceptions = new ArrayList<>();
      Exception exception = null;
      for (Future<T> future : futures) {
        T result;
        try {
          result = future.get();
          exception = null;
        } catch (Exception e) {
          exception = e;
          result = null;
        }
        resultList.add(result);
        resultWithExceptions.add(new SafeResult<>(result, exception));
      }
      if (exception != null) {
        throw new FutureListException(resultWithExceptions);
      }
      return resultList;
    });
  }

  /**
   * Track the cost of an activity and with a scope that follows work across
   * threads.
   *
   * @param calls
   * @return report
   */
  public static MoarThreadReport $$(CallableVoid... calls) {
    return require(() -> {
      if (!trackCosts) {
        // Skip the cost/complexity if we are not tracking detail level costs
        for (CallableVoid call : calls) {
          call.call();
        }
        return new MoarThreadReport(0, emptyList());
      }
      MoarThreadActivity priorActity = threadActivity.get();
      MoarThreadActivity activity = new MoarThreadActivity();
      try {
        threadActivity.set(activity);
        for (CallableVoid call : calls) {
          call.call();
        }
      } finally {
        threadActivity.set(priorActity);
      }
      return activity.describe();
    });
  }

  /**
   * Accumulate costs based on description
   */
  private static void accumulate(String description, long elapsed) {
    MoarThreadActivity activity = threadActivity.get();
    if (activity != null) {
      activity.accumulate(description, elapsed);
    }
  }

  private static MoarAsyncProvider resolve(MoarAsyncProvider async) {
    if (!asyncEnabled) {
      return directAsyncProvider;
    }
    return async;
  }

  /**
   * @param trackCosts
   *   True to configure for tracking costs.
   */
  public static void setTrackCosts(boolean trackCosts) {
    MoarThreadSugar.trackCosts = trackCosts;
  }

  /**
   * @param trackDetailCosts
   *   True to configure for tracking detail costs.
   */
  public static void setTrackDetailCosts(boolean trackDetailCosts) {
    MoarThreadSugar.trackDetailCosts = trackDetailCosts;
  }

}