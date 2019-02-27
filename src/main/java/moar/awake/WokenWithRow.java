package moar.awake;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for a context where we have a row (or part of the row).
 *
 * @author Mark Farnsworth
 * @param <Row>
 */
public interface WokenWithRow<Row> {
  void delete();

  Row find();

  Row insert();

  Row insert(Consumer<Row> r);

  List<Row> list();

  Row upsert();

  Row upsert(Consumer<Row> row);

  WokenWithRow<Row> where(Consumer<Row> r);
}
