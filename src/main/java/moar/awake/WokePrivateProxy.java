package moar.awake;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static java.util.Collections.unmodifiableMap;
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
  private static String ROW_INTERFACE_SUFFIX = "Row";

  private static String toDbName(String string, String q) {
    return q + UPPER_CAMEL.to(LOWER_UNDERSCORE, string) + q;
  }

  Map<String, Object> setMap = new ConcurrentHashMap<>();
  Map<String, Object> map = new ConcurrentHashMap<>();
  Class<?> clz;
  private String identifierQuoteString = "`";
  private String tableName;

  WokePrivateProxy(Class<?> clz) {
    this.clz = clz;
  }

  String fromDbName(String key) {
    try {
      StringBuilder s = new StringBuilder();
      boolean upper = true;
      char qChar = identifierQuoteString.charAt(0);
      for (char c : key.toCharArray()) {
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
      return getPropertyName(s.toString());
    } catch (RuntimeException e) {
      throw new RuntimeException(key, e);
    }
  }

  Map<String, Object> get() {
    Map<String, Object> dbMap = new ConcurrentHashMap<>();
    for (String key : map.keySet()) {
      String dbName = toDbName(key, getIdentifierQuoteString());
      dbMap.put(dbName, map.get(key));
    }
    return dbMap;
  }

  List<String> getColumns(boolean includeId) {
    List<String> columns = new ArrayList<>();
    for (Method method : clz.getMethods()) {
      String name = method.getName();
      if (isProperty(name)) {
        if (!getPropertyName(name).equals(getPropertyName("Id")) || includeId) {
          String dbName = toDbName(getPropertyName(name), getIdentifierQuoteString());
          if (!columns.contains(dbName)) {
            columns.add(dbName);
          }
        }
      }
    }
    Collections.sort(columns);
    return columns;
  }

  Object getDbValue(String column) {
    String propName = fromDbName(column);
    Object value = map.get(propName);
    if (value instanceof Date) {
      return new java.sql.Timestamp(((Date) value).getTime());
    }
    return value;
  }

  String getIdColumn() {
    String q = identifierQuoteString;
    return q + "id" + q;
  }

  String getIdentifierQuoteString() {
    return identifierQuoteString;
  }

  Object getIdValue() {
    return map.get(getPropertyName("Id"));
  }

  private Object getProperty(String name) {
    return map.get(getPropertyName(name));
  }

  private String getPropertyName(String name) {
    if (name.startsWith("get") || name.startsWith("set")) {
      name = name.substring(3);
    }
    return UPPER_CAMEL.to(LOWER_CAMEL, name);
  }

  String getTableName() {
    if (tableName == null) {
      String simpleName = clz.getSimpleName();
      if (simpleName.endsWith(ROW_INTERFACE_SUFFIX)) {
        simpleName = simpleName.substring(0, simpleName.length() - ROW_INTERFACE_SUFFIX.length());
      }
      tableName = toDbName(simpleName, identifierQuoteString);
    }
    return tableName;
  }

  Class<?> getTargetClass() {
    return clz;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String name = method.getName();
    if (args == null) {
      if (name.equals("privateProxy")) {
        return this;
      } else if (name.equals("toString")) {
        return toString();
      } else if (name.equals("hashCode")) {
        return hashCode();
      } else {
        if (isProperty(name)) {
          Class<?> returnType = method.getReturnType();
          Object value = getProperty(name);
          if (value == null) {
            return null;
          }
          if (value instanceof Number) {
            Number number = (Number) value;
            if (returnType == Double.class) {
              return number.doubleValue();
            }
            if (returnType == Long.class) {
              return number.longValue();
            }
            if (returnType == Integer.class) {
              return number.intValue();
            }
            if (returnType == Float.class) {
              return number.floatValue();
            }
            if (returnType == Short.class) {
              return number.shortValue();
            }
            if (returnType == Byte.class) {
              return number.byteValue();
            }
          }
          if (returnType.isAssignableFrom(value.getClass())) {
            if (value instanceof List) {
              if (name.endsWith("s")) {
                String itemClassName = name.substring(3, name.length() - 1);
                String fqn = clz.getPackage().getName() + "." + itemClassName;
                Class<?> clz = Class.forName(fqn);
                List<Map> list = (List) value;
                List<Object> result = new ArrayList<Object>();
                for (Map item : list) {
                  result.add(InterfaceUtil.use(clz).of(item));
                }
                return result;
              }
            }
            return value;
          }
          if (returnType.isInterface()) {
            return InterfaceUtil.use(returnType).of((Map<String, Object>) value);
          }
          throw new MoarException();
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
      } else if (name.equals("equals")) {
        return equals(args[0]);
      } else {
        if (isProperty(name)) {
          setProperty(name, args[0]);
          return null;
        }
      }
    }
    throw new MoarException(name, " is not supported by this proxy");
  }

  boolean isDbDirty(String column) {
    String propName = fromDbName(column);
    Object mapValue = map.get(propName);
    Object setValue = setMap.get(propName);
    if (setValue == mapValue) {
      return false;
    }
    if (mapValue != null) {
      return !mapValue.equals(setValue);
    }
    return true;
  }

  @Override
  public boolean isDirty() {
    for (String column : getColumns(true)) {
      if (isDbDirty(column)) {
        return true;
      }
    }
    return false;
  }

  private boolean isProperty(String name) {
    return name.startsWith("get") || name.startsWith("set");
  }

  void reset() {
    map.clear();
    for (String key : setMap.keySet()) {
      map.put(key, setMap.get(key));
    }
  }

  void set(Map<String, Object> dbMap) {
    setMap.clear();
    map.clear();
    for (String key : dbMap.keySet()) {
      String propName = fromDbName(key);
      Object dbValue = dbMap.get(key);
      if (dbValue == null) {
        setMap.remove(key);
      } else {
        setMap.put(propName, dbValue);
        map.put(propName, dbValue);
      }
    }
  }

  void setIdentifierQuoteString(String value) {
    identifierQuoteString = value;
  }

  private void setProperty(String name, Object arg) {
    if (arg == null) {
      map.remove(name);
    } else {
      map.put(getPropertyName(name), arg);
    }
  }

  void setTableName(String tableish) {
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
