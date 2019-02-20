package moar.awake;

import static moar.sugar.Sugar.asRuntimeException;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.safely;
import java.sql.Connection;

public class WokeTxSession
    extends
    WokeSessionBase
    implements
    AutoCloseable {
  private final ConnectionHold connectionHold;

  @SuppressWarnings("resource")
  WokeTxSession(ConnectionHold connectionHold) {
    this.connectionHold = connectionHold;
    require(() -> {
      Connection cn = connectionHold.get();
      cn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
      cn.setAutoCommit(false);
    });
  }

  @Override
  public void close() throws Exception {
    Throwable e = safely(() -> connectionHold.get().setAutoCommit(true));
    connectionHold.close();
    if (e != null) {
      throw asRuntimeException(e);
    }
  }

  @Override
  public ConnectionHold reserve() {
    return new ConnectionHold() {

      @Override
      public void close() {
        // Do nothing; we are in a TX
      }

      @Override
      public Connection get() {
        return connectionHold.get();
      }

      @Override
      public String getIdentifierQuoteString() {
        return connectionHold.getIdentifierQuoteString();
      }
    };
  }

  public void rollback() {
    require(() -> connectionHold.get().rollback());
  }

}
