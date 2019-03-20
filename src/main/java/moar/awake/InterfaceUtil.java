package moar.awake;

import static moar.sugar.Sugar.require;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class InterfaceUtil {
  static <Row> WokePrivateProxy asWokeProxy(Row row) {
    return ((WokeProxiedObject) row).privateProxy();
  }

  private static CacheBuilder<Object, Object> cache(int maxSize) {
    return CacheBuilder.newBuilder().maximumSize(maxSize);
  }

  public static <T> LoadingCache<Long, T> createLoadingCache(DataSource dataSource, int maxSize, Class<T> clz) {
    return cache(maxSize).build(new CacheLoader<Long, T>() {
      @Override
      public T load(Long id) throws Exception {
        return findOrDefine(dataSource, id, clz);
      }
    });
  }

  private static <T> T findOrDefine(DataSource ds, Long id, Class<T> clz) {
    WokenWithSession<T> repo = use(clz).of(ds);
    T row = repo.id(id).find();
    if (row == null) {
      return repo.define();
    }
    return row;
  }

  public static <Row> WokenWithoutSession<Row> use(Class<Row> clz) {
    return new WokeRepository<>(clz);
  }

  public static <Row> WokenWithoutSession<Row> use(Class<Row> clz, String tableName) {
    return new WokeRepository<>(clz, tableName);
  }

  public static WokeDataSourceSession use(DataSource ds) {
    return new WokeDataSourceSession(ds);
  }

  public static WokeProxy use(Object object) {
    return asWokeProxy(object);
  }

  public static <Row> List<Row> use(WokeResultSet<Row> iter) {
    try {
      return use(iter, Integer.MAX_VALUE);
    } finally {
      require(() -> iter.close());
    }
  }

  public static <Row> List<Row> use(WokeResultSet<Row> iter, int limit) {
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
