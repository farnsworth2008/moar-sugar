package moar.sugar;

import java.util.function.Function;

/**
 * A bit of syntactical sugar to make it easier to interact with properties.
 * <p>
 * Properties automatically use names based the base name supplied via the
 * constructor.
 */
public class PropertyAccessor {
  MoarLogger LOG = new MoarLogger(PropertyAccessor.class);

  private final String baseName;

  private final Function<String, String> fetch;

  public PropertyAccessor() {
    this("");
  }

  public PropertyAccessor(final Class<?> clz) {
    this(clz.getName());
  }

  public PropertyAccessor(final Class<?> clz,
      final Function<String, String> fetch) {
    this(clz.getName(), fetch);
  }

  public PropertyAccessor(final String baseName) {
    this(baseName, name -> null);
  }

  public PropertyAccessor(final String baseName,
      final Function<String, String> fetch) {
    this.baseName = baseName;
    this.fetch = fetch;
  }

  public boolean getBoolean(final String name, final Boolean defaultValue) {
    return Boolean.valueOf(getString(name, defaultValue.toString()));
  }

  public Double getDouble(final String name, final Double defaultValue) {
    return Double.valueOf(getString(name, defaultValue.toString()));
  }

  public Double getDouble(final String name, final Float defaultValue) {
    return getDouble(name, defaultValue.doubleValue());
  }

  public Double getDouble(final String name, final Integer defaultValue) {
    return getDouble(name, defaultValue.doubleValue());
  }

  private String getEnvName(final String key) {
    return key.toUpperCase().replace('.', '_');
  }

  public Integer getInteger(final String name, final Integer defaultValue) {
    return Integer.valueOf(getString(name, defaultValue.toString()));
  }

  public Long getLong(final String name, final Integer i) {
    return getLong(name, i.longValue());
  }

  public Long getLong(final String name, final Long defaultValue) {
    return Long.valueOf(getString(name, defaultValue.toString()));
  }

  public String getString(String name) {
    return getString(name, null);
  }

  public String getString(final String name, final String defaultValue) {
    final String key = baseName + "." + name;
    final String envName = getEnvName(key);
    final String envValue = System.getenv(envName);
    String downStreamValue = null;
    if (envValue != null) {
      downStreamValue = envValue;
    }
    final String fetchedValue = fetch.apply(key);
    if (fetchedValue != null) {
      downStreamValue = fetchedValue;
    }
    if (downStreamValue == null) {
      downStreamValue = defaultValue;
    }
    return System.getProperty(key, downStreamValue);
  }
}
