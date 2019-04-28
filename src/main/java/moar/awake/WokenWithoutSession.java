package moar.awake;

import java.util.Map;
import javax.sql.DataSource;

public interface WokenWithoutSession<Row> {
  String getTableName();

  WokenRepository<Row> of(DataSource ds);

  Row of(Map<String, Object> map);

  WokenRepository<Row> of(WokeSessionBase s);
}
