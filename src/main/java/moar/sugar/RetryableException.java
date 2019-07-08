package moar.sugar;

/**
 * An exception that is retryable.
 * <p>
 * Used in conjunction with {@link Sugar#retryable}
 *
 * @author Mark Farnsworth
 */
@SuppressWarnings("serial")
public class RetryableException
    extends
    RuntimeException {

  public RetryableException(String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the specified cause.
   *
   * @param cause
   *   What caused the exception.
   */
  public RetryableException(Throwable cause) {
    super(cause);
  }

}
