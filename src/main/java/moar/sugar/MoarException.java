package moar.sugar;

/**
 * A {@link RuntimeException} where the message is a string with a JSON
 * formatted representation of objects that are related to the problem.
 *
 * @author Mark Farnsworth
 */
public class MoarException
    extends
    RuntimeException {

  private static MoarJson moarJson = MoarJson.getMoarJson();

  /**
   * Constructs a exception with the specified detail message.
   *
   * @param args
   *   Objects of interest.
   */
  public MoarException(final Object... args) {
    super(moarJson.toJsonSafely(args));
  }

  /**
   * Constructs a exception with the specified detail message.
   *
   * @param cause
   *   the cause (which is saved for later retrieval by the {@link #getCause()}
   *   method). (A <tt>null</tt> value is permitted, and indicates that the
   *   cause is nonexistent or unknown.)
   * @param args
   *   Objects of interest.
   */
  public MoarException(final Throwable cause, final Object... args) {
    super(moarJson.toJsonSafely(args), cause);
  }

}
