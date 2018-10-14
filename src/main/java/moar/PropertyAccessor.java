package moar;

import static moar.JsonUtil.debug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bit of syntactical sugar to make it easier to interact with System property methods.
 * <p>
 * Properties created via this class are automatically named based on this package (i.e. medlio) and the base name
 * supplied via the constructor.
 */
public class PropertyAccessor {
  Logger LOG = LoggerFactory.getLogger(PropertyAccessor.class);

  private final String baseName;

  public PropertyAccessor(final Class<?> clz) {
    this(clz.getName());
  }

  public PropertyAccessor(final String baseName) {
    this.baseName = baseName;
  }

  public boolean getBoolean(final String name, final Boolean defaultValue) {
    return Boolean.valueOf(getString(name, defaultValue.toString()));
  }

  public Integer getInteger(final String name, final Integer defaultValue) {
    return Integer.valueOf(getString(name, defaultValue.toString()));
  }

  public Long getLong(final String name, final Long defaultValue) {
    return Long.valueOf(getString(name, defaultValue.toString()));
  }

  public String getString(final String name, final String defaultValue) {
    final String key = baseName + "." + name;
    final String value = System.getProperty(key, defaultValue);
    debug(LOG, key, value, defaultValue);
    return value;
  }
}
