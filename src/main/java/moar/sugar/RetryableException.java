package moar.sugar;

/**
 * An exception that is retryable.
 * <p>
 * Used in congunction with {@link Sugar#retryable}
 *
 * @author Mark Farnsworth
 */
public class RetryableException
    extends
    RuntimeException {

  public RetryableException(final Throwable cause) {
    super(cause);
  }

}
