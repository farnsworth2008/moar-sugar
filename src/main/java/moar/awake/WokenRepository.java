package moar.awake;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Context for when we have a session.
 *
 * @author Mark Farnsworth
 * @param <Row>
 *   Row type
 */
public interface WokenRepository<Row> {
  Row define();

  Row define(Consumer<Row> row);

  void delete(Row row);

  void delete(String where, Object... params);

  WokenWithRow<Row> id(Long id);

  WokenWithRow<Row> id(String id);

  WokenWithRow<Row> id(UUID id);

  Row insert(Consumer<Row> row);

  Row insert(Row row);

  void insertBatch(List<Row> rows);

  WokeResultSet<Row> iterator(String where, Object... params);

  List<Row> list(String where, Object... params);

  void update(Row row);

  Row upsert(Consumer<Row> row);

  Row upsert(Row row);

  WokenWithRow<Row> where(Consumer<Row> row);
}
