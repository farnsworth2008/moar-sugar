package moar.awake;

import static moar.sugar.Sugar.require;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public class WakeUtil {
  static <Row> WokePrivateProxy asWokeProxy(Row row) {
    return ((WokeProxiedObject) row).privateProxy();
  }

  /**
   * @param clz
   * @return Waker configured for the class.
   */
  public static <Row> WokenWithoutSession<Row> wake(Class<Row> clz) {
    return new WokeRepository<>(clz);
  }

  public static <Row> WokenWithoutSession<Row> wake(Class<Row> clz, String tableName) {
    return new WokeRepository<>(clz, tableName);
  }

  public static WokeDataSourceSession wake(DataSource ds) {
    return new WokeDataSourceSession(ds);
  }

  public static WokeProxy wake(Object object) {
    return asWokeProxy(object);
  }

  public static <Row> List<Row> wake(WokeResultSet<Row> iter) {
    try {
      return wake(iter, Integer.MAX_VALUE);
    } finally {
      require(() -> iter.close());
    }
  }

  public static <Row> List<Row> wake(WokeResultSet<Row> iter, int limit) {
    List<Row> list = new ArrayList<>();
    while (iter.next()) {
      list.add(iter.get());
      if (list.size() >= limit) {
        return list;
      }
    }
    return list;
  }
}
