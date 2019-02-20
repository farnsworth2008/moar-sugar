package moar.sugar.thread;

import java.util.List;

/**
 * Report of activity that was tracked in a thread.
 *
 * @author Mark Farnsworth
 */
public class MoarThreadReport {

  private final long time;
  private final List<MoarThreadTracker> trackerList;

  /**
   * Create Report
   *
   * @param time
   * @param detail
   * Detail Trackers
   */
  public MoarThreadReport(long time, List<MoarThreadTracker> detail) {
    this.time = time;
    trackerList = detail;
  }

  /**
   * @return Total time in milliseconds
   */
  public long getTime() {
    return time;
  }

  /**
   * Get the tracker for a description
   *
   * @param desc
   * @return Tracker for the description
   */
  public MoarThreadTracker getTracker(String desc) {
    for (MoarThreadTracker tracker : trackerList) {
      if (tracker.getDescription().equals(desc)) {
        return tracker;
      }
    }
    return null;
  }

  /**
   * @return List of Trackers
   */
  public List<MoarThreadTracker> getTrackers() {
    return trackerList;
  }

}
