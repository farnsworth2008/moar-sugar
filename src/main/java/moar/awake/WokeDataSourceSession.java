package moar.awake;
import static moar.sugar.Sugar.require;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;

/**
 * A data source based session.
 *
 * @author Mark Farnsworth
 */
public class WokeDataSourceSession
    extends
    WokeSession {

  private final DataSource ds;

  public WokeDataSourceSession(DataSource ds) {
    this.ds = ds;
  }

  @Override
  public ConnectionHold reserve() {
    Connection cn = require(() -> ds.getConnection());
    DatabaseMetaData md = require(() -> cn.getMetaData());
    return new ConnectionHold() {

      @Override
      public void close() {
        require(() -> cn.close());
      }

      @Override
      public Connection get() {
        return cn;
      }

      @Override
      public String getIdentifierQuoteString() {
        return require(() -> md.getIdentifierQuoteString());
      }
    };
  }

}
