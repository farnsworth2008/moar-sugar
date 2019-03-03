package moar.sugar;

public class SilentResult<T> {

  private final T result;
  private final byte[] outBytes;
  private final byte[] errBytes;

  public SilentResult(T result, byte[] outBytes, byte[] errBytes) {
    this.result = result;
    this.outBytes = outBytes;
    this.errBytes = errBytes;
  }

  public T get() {
    return result;
  }

  public String getErr() {
    return new String(errBytes);
  }

  public String getOut() {
    return new String(outBytes);
  }
}
