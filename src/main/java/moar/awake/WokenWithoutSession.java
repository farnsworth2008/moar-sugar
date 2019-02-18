package moar.awake;

import javax.sql.DataSource;

public interface WokenWithoutSession<Row> {
  String getTableName();

  WokenWithSession<Row> of(DataSource ds);

  WokenWithSession<Row> of(WokeSessionBase s);
}
