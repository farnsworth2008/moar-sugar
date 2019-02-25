package moar.awake;
import static moar.awake.WakeUtil.wake;
import static moar.sugar.Sugar.require;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.function.Consumer;
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

  /**
   * Upsert multiples.
   *
   * @param clz
   *   Class for entity.
   * @param updators
   *   One or more updators
   */
  @SafeVarargs
  public final <T> void upsert(Class<T> clz, Consumer<T>... updators) {
    WokenWithSession<T> repo = wake(clz).of(ds);
    for (Consumer<T> updator : updators) {
      repo.upsert(updator);
    }
  }

}
