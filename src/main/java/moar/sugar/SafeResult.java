package moar.sugar;

import static moar.sugar.Sugar.asRuntimeException;

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
   *   The result.
   * @param throwable
   *   Something that was thrown.
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

  public T getOrThrow() {
    if (threw()) {
      throw asRuntimeException(thrown());
    }
    return get();
  }

  /**
   * Determine if an exception was thrown.
   *
   * @return true if the result has a thrown exception.
   */
  public Boolean threw() {
    return throwable != null;
  }

  /**
   * @return Exception or null
   */
  public Throwable thrown() {
    return throwable;
  }

}
