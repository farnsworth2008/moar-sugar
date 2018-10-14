package moar;

public class StringMessageException
    extends
    RuntimeException {
  public StringMessageException(final String message) {
    super(message);
  }
}
