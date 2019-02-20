package moar.sugar.thread;

import static java.lang.Math.min;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static moar.sugar.Sugar.codeLocationAt;
import static moar.sugar.Sugar.require;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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
  private static class Activity {
    private final Map<String, MoarThreadTracker> costMap = new ConcurrentHashMap<>();
    private final long start = currentTimeMillis();
    private final AtomicLong cost = new AtomicLong();

    void accumulate(String description, long elapsed) {
      synchronized (costMap) {
        if (!costMap.containsKey(description)) {
          costMap.put(description, new MoarThreadTracker(description));
        }
        MoarThreadTracker mapEntry = costMap.get(description);
        mapEntry.add(elapsed);
        cost.addAndGet(elapsed);
      }
    }

    MoarThreadReport describe() {
      long cost = currentTimeMillis() - start;
      List<MoarThreadTracker> sortedCosts = new ArrayList<>();
      for (String desc : costMap.keySet()) {
        MoarThreadTracker entry = costMap.get(desc);
        sortedCosts.add(entry);
      }
      Collections.sort(sortedCosts, (o1, o2) -> {
        String o1Desc = o1.getDescription();
        String o2Desc = o2.getDescription();
        return o1Desc.compareTo(o2Desc);
      });
      return new MoarThreadReport(cost, sortedCosts);
    }
  }

  private static MoarLogger LOG = new MoarLogger(MoarThreadSugar.class);
  private static PropertyAccessor prop = new PropertyAccessor(MoarThreadSugar.class.getName());
  private static boolean asyncEnabled = prop.getBoolean("async", true);
  private static long TRACE_COST_LIMIT = prop.getLong("traceCostLimit", 10 * 1000L);
  private static ThreadLocal<Activity> threadActivity = new ThreadLocal<>();
  private static ThreadLocal<Boolean> threadIsAsync = new ThreadLocal<>();
  private static ListeningExecutorService directExecutorService = MoreExecutors.newDirectExecutorService();
  private static boolean trackCosts = prop.getBoolean("trackCosts", true);
  private static boolean trackDetailCosts = prop.getBoolean("trackDetailCosts", true);
  private static MoarAsyncProvider directAsyncProvider = new MoarAsyncProvider() {
    @Override
    public <T> Future<T> submit(Callable<T> c) {
      return directExecutorService.submit(c);
    }

    @Override
    public void shutdown() {}
  };
  private static MoarJson moarJson = MoarJson.getMoarJson();

  /**
   * @return Vector of futures.
   */
  public static Vector<Future<Object>> $() {
    return $(Object.class);
  }

  /**
   * Schedule the call to execute in the future using the provider.
   *
   * @param provider
   *   Provider
   * @param call
   *   The call
   * @return A future.
   */
  public static <T> Future<T> $(MoarAsyncProvider provider, Callable<T> call) {
    Vector<Future<T>> futures = new Vector<>();
    $(provider, futures, call);
    return futures.get(0);
  }

  /**
   * Schedule the call for the future.
   *
   * @param provider
   * @param call
   * @return A future
   */
  public static Future<Object> $(MoarAsyncProvider provider, CallableVoid call) {
    Vector<Future<Object>> futures = new Vector<>();
    $(provider, futures, call);
    return futures.get(0);
  }

  /**
   * Schedule the call for the future.
   *
   * @param provider
   * @param futures
   *   Vector of futures.
   * @param call
   */
  public static void $(MoarAsyncProvider provider, Vector<Future<Object>> futures, CallableVoid call) {
    $(provider, futures, () -> {
      call.call();
      return null;
    });
  }

  /**
   * Schedule the call for the future.
   *
   * @param provider
   * @param futures
   *   Vector of futures.
   * @param call
   */
  public static <T> void $(MoarAsyncProvider provider, Vector<Future<T>> futures, Callable<T> call) {
    if (futures == null) {
      throw new NullPointerException("futures");
    }
    Activity parentActivity = threadActivity.get();
    try {
      Future<T> future = resolve(provider).submit(() -> {
        threadIsAsync.set(true);
        Activity priorActivity = threadActivity.get();
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

  /**
   * Execute a call on the current thread with tracking.
   *
   * @param call
   * @return The result
   * @throws Exception
   */
  public static <T> T $(Callable<T> call) throws Exception {
    return $(codeLocationAt(1), call);
  }

  /**
   * Create a vector of futures for a class.
   *
   * @param clz
   * @return Vector of futures
   */
  public static <T> Vector<Future<T>> $(Class<T> clz) {
    return new Vector<>();
  }

  /**
   * Wrap an {@link ExecutorService} for {@link MoarThreadSugar}.
   *
   * @param service
   * @return An async provider that can be used for other calls.
   */
  public static MoarAsyncProvider $(ExecutorService service) {
    return new MoarAsyncProvider() {
      @Override
      public <T> Future<T> submit(Callable<T> call) {
        return service.submit(call);
      }

      @Override
      public void shutdown() {
        service.shutdown();
      }
    };
  }

  /**
   * Get result from the future
   *
   * @param future
   * @return result.
   */
  public static <T> T $(Future<T> future) {
    return require(() -> future.get());
  }

  /**
   * Create a service using a fixed number of threads.
   *
   * @param nThreads
   * @return Adapter for a service.
   */
  public static MoarAsyncProvider $(int nThreads) {
    return $(newFixedThreadPool(nThreads));
  }

  /**
   * @param log
   * @param desc
   * @param call
   * @return result
   * @throws Exception
   * @deprecated
   */
  @Deprecated
  public static <T> T $(MoarLogger log, String desc, Callable<T> call) throws Exception {
    long start = currentTimeMillis();
    try {
      log.debug(desc);
      return call.call();
    } finally {
      log.debug(currentTimeMillis() - start, desc);
    }
  }

  /**
   * @param log
   * @param desc
   * @param call
   * @throws Exception
   * @deprecated
   */
  @Deprecated
  public static void $(MoarLogger log, String desc, CallableVoid call) throws Exception {
    $(log, desc, () -> {
      call.call();
      return null;
    });
  }

  /**
   * Call with tracking.
   *
   * @param call
   */
  public static void $(CallableVoid call) {
    $(codeLocationAt(1), call);
  }

  /**
   * @param desc
   * @param callable
   * @return Result
   * @throws Exception
   */
  public static <T> T $(String desc, Callable<T> callable) throws Exception {
    if (!trackDetailCosts) {
      return callable.call();
    }
    if (threadActivity.get() == null) {
      return callable.call();
    }
    long clock = currentTimeMillis();
    try {
      return callable.call();
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

  /**
   * Create a proxy to track the cost of the methods
   *
   * @param baseDesc
   * @param clz
   * @param r
   * @return proxy
   */
  @SuppressWarnings("unchecked")
  public static <T> T $(String baseDesc, Class<?> clz, T r) {
    if (!trackDetailCosts) {
      return r;
    }
    String simpleName = clz.getSimpleName();
    if (!clz.isInterface()) {
      LOG.warn(clz.getSimpleName(), "Unable to track cost because it is not an interface");
      return r;
    }
    ClassLoader c = MoarThreadSugar.class.getClassLoader();
    Class<?>[] cc = { clz };
    return (T) Proxy.newProxyInstance(c, cc, (proxy, method, args) -> {
      String desc;
      if (isRestExchange(r, method, args)) {
        URI uri = (URI) args[0];
        desc = uri.toString();
      } else if (clz == Statement.class && method.getName().equals("execute") && args.length == 1
          && args[0] instanceof String) {
        String sql = (String) args[0];
        desc = sql.substring(0, min(sql.length(), 40));
      } else {
        List<String> pTypes = new ArrayList<>();
        for (Class<?> p : method.getParameterTypes()) {
          pTypes.add(p.getSimpleName());
        }
        desc = moarJson.toJsonSafely(simpleName, method.getName(), pTypes);
      }
      try {
        return $(baseDesc, () -> $(desc, () -> method.invoke(r, args)));
      } catch (UndeclaredThrowableException e1) {
        throw e1.getCause();
      } catch (InvocationTargetException e2) {
        throw e2.getCause();
      } catch (Exception e3) {
        throw e3;
      }
    });
  }

  /**
   * Run something
   *
   * @param desc
   * @param call
   */
  public static void $(String desc, CallableVoid call) {
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
   * @param call
   * @return report
   */
  public static MoarThreadReport $$(CallableVoid call) {
    return require(() -> {
      if (!trackCosts) {
        // Skip the cost/complexity if we are not tracking detail level costs
        call.call();
        return new MoarThreadReport(0, emptyList());
      }
      Activity priorActity = threadActivity.get();
      Activity activity = new Activity();
      try {
        threadActivity.set(activity);
        call.call();
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
    Activity activity = threadActivity.get();
    if (activity != null) {
      activity.accumulate(description, elapsed);
    }
  }

  /**
   * Detect rest exchange so we can provide better descriptions.
   */
  private static <T> boolean isRestExchange(T r, Method method, Object[] args) {
    if (method.getName().equals("exchange") && args != null) {
      if (args.length == 4 && args[0] instanceof URI) {
        return true;
      }
    }
    return false;
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