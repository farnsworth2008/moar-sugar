package moar.awake;

/**
 * An interface for iterating over rows.
 *
 * @author Mark Farnsworth
 * @param <Row>
 */
public interface WokeResultSet<Row>
    extends
    AutoCloseable {

  Row get();

  boolean next();
}
