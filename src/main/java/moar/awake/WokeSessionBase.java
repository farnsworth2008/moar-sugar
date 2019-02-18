package moar.awake;

import static java.lang.String.format;
import static moar.awake.Waker.asWokeProxy;
import static moar.awake.Waker.buildSelect;
import static moar.awake.Waker.create;
import static moar.awake.Waker.expandColumnSplat;
import static moar.awake.Waker.mapResultRow;
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

  private String buildSql(final String finalTableish, final Object[] woken) {
    final boolean isCall = finalTableish.startsWith("call ") || finalTableish.startsWith("call\n");
    String sql;
    if (isCall) {
      sql = finalTableish;
    } else {
      sql = buildSelect(woken, 1);
      sql += "from " + expandColumnSplat(woken, finalTableish, 0) + "\n";
    }
    return sql;
  }

  public void delete(final Object row) {
    if (row == null) {
      return;
    }
    final WokePrivateProxy proxy = ((WokeProxiedObject) row).privateProxy();
    String sql = "delete from \n";
    sql += proxy.getTableName() + "\n";
    sql += "where\n";
    sql += proxy.getIdColumn() + "=?";
    final String finalSql = sql;
    try (ConnectionHold c = reserve()) {
      require(() -> {
        try (PreparedStatement ps = c.get().prepareStatement(finalSql)) {
          ps.setObject(1, proxy.getIdValue());
          final int result = ps.executeUpdate();
          require("1 || 0, " + result, 1 == result || 0 == result);
        }
      });
    }
  }

  public void executeSql(final String sql, final Object... args) {
    try (ConnectionHold c = reserve()) {
      require(() -> {
        try (PreparedStatement ps = c.get().prepareStatement(sql)) {
          int i = 0;
          for (final Object arg : args) {
            ps.setObject(++i, arg);
          }
          return ps.executeUpdate();
        }
      });
    }
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  public WokeMappableResultSet iterator(String tableish, final Class[] classes, final Object... params) {
    final boolean isSelect = tableish.startsWith("select ") || tableish.startsWith("select\n");
    if (isSelect) {
      tableish = format("(%s) tableish", tableish);
    }
    final String finalTableish = tableish;
    final AtomicReference<ConnectionHold> cn = new AtomicReference<>();
    final AtomicReference<PreparedStatement> ps = new AtomicReference<>();
    final AtomicReference<ResultSet> rs = new AtomicReference<>();
    cn.set(reserve());
    final String q = cn.get().getIdentifierQuoteString();
    final Object[] woken = new Object[classes.length];
    for (int i = 0; i < woken.length; i++) {
      woken[i] = create(classes[i]);
      asWokeProxy(woken[i]).setIdentifierQuoteString(q);
    }

    final String sql = buildSql(finalTableish, woken);
    try {
      ps.set(cn.get().get().prepareStatement(sql));
      try {
        for (int i = 0; i < params.length; i++) {
          ps.get().setObject(i + 1, params[i]);
        }
      } catch (final Throwable t) {
        swallow(() -> ps.get().close());
        throw asRuntimeException(t);
      }
    } catch (final Throwable t) {
      swallow(() -> cn.get().close());
      throw asRuntimeException(t);
    }
    final ResultSet resultSet = require(() -> ps.get().executeQuery());
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
        final Object[] rowObjects = new Object[classes.length];
        for (int i = 0; i < classes.length; i++) {
          final Object rowObject = create(classes[i]);
          final WokePrivateProxy wokenProxy = asWokeProxy(rowObject);
          wokenProxy.setIdentifierQuoteString(cn.get().getIdentifierQuoteString());
          final Map<String, Object> map = wokenProxy.get();
          final boolean hasId = rowObject instanceof WakeableRow.IdColumn;
          final List<String> columns = wokenProxy.getColumns(!hasId);
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
   */
  public void reset(final Object woke) {
    final WokePrivateProxy proxy = ((WokeProxiedObject) woke).privateProxy();
    proxy.reset();
  }

  public final void update(final Object row) {
    final WokePrivateProxy proxy = ((WokeProxiedObject) row).privateProxy();
    String sql = "update\n";
    sql += proxy.getTableName() + "\n";
    sql += "set\n";
    final AtomicInteger i = new AtomicInteger();
    final List<Consumer<PreparedStatement>> setProps = new ArrayList<>();
    final boolean hasId = row instanceof WakeableRow.IdColumn;
    for (final String column : proxy.getColumns(!hasId)) {
      if (proxy.isDbDirty(column)) {
        sql += column + "=?\n";
        setProps.add(ps -> require(() -> ps.setObject(i.incrementAndGet(), proxy.getDbValue(column))));
      }
    }
    sql += "where\n";
    sql += proxy.getIdColumn() + "=?";
    final Object idValue = proxy.getIdValue();
    setProps.add(ps -> require(() -> ps.setObject(i.incrementAndGet(), idValue)));
    final String finalSql = sql;
    try (ConnectionHold c = reserve()) {
      require(() -> {
        try (PreparedStatement ps = c.get().prepareStatement(finalSql)) {
          for (final Consumer<PreparedStatement> item : setProps) {
            item.accept(ps);
          }
          final int result = ps.executeUpdate();
          require("1, " + result, 1 == result);
        }
      });
    }
  }

  public final void update(final String tableish, final Object woke) {
    final WokePrivateProxy proxy = ((WokeProxiedObject) woke).privateProxy();
    proxy.setTableName(tableish);
    update(woke);
  }

}