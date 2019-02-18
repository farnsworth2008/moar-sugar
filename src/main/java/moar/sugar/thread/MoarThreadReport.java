package moar.sugar.thread;

import java.util.List;

public class MoarThreadReport {

  private long time;
  private List<MoarThreadTracker> trackerList;

  public MoarThreadReport(final long time,
      final List<MoarThreadTracker> detail) {
    setTime(time);
    setTrackers(detail);
  }

  public long getTime() {
    return time;
  }

  public MoarThreadTracker getTracker(String desc) {
    for (MoarThreadTracker tracker : trackerList) {
      if (tracker.getDescription().equals(desc)) {
        return tracker;
      }
    }
    return null;
  }

  public List<MoarThreadTracker> getTrackers() {
    return trackerList;
  }

  public void setTime(final long time) {
    this.time = time;
  }

  public void setTrackers(final List<MoarThreadTracker> detail) {
    trackerList = detail;
  }

}
