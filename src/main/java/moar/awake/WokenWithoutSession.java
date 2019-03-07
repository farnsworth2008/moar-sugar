package moar.awake;

import java.util.Map;
import javax.sql.DataSource;

public interface WokenWithoutSession<Row> {
  String getTableName();

  WokenWithSession<Row> of(DataSource ds);

  Row of(Map<String, Object> map);

  WokenWithSession<Row> of(WokeSessionBase s);
}
