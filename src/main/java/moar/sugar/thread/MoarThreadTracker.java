package moar.sugar.thread;

import static java.util.Collections.unmodifiableMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import moar.sugar.PropertyAccessor;

/**
 * Thread tracker
 *
 * @author Mark Farnsworth
 */
public class MoarThreadTracker {
  private static PropertyAccessor props = new PropertyAccessor(MoarThreadTracker.class);
  private static long bucketSize = props.getLong("bucketSize", 1000L);
  private final String description;
  private final AtomicLong min, max, count, total;
  private final Map<Long, AtomicLong> buckets = new ConcurrentHashMap<>();

  /**
   * Create thread tracker
   *
   * @param desc
   */
  public MoarThreadTracker(String desc) {
    count = new AtomicLong();
    min = new AtomicLong(Long.MAX_VALUE);
    max = new AtomicLong();
    description = desc;
    total = new AtomicLong();
  }

  /**
   * Add elapsed time
   *
   * @param elap
   */
  public void add(Long elap) {
    updateMin(elap);
    updateMax(elap);
    count.incrementAndGet();
    total.addAndGet(elap);
    Long key = elap / bucketSize;
    getBucket(key).incrementAndGet();
  }

  private AtomicLong getBucket(Long key) {
    AtomicLong bucket = getBuckets().get(key);
    if (bucket == null) {
      AtomicLong newBucket = new AtomicLong();
      buckets.put(key, newBucket);
      return newBucket;
    }
    return bucket;
  }

  /**
   * @return Map of buckets
   */
  public Map<Long, AtomicLong> getBuckets() {
    return unmodifiableMap(buckets);
  }

  public Long getCount() {
    return count.get();
  }

  public String getDescription() {
    return description;
  }

  public Long getMax() {
    return max.get();
  }

  public Long getMin() {
    return min.get();
  }

  public Long getTotal() {
    return total.get();
  }

  private void updateMax(long elapsed) {
    long current;
    do {
      current = max.get();
    } while (!max.compareAndSet(current, Math.max(current, elapsed)));
  }

  private void updateMin(long elapsed) {
    long current;
    do {
      current = min.get();
    } while (!min.compareAndSet(current, Math.min(current, elapsed)));
  }
}
