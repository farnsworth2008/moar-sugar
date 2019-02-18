package moar.sugar;

/**
 * Holds a safe result.
 * <p>
 * The result of {@link Sugar#safely}
 * <p>
 * On a safe result, any throwable is simply captured and stored for retrival
 * via {@link SafeResult#getThrowable}
 *
 * @author Mark Farnsworth
 * @param <T>
 *   Type of the safe result.
 */
public class SafeResult<T> {

  private T result;
  private Throwable throwable;

  public SafeResult(T result, Throwable throwable) {
    this.setResult(result);
    this.setThrowable(throwable);
  }

  public T get() {
    return result;
  }

  public void setResult(T result) {
    this.result = result;
  }

  public void setThrowable(Throwable throwable) {
    this.throwable = throwable;
  }

  public Throwable thrown() {
    return throwable;
  }

}
