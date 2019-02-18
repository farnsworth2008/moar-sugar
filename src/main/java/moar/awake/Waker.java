package moar.awake;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.ThreadLocal.withInitial;
import static moar.sugar.Sugar.asRuntimeException;
import static moar.sugar.Sugar.closeQuietly;
import static moar.sugar.Sugar.nonNull;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.retryable;
import static moar.sugar.Sugar.swallow;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTransactionRollbackException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.sql.DataSource;
import moar.sugar.MoarException;
import moar.sugar.MoarLogger;
import moar.sugar.PropertyAccessor;
import moar.sugar.RetryableException;

/**
 * Top level for wake style data operations. This is where most of the *magic*
 * of the system is implemented.
 *
 * @author Mark Farnsworth
 * @param <Row>
 *   Row type
 */
public class Waker<Row>
    implements
    WokenWithSession<Row>,
    WokenWithoutSession<Row>,
    WokenWithRow<Row> {

  private static final PropertyAccessor props
      = new PropertyAccessor(Waker.class);
  private static final int TX_TRIES = props.getInteger("txTries", 3);
  private static final long TX_RETRY_TIME_DELAY
      = props.getLong("txRetryDelay", 1000);
  private static ThreadLocal<Boolean> inInsert = withInitial(() -> false);
  private static MoarLogger LOG = new MoarLogger(Waker.class);

  static <Row> WokePrivateProxy asWokeProxy(final Row row) {
    return ((WokeProxiedObject) row).privateProxy();
  }

  static String buildColumnsSql(final Object[] wokens, final int mode) {
    String columnsSql = "";
    for (int i = 0; i < wokens.length; i++) {
      if (i > 0) {
        columnsSql += ",\n";
      }
      columnsSql += " ";
      final Object woken = wokens[i];
      final WokePrivateProxy proxy = asWokeProxy(woken);
      String alias = proxy.getTargetClass().getSimpleName();
      final String q = proxy.getIdentifierQuoteString();
      alias = alias == null ? "" : alias + ".";
      final List<String> columns
          = proxy.getColumns(!(woken instanceof WakeableRow.IdColumn));
      for (final String column : columns) {
        final String columnAs
            = q + alias.replace('.', '_') + column.replace(q, "") + q;
        if (mode == 0) {
          columnsSql += alias + column + " " + columnAs;
        } else if (mode == 1) {
          columnsSql += columnAs;
        } else if (mode == 2) {
          columnsSql += column;
        } else {
          throw new MoarException("Mode not supported", mode);
        }
        columnsSql += ",\n";
      }
      if (woken instanceof WakeableRow.IdColumn) {
        final String column = "id";
        final String columnAs
            = q + alias.replace('.', '_') + column.replace(q, "") + q + "\n";
        if (mode == 0) {
          columnsSql += alias + column + " " + columnAs;
        } else if (mode == 1) {
          columnsSql += columnAs + "\n";
        } else if (mode == 2) {
          columnsSql += column + "\n";
        } else {
          throw new MoarException("Mode not supported", mode);
        }
      } else {
        if (mode == 0 || mode == 1) {
          columnsSql += "null " + alias.replace('.', '_') + "id" + "\n";
        } else if (mode == 2) {
          columnsSql += "null " + "id" + "\n";
        } else {
          throw new MoarException("Mode not supported", mode);
        }
      }
    }
    columnsSql += "\n";
    return columnsSql;
  }

  static String buildSelect(final Object[] woken, final int mode) {
    final String sql = "select\n" + buildColumnsSql(woken, mode);
    return sql;
  }

  static <Row> Row create(final Class<Row> clz) {
    final ClassLoader c = Waker.class.getClassLoader();
    final Class<?>[] cc = { clz, WokeProxiedObject.class };
    return (Row) Proxy.newProxyInstance(c, cc, new WokePrivateProxy(clz));
  }

  static String expandColumnSplat(final Object[] woken, final String tableish,
      final int mode) {
    int aliasSplatAreaStartPos = 0;
    int aliasSplatAreaEndPos = 0;
    int aliasSplatPos;
    while ((aliasSplatPos
        = tableish.indexOf(".[*]", aliasSplatAreaEndPos)) != -1) {
      final int aliasSplatStart = tableish.lastIndexOf(' ', aliasSplatPos) + 1;
      aliasSplatAreaStartPos = aliasSplatAreaStartPos == 0 ? aliasSplatStart
          : aliasSplatAreaStartPos;
      aliasSplatAreaEndPos
          = tableish.indexOf(".[*]", aliasSplatStart) + ".[*]".length();
    }

    final int splatPos = tableish.indexOf("[*]");
    if (aliasSplatAreaStartPos == 0 && splatPos != -1) {
      aliasSplatAreaStartPos = splatPos;
      aliasSplatAreaEndPos = splatPos + "[*]".length();
    }

    String expand = tableish.substring(0, aliasSplatAreaStartPos);
    expand += buildColumnsSql(woken, mode);
    expand += tableish.substring(aliasSplatAreaEndPos);
    return expand;
  }

  static void mapResultRow(final boolean hasId, final Map<String, Object> map,
      final List<String> columns, final ResultSet rs) throws SQLException {
    int col = 0;
    for (final String column : columns) {
      setColumnValue(map, column, rs.getObject(++col));
    }
    if (hasId) {
      setColumnValue(map, "id", rs.getObject(++col));
    }
  }

  public static void runWokeTransaction(final WokeSessionBase session,
      final Consumer<WokeTxSession> tx) {
    synchronized (session) {
      require(() -> {
        try (WokeTxSession txSession = new WokeTxSession(session.reserve())) {
          try {
            retryable(TX_TRIES, TX_RETRY_TIME_DELAY,
                () -> tx.accept(txSession));
          } catch (final Throwable t) {
            LOG.trace("runWokeTransaction rollback", t.getMessage(), t);
            txSession.rollback();
            throw asRuntimeException(t);
          }
        }
      });
    }
  }

  static void setColumnValue(final Map<String, Object> map, final String column,
      final Object value) {
    if (value == null) {
      map.remove(column);
    } else {
      map.put(column, value);
    }
  }

  public static <Row> WokenWithoutSession<Row> wake(final Class<Row> clz) {
    return new Waker<>(clz);
  }

  public static <Row> WokenWithoutSession<Row> wake(final Class<Row> clz,
      final String tableName) {
    return new Waker<>(clz, tableName);
  }

  public static WokeDataSourceSession wake(final DataSource ds) {
    return new WokeDataSourceSession(ds);
  }

  public static WokeProxy wake(final Object object) {
    return asWokeProxy(object);
  }

  public static <Row> List<Row> wake(final WokeResultSet<Row> iter) {
    try {
      return wake(iter, Integer.MAX_VALUE);
    } finally {
      require(() -> iter.close());
    }
  }

  public static <Row> List<Row> wake(final WokeResultSet<Row> iter,
      final int limit) {
    final List<Row> list = new ArrayList<>();
    while (iter.next()) {
      list.add(iter.get());
      if (list.size() >= limit) {
        return list;
      }
    }
    return list;
  }

  private final Class<Row> clz;
  private final String tableName;

  private WokeSessionBase session;

  private Consumer<Row> key = null;

  public Waker(final Class<Row> clz) {
    this(clz, null);
  }

  public Waker(final Class<Row> clz, final String tableName) {
    this.clz = clz;
    this.tableName = tableName;
  }

  private List<Row> consumeResultSet(final boolean hasId,
      final List<String> columns, final PreparedStatement ps,
      final String idQuote) throws SQLException {
    final List<Row> list = new ArrayList<>();
    try (ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        final Row row = create(clz);
        final WokePrivateProxy woke = asWokeProxy(row);
        woke.setIdentifierQuoteString(idQuote);
        final Map<String, Object> map = woke.get();
        mapResultRow(hasId, map, columns, rs);
        woke.set(map);
        list.add(row);
      }
    }
    return list;
  }

  @Override
  public Row define() {
    return define(r -> {});
  }

  @Override
  public Row define(final Consumer<Row> r) {
    final Row row = create(clz);
    r.accept(row);
    return row;
  }

  @Override
  public void delete(Row row) {
    session.delete(row);
  }

  private void doInsertRowWithConnection(final Row row, final boolean isUpsert,
      final int[] identityColumn, final ConnectionHold hold)
      throws SQLException {
    final boolean hasId = row instanceof WakeableRow.IdColumn;
    final WokePrivateProxy woke = asWokeProxy(row);
    woke.setIdentifierQuoteString(hold.getIdentifierQuoteString());
    final List<String> columns = woke.getColumns(!hasId);
    final Map<String, Object> map = woke.get();
    final String q = hold.getIdentifierQuoteString();
    final String idColumn = q + "id" + q;
    final String table = nonNull(this.tableName, woke.getTableName());
    String sql = "insert into \n" + table + " (\n";
    if (hasId) {
      sql += q + "id" + q + "\n,";
    }
    sql += join("\n,", columns);
    sql += ") values (\n";
    if (hasId) {
      sql += "?,\n";
    }
    sql += "?\n";
    for (int i = 1; i < columns.size(); i++) {
      sql += "\n,?";
    }
    sql += "\n)\n";
    if (isUpsert) {
      sql += " on duplicate key update\n";
      boolean commaNeeded;
      if (hasId) {
        sql += q + "id" + q + "=last_insert_id(" + q + "id" + q + ") ";
        commaNeeded = true;
      } else {
        commaNeeded = false;
      }
      for (int i = 0; i < columns.size(); i++) {
        if (commaNeeded) {
          sql += "\n, ";
        }
        sql += columns.get(i) + "=?";
        commaNeeded = true;
      }
    }
    final Connection cn = hold.get();
    final boolean useGeneratedKeys = isUpsert && hasId;
    try (PreparedStatement ps
        = useGeneratedKeys ? cn.prepareStatement(sql, identityColumn)
            : cn.prepareStatement(sql)) {
      int p = 0;
      if (hasId) {
        ps.setObject(++p, map.get(idColumn));
      }
      for (int i = 0; i < columns.size(); i++) {
        ps.setObject(++p, woke.getDbValue(columns.get(i)));
      }
      if (isUpsert) {
        for (int i = 0; i < columns.size(); i++) {
          ps.setObject(++p, woke.getDbValue(columns.get(i)));
        }
      }
      try {
        final int upResult = ps.executeUpdate();
        swallow(() -> require("0 || 1 || 2, " + upResult,
            upResult == 0 || upResult == 1 || upResult == 2));
        if (useGeneratedKeys) {
          try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
              final Object id = rs.getObject(1);
              map.put(idColumn, id);
              woke.set(map);
            }
          }
        }
      } catch (final SQLSyntaxErrorException e) {
        throw new MoarException("bad sql syntax on upsert", e.getMessage(),
            stripTicks(q, sql));
      } catch (final SQLTransactionRollbackException e) {
        throw new RetryableException(e);
      } catch (final Throwable e) {
        throw new MoarException("upsert failed", e.getMessage(),
            stripTicks(q, sql));
      }
    }
  }

  private WokeResultSet<Row> doIterator(String tableish, final Object... params)
      throws SQLException {
    final Row woken = create(clz);
    final boolean isCall
        = tableish.startsWith("call ") || tableish.startsWith("call\n");
    final boolean isSelect
        = tableish.startsWith("select ") || tableish.startsWith("select\n");
    final String simpleName
        = asWokeProxy(woken).getTargetClass().getSimpleName();
    if (isSelect) {
      tableish = format("(%s) %s", tableish, simpleName);
    } else if (!isCall) {
      tableish = format("(select [*] from %s) %s ", tableish, simpleName);
    }
    final AtomicReference<ConnectionHold> cn = new AtomicReference<>();
    final AtomicReference<PreparedStatement> ps = new AtomicReference<>();
    final AtomicReference<ResultSet> rs = new AtomicReference<>();
    cn.set(session.reserve());
    final String q = cn.get().getIdentifierQuoteString();
    asWokeProxy(woken).setIdentifierQuoteString(q);
    String sql;
    if (isCall) {
      sql = tableish;
    } else {
      sql = buildSelect(new Object[] { woken }, 1);
      sql += "from " + expandColumnSplat(new Object[] { woken }, tableish, 0);
    }
    try {
      ps.set(cn.get().get().prepareStatement(sql));
      try {
        for (int i = 0; i < params.length; i++) {
          ps.get().setObject(i + 1, params[i]);
        }
      } catch (final Throwable t) {
        ps.get().close();
        throw asRuntimeException(t);
      }
    } catch (final Throwable t) {
      LOG.warn(sql);
      cn.get().close();
      throw asRuntimeException(t);
    }
    rs.set(require(() -> ps.get().executeQuery()));

    return new WokeResultSet<Row>() {
      @Override
      public void close() throws Exception {
        closeQuietly(rs.get());
        closeQuietly(ps.get());
        closeQuietly(cn.get());
      }

      @Override
      public Row get() {
        final Row row = create(clz);
        final boolean hasId = row instanceof WakeableRow.IdColumn;
        final WokePrivateProxy wokenProxy = asWokeProxy(row);
        wokenProxy
            .setIdentifierQuoteString(cn.get().getIdentifierQuoteString());
        final Map<String, Object> map = wokenProxy.get();
        final List<String> columns = wokenProxy.getColumns(!hasId);
        require(() -> {
          mapResultRow(hasId, map, columns, rs.get());
        });
        wokenProxy.set(map);
        return row;
      }

      @Override
      public boolean next() {
        return require(() -> rs.get().next());
      }
    };
  }

  private List<Row> doSessionFind() throws SQLException {
    LOG.trace("sessionFind");
    try {
      return doSessionFindOp();
    } finally {
      LOG.trace("out sessionFind");
    }
  }

  private List<Row> doSessionFindOp() {
    final Row keyRow = create(clz);
    asWokeProxy(keyRow);
    key.accept(keyRow);
    return doTableFind(keyRow);
  }

  private void doSessionInsertOp(final Row row, final Consumer<Row> updator,
      final boolean isUpsert) {
    asWokeProxy(row);
    if (key != null) {
      key.accept(row);
    }
    updator.accept(row);
    doTableInsert(row, isUpsert);
  }

  private Row doSessionInsertRow(final Row row, final Consumer<Row> updator,
      final boolean isUpsert) {
    try {
      if (inInsert.get()) {
        throw new MoarException("reentry");
      }
      inInsert.set(true);
      try {
        return enterSessionInsertRow(row, updator, isUpsert);
      } finally {
        inInsert.set(false);
      }
    } finally {
      key = r -> {};
    }
  }

  private List<Row> doTableFind(final Row row) {
    return require(() -> doTableFindSql(row));
  }

  private List<Row> doTableFindSql(final Row row) throws SQLException {
    try (ConnectionHold cn = session.reserve()) {
      final boolean hasId = row instanceof WakeableRow.IdColumn;
      final WokePrivateProxy woke = asWokeProxy(row);
      woke.setIdentifierQuoteString(cn.getIdentifierQuoteString());
      final String table = nonNull(tableName, woke.getTableName());
      final Map<String, Object> map = woke.get();
      final List<String> columns = woke.getColumns(!hasId);
      String sql = buildSelect(new Object[] { row }, 2);
      sql += "from " + table + " ";
      sql += "where ";
      int i = 0;
      for (final String key : map.keySet()) {
        if (i++ != 0) {
          sql += " and ";
        }
        sql += key + " = ?";
      }
      try {
        try (PreparedStatement ps = cn.get().prepareStatement(sql)) {
          setupStatement(map, map.keySet(), ps);
          return consumeResultSet(hasId, columns, ps,
              woke.getIdentifierQuoteString());
        }
      } catch (final SQLException e) {
        LOG.warn(e.getMessage(), sql, e);
        throw asRuntimeException(e);
      }
    }
  }

  private void doTableInsert(final Row row, final boolean isUpsert) {
    require(() -> doTableInsertSql(row, isUpsert));
  }

  private void doTableInsertSql(final Row row, final boolean isUpsert)
      throws SQLException {
    final int[] identityColumn = { 1 };
    try (ConnectionHold hold = session.reserve()) {
      doInsertRowWithConnection(row, isUpsert, identityColumn, hold);
    }
  }

  private Row enterSessionInsertRow(final Row row, final Consumer<Row> updator,
      final boolean isUpsert) {
    LOG.trace("sessionUpsert");
    try {
      synchronized (session) {
        doSessionInsertOp(row, updator, isUpsert);
      }
    } finally {
      LOG.trace("out sessionUpsert");
    }
    return row;
  }

  @Override
  public Row find() {
    return require(() -> {
      final List<Row> list = doSessionFind();
      return list.isEmpty() ? null : list.get(0);
    });
  }

  @Override
  public String getTableName() {
    return asWokeProxy(create(clz)).getTableName();
  }

  @Override
  public WokenWithRow<Row> id(final Long id) {
    return this.key(r -> {
      ((WakeableRow.IdColumnAsLong) r).setId(id);
    });
  }

  @Override
  public WokenWithRow<Row> id(final String id) {
    return this.key(r -> {
      ((WakeableRow.IdColumnAsString) r).setId(id);
    });
  }

  @Override
  public WokenWithRow<Row> id(final UUID id) {
    return this.key(r -> {
      ((WakeableRow.IdColumnAsUUID) r).setId(id);
    });
  }

  @Override
  public Row insert() {
    return insert(r -> {});
  }

  @Override
  public Row insert(final Consumer<Row> updator) {
    return doSessionInsertRow(create(clz), updator, false);
  }

  @Override
  public Row insert(final Row row) {
    key = r -> {};
    return doSessionInsertRow(row, r -> {}, false);
  }

  @Override
  public WokeResultSet<Row> iterator(final String tableish,
      final Object... params) {
    return require(() -> doIterator(tableish, params));
  }

  @Override
  public WokenWithRow<Row> key(final Consumer<Row> r) {
    this.key = r;
    return this;
  }

  @Override
  public List<Row> list() {
    return require(() -> doSessionFind());
  }

  @Override
  public List<Row> list(final String tableish, final Object... params) {
    return wake(iterator(tableish, params));
  }

  @Override
  public WokenWithSession<Row> of(final DataSource ds) {
    return of(new WokeDataSourceSession(ds));
  }

  @Override
  public WokenWithSession<Row> of(final WokeSessionBase cu) {
    this.session = cu;
    return this;
  }

  private void setupStatement(final Map<String, Object> map,
      final Set<String> columns, final PreparedStatement ps)
      throws SQLException {
    int i = 0;
    for (final String column : columns) {
      ps.setObject(++i, map.get(column));
    }
  }

  private String stripTicks(final String quote, final String string) {
    return string.replaceAll(quote, "");
  }

  @Override
  public void update(Row row) {
    session.update(row);
  }

  @Override
  public Row upsert() {
    return upsert(r -> {});
  }

  @Override
  public Row upsert(final Consumer<Row> updator) {
    return doSessionInsertRow(create(clz), updator, true);
  }

  @Override
  public Row upsert(final Row row) {
    key = r -> {};
    return doSessionInsertRow(row, r -> {}, true);
  }
}
