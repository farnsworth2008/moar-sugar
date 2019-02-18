package moar.awake;

import static moar.awake.Waker.asWokeProxy;

/**
 * A generic row that can be mapped to specific row classes while iterating.
 * <p>
 * {@link WokeSessionBase#iterator} provides a way to execute queries.
 * {@link WokeMappableResultSet} allows the results to be traversed and
 * {@link WokeMappableRow} allows the row to be mapped to Row interfaces.
 *
 * @author Mark Farnsworth
 */
public class WokeMappableRow {

  private final Object[] rowObjects;

  WokeMappableRow(final Object[] rowObjects) {
    this.rowObjects = rowObjects;
  }

  @SuppressWarnings("unchecked")
  public <Row> Row get(final Class<Row> clz) {
    for (final Object object : rowObjects) {
      if (asWokeProxy(object).getTargetClass() == clz) {
        return (Row) object;
      }
    }
    return null;
  }

}
