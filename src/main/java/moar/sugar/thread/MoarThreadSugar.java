package moar.sugar.thread;

import static java.lang.Math.min;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import moar.sugar.MoarJson;
import moar.sugar.MoarLogger;
import moar.sugar.PropertyAccessor;
import moar.sugar.SafeResult;
import moar.sugar.Sugar;

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

  public interface AsyncProvider {
    <T> Future<T> submit(Callable<T> call);
  }

  public static class AsyncService
      implements
      AsyncProvider {

    private final ExecutorService executorService;

    public AsyncService(ExecutorService executorService) {
      this.executorService = executorService;
    }

    public void shutdown() {
      executorService.shutdown();
    }

    @Override
    public <T> Future<T> submit(Callable<T> call) {
      return executorService.submit(call);
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
  private static AsyncProvider directAsyncProvider = new MoarThreadSugar.AsyncProvider() {
    @Override
    public <T> Future<T> submit(Callable<T> c) {
      return directExecutorService.submit(c);
    }
  };
  private static MoarJson moarJson = MoarJson.getMoarJson();

  public static Vector<Future<Object>> $() {
    return $(Object.class);
  }

  public static <T> Future<T> $(AsyncProvider provider, Callable<T> callable) {
    Vector<Future<T>> futures = new Vector<>();
    $(provider, futures, callable);
    return futures.get(0);
  }

  public static Future<Object> $(AsyncProvider provider, Runnable runnable) {
    Vector<Future<Object>> futures = new Vector<>();
    $(provider, futures, runnable);
    return futures.get(0);
  }

  /**
   * submit a runnable
   */
  public static void $(AsyncProvider provider, Vector<Future<Object>> futures, Runnable runnable) {
    $(provider, futures, () -> {
      runnable.run();
      return null;
    });
  }

  public static <T> void $(AsyncProvider provider, Vector<Future<T>> futures, Callable<T> callable) {
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
          return callable.call();
        } finally {
          threadActivity.set(priorActivity);
          threadIsAsync.set(false);
        }
      });
      futures.add(future);
    } catch (Throwable t) {
      // Ignore the exception and attempt to run on our thread.
      futures.add(CompletableFuture.completedFuture(require(() -> callable.call())));
    }
  }

  public static <T> T $(Callable<T> call) throws Exception {
    return $(Sugar.codeLocationAt(1), call);
  }

  public static <T> Vector<Future<T>> $(Class<T> clz) {
    return new Vector<>();
  }

  public static AsyncProvider $(ExecutorService service) {
    return new AsyncProvider() {
      @Override
      public <T> Future<T> submit(Callable<T> call) {
        return service.submit(call);
      }
    };
  }

  /**
   * Get a single futures result
   */
  public static <T> T $(Future<T> future) {
    return require(() -> {
      return future.get();
    });
  }

  public static AsyncService $(int nThreads) {
    return new AsyncService(Executors.newFixedThreadPool(nThreads));
  }

  public static <T> T $(MoarLogger log, String desc, Callable<T> callable) throws Exception {
    long start = currentTimeMillis();
    try {
      log.debug(desc);
      return callable.call();
    } finally {
      log.debug(currentTimeMillis() - start, desc);
    }
  }

  public static void $(MoarLogger log, String desc, Runnable r) throws Exception {
    $(log, desc, () -> {
      r.run();
      return null;
    });
  }

  public static void $(Runnable r) {
    $(Sugar.codeLocationAt(1), r);
  }

  public static <T> T $(String description, Callable<T> callable) throws Exception {
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
      accumulate(description, cost);
      if (cost < TRACE_COST_LIMIT) {
        LOG.trace(cost, description);
      } else {
        LOG.debug(cost, description);
      }
    }
  }

  /**
   * Create a proxy to track the cost of the methods
   */
  @SuppressWarnings("unchecked")
  public static <T> T $(String generalDescription, Class<?> clz, T r) {
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
        return $(generalDescription, () -> $(desc, () -> method.invoke(r, args)));
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
   */
  public static void $(String description, Runnable runnable) {
    if (!trackDetailCosts) {
      runnable.run();
      return;
    }
    require(() -> {
      $(description, () -> {
        runnable.run();
        return null;
      });
    });
  }

  /**
   * Complete a batch of futures
   * <p>
   * Wait for all the futures to return. If any futures have exceptions a single
   * {@link FutureListException} is thrown with the results of the batch.
   */
  public static <T> List<T> $(Vector<Future<T>> futuresParam) throws Exception {
    return $("futures " + Sugar.codeLocationAt(1), () -> {
      List<T> resultList = new ArrayList<>();
      List<SafeResult<Object>> resultWithExceptions = new ArrayList<>();
      Exception exception = null;
      Vector<Future<T>> futures = futuresParam;
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
   */
  public static MoarThreadReport $$(Runnable r) {
    if (!trackCosts) {
      // Skip the cost/complexity if we are not tracking detail level costs
      r.run();
      return new MoarThreadReport(0, emptyList());
    }
    Activity priorActity = threadActivity.get();
    Activity activity = new Activity();
    try {
      threadActivity.set(activity);
      r.run();
    } finally {
      threadActivity.set(priorActity);
    }
    return activity.describe();
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

  private static AsyncProvider resolve(AsyncProvider async) {
    if (!asyncEnabled) {
      return directAsyncProvider;
    }
    return async;
  }

  public static void setTrackCosts(boolean trackCosts) {
    MoarThreadSugar.trackCosts = trackCosts;
  }

  public static void setTrackDetailCosts(boolean trackDetailCosts) {
    MoarThreadSugar.trackDetailCosts = trackDetailCosts;
  }

}