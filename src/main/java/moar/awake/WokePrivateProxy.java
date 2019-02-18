package moar.awake;

import static java.util.Collections.unmodifiableMap;
import static moar.sugar.MoarStringUtil.toSnakeCase;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import moar.sugar.MoarException;

/**
 * Interface to expose private proxy methods.
 *
 * @author Mark Farnsworth
 */
class WokePrivateProxy
    implements
    WokeProxy,
    InvocationHandler {
  private static final String ROW_INTERFACE_SUFFIX = "Row";

  private static String toDbName(final String string, final String q) {
    return q + toSnakeCase(string) + q;
  }

  final Map<String, Object> setMap = new ConcurrentHashMap<>();
  final Map<String, Object> map = new ConcurrentHashMap<>();
  final Class<?> clz;
  private String identifierQuoteString = "";
  private String tableName;

  WokePrivateProxy(final Class<?> clz) {
    this.clz = clz;
  }

  String fromDbName(final String key) {
    try {
      final StringBuilder s = new StringBuilder();
      boolean upper = true;
      final char qChar = identifierQuoteString.charAt(0);
      for (final char c : key.toCharArray()) {
        if (c == qChar) {
          // ignore
        } else if (c != '_') {
          if (upper) {
            s.append(Character.toUpperCase(c));
            upper = false;
          } else {
            s.append(c);
          }
        }
        if (c == '_') {
          upper = true;
        }
      }
      return s.toString();
    } catch (final RuntimeException e) {
      throw new RuntimeException(key, e);
    }
  }

  Map<String, Object> get() {
    final Map<String, Object> dbMap = new ConcurrentHashMap<>();
    for (final String key : map.keySet()) {
      final String dbName = toDbName(key, getIdentifierQuoteString());
      dbMap.put(dbName, map.get(key));
    }
    return dbMap;
  }

  List<String> getColumns(final boolean includeId) {
    final List<String> columns = new ArrayList<>();
    for (final Method method : clz.getMethods()) {
      final String name = method.getName();
      if (isProperty(name)) {
        if (!getPropertyName(name).equals("Id") || includeId) {
          final String dbName
              = toDbName(getPropertyName(name), getIdentifierQuoteString());
          if (!columns.contains(dbName)) {
            columns.add(dbName);
          }
        }
      }
    }
    Collections.sort(columns);
    return columns;
  }

  Object getDbValue(final String column) {
    final String propName = fromDbName(column);
    final Object value = map.get(propName);
    if (value instanceof Date) {
      return new java.sql.Timestamp(((Date) value).getTime());
    }
    return value;
  }

  String getIdColumn() {
    final String q = identifierQuoteString;
    return q + "id" + q;
  }

  String getIdentifierQuoteString() {
    return identifierQuoteString;
  }

  Object getIdValue() {
    return map.get("Id");
  }

  private Object getProperty(final String name) {
    return map.get(getPropertyName(name));
  }

  private String getPropertyName(final String name) {
    return name.substring("get".length());
  }

  String getTableName() {
    if (tableName == null) {
      String simpleName = clz.getSimpleName();
      if (simpleName.endsWith(ROW_INTERFACE_SUFFIX)) {
        simpleName = simpleName.substring(0,
            simpleName.length() - ROW_INTERFACE_SUFFIX.length());
      }
      tableName = toDbName(simpleName, identifierQuoteString);
    }
    return tableName;
  }

  Class<?> getTargetClass() {
    return clz;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object invoke(final Object proxy, final Method method,
      final Object[] args) throws Throwable {
    final String name = method.getName();
    if (args == null) {
      if (name.equals("privateProxy")) {
        return this;
      } else if (name.equals("toString")) {
        return toString();
      } else {
        if (isProperty(name)) {
          final Object value = getProperty(name);
          if (value instanceof Number) {
            final Number number = (Number) value;
            final Class<?> returnType = method.getReturnType();
            if (returnType == Double.class) {
              return number.doubleValue();
            } else if (returnType == Long.class) {
              return number.longValue();
            } else if (returnType == Integer.class) {
              return number.intValue();
            } else if (returnType == Float.class) {
              return number.floatValue();
            } else if (returnType == Short.class) {
              return number.shortValue();
            } else if (returnType == Byte.class) {
              return number.byteValue();
            }
          }
          return value;
        }
      }
    } else if (args.length == 1) {
      if (name.startsWith("$")) {
        if (name.equals("$set")) {
          set((Map<String, Object>) args[0]);
          return null;
        } else if (name.equals("$setIdentifierQuoteString")) {
          setIdentifierQuoteString((String) args[0]);
          return null;
        }
      } else {
        if (isProperty(name)) {
          setProperty(name, args[0]);
          return null;
        }
      }
    }
    throw new MoarException(name, " is not supported by this proxy");
  }

  boolean isDbDirty(final String column) {
    final String propName = fromDbName(column);
    final Object mapValue = map.get(propName);
    final Object setValue = setMap.get(propName);
    if (setValue == mapValue) {
      return false;
    }
    if (mapValue != null) {
      return !mapValue.equals(setValue);
    } else {
      return true;
    }
  }

  @Override
  public boolean isDirty() {
    for (final String column : getColumns(true)) {
      if (isDbDirty(column)) {
        return true;
      }
    }
    return false;
  }

  private boolean isProperty(final String name) {
    // try to be fast with this check!
    // get or set!
    return name.startsWith("g")
        || name.startsWith("s") && name.substring(1).startsWith("et");
  }

  void reset() {
    map.clear();
    for (final String key : setMap.keySet()) {
      map.put(key, setMap.get(key));
    }
  }

  void set(final Map<String, Object> dbMap) {
    setMap.clear();
    map.clear();
    for (final String key : dbMap.keySet()) {
      final String propName = fromDbName(key);
      final Object dbValue = dbMap.get(key);
      setMap.put(propName, dbValue);
      map.put(propName, dbValue);
    }
  }

  void setIdentifierQuoteString(final String value) {
    identifierQuoteString = value;
  }

  private void setProperty(final String name, final Object arg) {
    if (arg == null) {
      map.remove(name);
    } else {
      map.put(getPropertyName(name), arg);
    }
  }

  void setTableName(final String tableish) {
    tableName = tableish;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Map toMap() {
    return unmodifiableMap(map);
  }

  @Override
  public String toString() {
    return map.toString();
  }

}
