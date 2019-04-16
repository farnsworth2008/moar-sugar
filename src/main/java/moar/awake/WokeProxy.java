package moar.awake;

import java.util.Map;

/**
 * Public interface for working with a proxy.
 *
 * @author Mark Farnsworth
 */
public interface WokeProxy {
  boolean isDirty();

  Map<String, Object> toMap();
}
