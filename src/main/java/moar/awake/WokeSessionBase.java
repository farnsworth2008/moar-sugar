package moar.awake;

import static java.lang.String.format;
import static moar.awake.InterfaceUtil.asWokeProxy;
import static moar.awake.WokeRepository.buildSelect;
import static moar.awake.WokeRepository.create;
import static moar.awake.WokeRepository.expandColumnSplat;
import static moar.awake.WokeRepository.mapResultRow;
import static moar.sugar.Sugar.asRuntimeException;
import static moar.sugar.Sugar.closeQuietly;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.swallow;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class WokeSessionBase {

  private String buildSql(String finalTableish, Object[] woken) {
    boolean isCall = finalTableish.startsWith("call ") || finalTableish.startsWith("call\n");
    String sql;
    if (isCall) {
      sql = finalTableish;
    } else {
      sql = buildSelect(woken, 1);
      sql += "from " + expandColumnSplat(woken, finalTableish, 0) + "\n";
    }
    return sql;
  }

  public void delete(Object... rows) {
    if (rows == null) {
      return;
    }
    for (Object row : rows) {
      if (row != null) {
        WokePrivateProxy proxy = ((WokeProxiedObject) row).privateProxy();
        String sql = "delete from \n";
        sql += proxy.getTableName() + "\n";
        sql += "where\n";
        sql += proxy.getIdColumn() + "=?";
        String finalSql = sql;
        try (ConnectionHold c = reserve()) {
          require(() -> {
            try (PreparedStatement ps = c.get().prepareStatement(finalSql)) {
              ps.setObject(1, proxy.getIdValue());
              int result = ps.executeUpdate();
              require(1 == result || 0 == result);
            }
          });
        }
      }
    }
  }

  public void executeSql(String sql, Object... args) {
    try (ConnectionHold c = reserve()) {
      require(() -> {
        try (PreparedStatement ps = c.get().prepareStatement(sql)) {
          int i = 0;
          for (Object arg : args) {
            ps.setObject(++i, arg);
          }
          return ps.executeUpdate();
        }
      });
    }
  }

  @SuppressWarnings({ "rawtypes", "unchecked", "resource" })
  public WokeMappableResultSet iterator(String tableish, Class[] classes, Object... params) {
    boolean isSelect = tableish.startsWith("select ") || tableish.startsWith("select\n");
    if (isSelect) {
      tableish = format("(%s) tableish", tableish);
    }
    String finalTableish = tableish;
    AtomicReference<ConnectionHold> cn = new AtomicReference<>();
    AtomicReference<PreparedStatement> ps = new AtomicReference<>();
    AtomicReference<ResultSet> rs = new AtomicReference<>();
    cn.set(reserve());
    String q = cn.get().getIdentifierQuoteString();
    Object[] woken = new Object[classes.length];
    for (int i = 0; i < woken.length; i++) {
      woken[i] = create(classes[i]);
      asWokeProxy(woken[i]).setIdentifierQuoteString(q);
    }

    String sql = buildSql(finalTableish, woken);
    try {
      ps.set(cn.get().get().prepareStatement(sql));
      try {
        for (int i = 0; i < params.length; i++) {
          ps.get().setObject(i + 1, params[i]);
        }
      } catch (Throwable t) {
        swallow(() -> ps.get().close());
        throw asRuntimeException(t);
      }
    } catch (Throwable t) {
      swallow(() -> cn.get().close());
      throw asRuntimeException(t);
    }
    ResultSet resultSet = require(() -> ps.get().executeQuery());
    rs.set(resultSet);

    return new WokeMappableResultSet() {
      @Override
      public void close() throws Exception {
        closeQuietly(rs.get());
        closeQuietly(ps.get());
        closeQuietly(cn.get());
      }

      @Override
      public WokeMappableRow get() {
        Object[] rowObjects = new Object[classes.length];
        for (int i = 0; i < classes.length; i++) {
          Object rowObject = create(classes[i]);
          WokePrivateProxy wokenProxy = asWokeProxy(rowObject);
          wokenProxy.setIdentifierQuoteString(cn.get().getIdentifierQuoteString());
          Map<String, Object> map = wokenProxy.get();
          boolean hasId = rowObject instanceof WakeableRow.IdColumn;
          List<String> columns = wokenProxy.getColumns(!hasId);
          require(() -> {
            mapResultRow(hasId, map, columns, rs.get());
          });
          wokenProxy.set(map);
          rowObjects[i] = rowObject;
        }
        return new WokeMappableRow(rowObjects);
      }

      @Override
      public boolean next() {
        return require(() -> rs.get().next());
      }
    };
  }

  public abstract ConnectionHold reserve();

  /**
   * Reset an object to the state it had when it was loaded.
   *
   * @param objects
   *   Objects that need to be reset.
   */
  public void reset(Object... objects) {
    for (Object object : objects) {
      WokePrivateProxy proxy = ((WokeProxiedObject) object).privateProxy();
      proxy.reset();
    }
  }

  public void update(Object... rows) {
    for (Object row : rows) {
      WokePrivateProxy proxy = ((WokeProxiedObject) row).privateProxy();
      String sql = "update\n";
      sql += proxy.getTableName();
      sql += "\nset";
      AtomicInteger i = new AtomicInteger();
      List<Consumer<PreparedStatement>> setProps = new ArrayList<>();
      boolean hasId = row instanceof WakeableRow.IdColumn;
      boolean comma = false;
      for (String column : proxy.getColumns(!hasId)) {
        if (proxy.isDbDirty(column)) {
          if (comma) {
            sql += ", ";
          } else {
            comma = true;
          }
          sql += "\n" + column + "=?";
          setProps.add(ps -> require(() -> ps.setObject(i.incrementAndGet(), proxy.getDbValue(column))));
        }
      }
      sql += "\nwhere ";
      sql += proxy.getIdColumn() + "=?";
      Object idValue = proxy.getIdValue();
      setProps.add(ps -> require(() -> ps.setObject(i.incrementAndGet(), idValue)));
      String finalSql = sql;
      try (ConnectionHold c = reserve()) {
        require(() -> {
          try (PreparedStatement ps = c.get().prepareStatement(finalSql)) {
            for (Consumer<PreparedStatement> item : setProps) {
              item.accept(ps);
            }
            int result = ps.executeUpdate();
            require(1 == result);
          }
        });
      }
    }
  }

  public void update(String tableish, Object... rows) {
    for (Object row : rows) {
      WokePrivateProxy proxy = ((WokeProxiedObject) row).privateProxy();
      proxy.setTableName(tableish);
      update(row);
    }
  }
}