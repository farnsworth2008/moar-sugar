package moar.awake;

/**
 * Iterator for working with rows.
 *
 * @author Mark Farnsworth
 */
public interface WokeMappableResultSet
    extends
    AutoCloseable {

  WokeMappableRow get();

  boolean next();

}
