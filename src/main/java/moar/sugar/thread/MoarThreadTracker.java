package moar.sugar.thread;

import static java.util.Collections.unmodifiableMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import moar.sugar.PropertyAccessor;

public class MoarThreadTracker {
  private static final PropertyAccessor props
      = new PropertyAccessor(MoarThreadTracker.class);
  private static final long bucketSize = props.getLong("bucketSize", 1000L);
  private final String description;
  private final AtomicLong min, max, count, total;
  private final Map<Long, AtomicLong> buckets = new ConcurrentHashMap<>();

  public MoarThreadTracker(final String description) {
    count = new AtomicLong();
    min = new AtomicLong(Long.MAX_VALUE);
    max = new AtomicLong();
    this.description = description;
    total = new AtomicLong();
  }

  public void add(final Long elapsed) {
    updateMin(elapsed);
    updateMax(elapsed);
    count.incrementAndGet();
    total.addAndGet(elapsed);
    final Long key = elapsed / bucketSize;
    getBucket(key).incrementAndGet();
  }

  private AtomicLong getBucket(final Long key) {
    final AtomicLong bucket = getBuckets().get(key);
    if (bucket == null) {
      final AtomicLong newBucket = new AtomicLong();
      buckets.put(key, newBucket);
      return newBucket;
    }
    return bucket;
  }

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

  private void updateMax(final long elapsed) {
    long current;
    do {
      current = max.get();
    } while (!max.compareAndSet(current, Math.max(current, elapsed)));
  }

  private void updateMin(final long elapsed) {
    long current;
    do {
      current = min.get();
    } while (!min.compareAndSet(current, Math.min(current, elapsed)));
  }
}
