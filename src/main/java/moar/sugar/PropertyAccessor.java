package moar.sugar;

import static java.lang.System.getenv;
import java.util.function.Function;
import com.google.common.base.CaseFormat;

/**
 * A bit of syntactical sugar to make it easier to interact with properties.
 * <p>
 * Properties automatically use names based the base name supplied via the
 * constructor.
 */
@SuppressWarnings("javadoc")
public class PropertyAccessor {
  MoarLogger LOG = new MoarLogger(PropertyAccessor.class);

  private final String baseName;

  private final Function<String, String> fetch;

  public PropertyAccessor() {
    this("");
  }

  public PropertyAccessor(Class<?> clz) {
    this(clz.getSimpleName());
  }

  public PropertyAccessor(Class<?> clz, Function<String, String> fetch) {
    this(clz.getName(), fetch);
  }

  public PropertyAccessor(String baseName) {
    this(baseName, name -> null);
  }

  public PropertyAccessor(String baseName, Function<String, String> fetch) {
    this.baseName = baseName;
    this.fetch = fetch;
  }

  public Boolean getBoolean(String name, Boolean defaultValue) {
    return Boolean.valueOf(getString(name, defaultValue.toString()));
  }

  public Double getDouble(String name, Double defaultValue) {
    return Double.valueOf(getString(name, defaultValue.toString()));
  }

  public Double getDouble(String name, Float defaultValue) {
    return getDouble(name, defaultValue.doubleValue());
  }

  public Double getDouble(String name, Integer defaultValue) {
    return getDouble(name, defaultValue.doubleValue());
  }

  private String getEnvName(String key) {
    String name = CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.UPPER_UNDERSCORE).convert(key);
    name = name.replace('.', '_');
    name = name.replaceAll("__", "_");
    return name;
  }

  public Integer getInteger(String name, Integer defaultValue) {
    return Integer.valueOf(getString(name, defaultValue.toString()));
  }

  public Long getLong(String name, Integer i) {
    return getLong(name, i.longValue());
  }

  public Long getLong(String name, Long defaultValue) {
    return Long.valueOf(getString(name, defaultValue.toString()));
  }

  public String getString(String name) {
    return getString(name, null);
  }

  public String getString(String name, String defaultValue) {
    String prefix = baseName + (baseName.isEmpty() ? "" : ".");
    String key = prefix + name;
    String envName = getEnvName(key);
    String envValue = getenv(envName);
    String downStreamValue = null;
    if (envValue != null) {
      downStreamValue = envValue;
    }
    String fetchedValue = fetch.apply(key);
    if (fetchedValue != null) {
      downStreamValue = fetchedValue;
    }
    if (downStreamValue == null) {
      downStreamValue = defaultValue;
    }
    return System.getProperty(key, downStreamValue);
  }
}
