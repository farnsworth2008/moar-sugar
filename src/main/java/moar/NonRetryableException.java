package moar;

public class NonRetryableException
    extends
    RuntimeException {

  public NonRetryableException(final Throwable e) {
    super(e);
  }

}
