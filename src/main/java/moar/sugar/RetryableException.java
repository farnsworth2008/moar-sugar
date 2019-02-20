package moar.sugar;

/**
 * An exception that is retryable.
 * <p>
 * Used in conjunction with {@link Sugar#retryable}
 *
 * @author Mark Farnsworth
 */
public class RetryableException
    extends
    RuntimeException {

  /**
   * Constructs a new exception with the specified cause.
   *
   * @param cause
   */
  public RetryableException(Throwable cause) {
    super(cause);
  }

}
