package moar.sugar.thread;

import static java.lang.System.currentTimeMillis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class MoarThreadActivity {
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