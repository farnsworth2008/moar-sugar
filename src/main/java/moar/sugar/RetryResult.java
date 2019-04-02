package moar.sugar;

public class RetryResult<T> {

  private final int retryCount;
  private final T result;

  public RetryResult(int retryCount, T result) {
    this.retryCount = retryCount;
    this.result = result;
  }

  public T get() {
    return result;
  }

  public int getRetryCount() {
    return retryCount;
  }

}
