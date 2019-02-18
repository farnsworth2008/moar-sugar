package moar.awake;

import java.sql.Connection;

/**
 * A hold on the real connection.
 * <p>
 * The hold allows parts of the system that need to keep a connection open to
 * hold this reference until they are done with their work.
 *
 * @author Mark Farnsworth
 */
interface ConnectionHold
    extends
    AutoCloseable {

  @Override
  void close();

  Connection get();

  String getIdentifierQuoteString();

}
