package moar.sugar;

/**
 * Holds a safe result.
 * <p>
 * The result of {@link Sugar#safely}
 * <p>
 * On a safe result, any throwable is simply captured and stored for retrieval
 * via {@link SafeResult#thrown}
 *
 * @author Mark Farnsworth
 * @param <T>
 *   Type of the safe result.
 */
public class SafeResult<T> {

  private final T result;
  private final Throwable throwable;

  /**
   * Create a safe result.
   * 
   * @param result
   * @param throwable
   */
  public SafeResult(T result, Throwable throwable) {
    this.result = result;
    this.throwable = throwable;
  }

  /**
   * @return result
   */
  public T get() {
    return result;
  }

  /**
   * @return Exception or null
   */
  public Throwable thrown() {
    return throwable;
  }

}
