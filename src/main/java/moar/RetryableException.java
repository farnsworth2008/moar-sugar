package moar;

import java.sql.SQLTransactionRollbackException;

public class RetryableException
    extends
    RuntimeException {

  public RetryableException(final SQLTransactionRollbackException e) {
    super(e);
  }

}
