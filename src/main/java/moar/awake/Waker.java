package moar.awake;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.ThreadLocal.withInitial;
import static moar.sugar.Sugar.asRuntimeException;
import static moar.sugar.Sugar.closeQuietly;
import static moar.sugar.Sugar.nonNull;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.retry;
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

  private static PropertyAccessor props = new PropertyAccessor(Waker.class);
  private static int TX_TRIES = props.getInteger("txTries", 3);
  private static long TX_RETRY_TIME_DELAY = props.getLong("txRetryDelay", 1000);
  private static ThreadLocal<Boolean> inInsert = withInitial(() -> false);
  private static MoarLogger LOG = new MoarLogger(Waker.class);

  static <Row> WokePrivateProxy asWokeProxy(Row row) {
    return ((WokeProxiedObject) row).privateProxy();
  }

  static String buildColumnsSql(Object[] wokens, int mode) {
    String columnsSql = "";
    for (int i = 0; i < wokens.length; i++) {
      if (i > 0) {
        columnsSql += ",\n";
      }
      columnsSql += " ";
      Object woken = wokens[i];
      WokePrivateProxy proxy = asWokeProxy(woken);
      String alias = proxy.getTargetClass().getSimpleName();
      String q = proxy.getIdentifierQuoteString();
      alias = alias == null ? "" : alias + ".";
      List<String> columns = proxy.getColumns(!(woken instanceof WakeableRow.IdColumn));
      for (String column : columns) {
        String columnAs = q + alias.replace('.', '_') + column.replace(q, "") + q;
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
        String column = "id";
        String columnAs = q + alias.replace('.', '_') + column.replace(q, "") + q + "\n";
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

  static String buildSelect(Object[] woken, int mode) {
    String sql = "select\n" + buildColumnsSql(woken, mode);
    return sql;
  }

  @SuppressWarnings("unchecked")
  static <Row> Row create(Class<Row> clz) {
    ClassLoader c = Waker.class.getClassLoader();
    Class<?>[] cc = { clz, WokeProxiedObject.class };
    return (Row) Proxy.newProxyInstance(c, cc, new WokePrivateProxy(clz));
  }

  static String expandColumnSplat(Object[] woken, String tableish, int mode) {
    int aliasSplatAreaStartPos = 0;
    int aliasSplatAreaEndPos = 0;
    int aliasSplatPos;
    while ((aliasSplatPos = tableish.indexOf(".[*]", aliasSplatAreaEndPos)) != -1) {
      int aliasSplatStart = tableish.lastIndexOf(' ', aliasSplatPos) + 1;
      aliasSplatAreaStartPos = aliasSplatAreaStartPos == 0 ? aliasSplatStart : aliasSplatAreaStartPos;
      aliasSplatAreaEndPos = tableish.indexOf(".[*]", aliasSplatStart) + ".[*]".length();
    }

    int splatPos = tableish.indexOf("[*]");
    if (aliasSplatAreaStartPos == 0 && splatPos != -1) {
      aliasSplatAreaStartPos = splatPos;
      aliasSplatAreaEndPos = splatPos + "[*]".length();
    }

    String expand = tableish.substring(0, aliasSplatAreaStartPos);
    expand += buildColumnsSql(woken, mode);
    expand += tableish.substring(aliasSplatAreaEndPos);
    return expand;
  }

  static void mapResultRow(boolean hasId, Map<String, Object> map, List<String> columns, ResultSet rs)
      throws SQLException {
    int col = 0;
    for (String column : columns) {
      setColumnValue(map, column, rs.getObject(++col));
    }
    if (hasId) {
      setColumnValue(map, "id", rs.getObject(++col));
    }
  }

  /**
   * Run a transaction with retry, rollback on exceptions.
   *
   * @param session
   * @param tx
   */
  public static void runWokeTransaction(WokeSessionBase session, Consumer<WokeTxSession> tx) {
    synchronized (session) {
      require(() -> {
        try (WokeTxSession txSession = new WokeTxSession(session.reserve())) {
          try {
            retry(TX_TRIES, TX_RETRY_TIME_DELAY, () -> tx.accept(txSession));
          } catch (Throwable t) {
            LOG.trace("runWokeTransaction rollback", t.getMessage(), t);
            txSession.rollback();
            throw asRuntimeException(t);
          }
        }
      });
    }
  }

  static void setColumnValue(Map<String, Object> map, String column, Object value) {
    if (value == null) {
      map.remove(column);
    } else {
      map.put(column, value);
    }
  }

  /**
   * @param clz
   * @return Waker configured for the class.
   */
  public static <Row> WokenWithoutSession<Row> wake(Class<Row> clz) {
    return new Waker<>(clz);
  }

  public static <Row> WokenWithoutSession<Row> wake(Class<Row> clz, String tableName) {
    return new Waker<>(clz, tableName);
  }

  public static WokeDataSourceSession wake(DataSource ds) {
    return new WokeDataSourceSession(ds);
  }

  public static WokeProxy wake(Object object) {
    return asWokeProxy(object);
  }

  public static <Row> List<Row> wake(WokeResultSet<Row> iter) {
    try {
      return wake(iter, Integer.MAX_VALUE);
    } finally {
      require(() -> iter.close());
    }
  }

  public static <Row> List<Row> wake(WokeResultSet<Row> iter, int limit) {
    List<Row> list = new ArrayList<>();
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

  private final ThreadLocal<WokeSessionBase> session = new ThreadLocal<>();

  private final ThreadLocal<Consumer<Row>> key = new ThreadLocal<>();

  public Waker(Class<Row> clz) {
    this(clz, null);
  }

  public Waker(Class<Row> clz, String tableName) {
    this.clz = clz;
    this.tableName = tableName;
  }

  private List<Row> consumeResultSet(boolean hasId, List<String> columns, PreparedStatement ps, String idQuote)
      throws SQLException {
    List<Row> list = new ArrayList<>();
    try (ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        Row row = create(clz);
        WokePrivateProxy woke = asWokeProxy(row);
        woke.setIdentifierQuoteString(idQuote);
        Map<String, Object> map = woke.get();
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
  public Row define(Consumer<Row> r) {
    Row row = create(clz);
    r.accept(row);
    return row;
  }

  @Override
  public void delete(Row row) {
    session.get().delete(row);
  }

  private void doInsertRowWithConnection(Row row, boolean isUpsert, int[] identityColumn, ConnectionHold hold)
      throws SQLException {
    boolean hasId = row instanceof WakeableRow.IdColumn;
    WokePrivateProxy woke = asWokeProxy(row);
    woke.setIdentifierQuoteString(hold.getIdentifierQuoteString());
    List<String> columns = woke.getColumns(!hasId);
    Map<String, Object> map = woke.get();
    String q = hold.getIdentifierQuoteString();
    String idColumn = q + "id" + q;
    String table = nonNull(this.tableName, woke.getTableName());
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
    boolean useGeneratedKeys = isUpsert && hasId;

    @SuppressWarnings("resource")
    Connection cn = hold.get();
    try (
        PreparedStatement ps = useGeneratedKeys ? cn.prepareStatement(sql, identityColumn) : cn.prepareStatement(sql)) {
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
        int upResult = ps.executeUpdate();
        swallow(() -> require("0 || 1 || 2, " + upResult, upResult == 0 || upResult == 1 || upResult == 2));
        if (useGeneratedKeys) {
          try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
              Object id = rs.getObject(1);
              map.put(idColumn, id);
              woke.set(map);
            }
          }
        }
      } catch (SQLSyntaxErrorException e) {
        throw new MoarException("bad sql syntax on upsert", e.getMessage(), stripTicks(q, sql));
      } catch (SQLTransactionRollbackException e) {
        throw new RetryableException(e);
      } catch (Throwable e) {
        throw new MoarException("upsert failed", e.getMessage(), stripTicks(q, sql));
      }
    }
  }

  private WokeResultSet<Row> doIterator(String tableish, Object... params) {
    Row woken = create(clz);
    boolean isCall = tableish.startsWith("call ") || tableish.startsWith("call\n");
    boolean isSelect = tableish.startsWith("select ") || tableish.startsWith("select\n");
    String simpleName = asWokeProxy(woken).getTargetClass().getSimpleName();
    if (isSelect) {
      tableish = format("(%s) %s", tableish, simpleName);
    } else if (!isCall) {
      if (tableish.toLowerCase().startsWith("where ")) {
        tableish = getTableName() + " " + this.clz.getSimpleName() + " " + tableish;
      }
      tableish = format("(select [*] from %s) %s ", tableish, simpleName);
    }
    AtomicReference<ConnectionHold> cn = new AtomicReference<>();
    AtomicReference<PreparedStatement> ps = new AtomicReference<>();
    AtomicReference<ResultSet> rs = new AtomicReference<>();
    cn.set(session.get().reserve());
    String q = cn.get().getIdentifierQuoteString();
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
      } catch (Throwable t) {
        ps.get().close();
        throw asRuntimeException(t);
      }
    } catch (Throwable t) {
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
        Row row = create(clz);
        boolean hasId = row instanceof WakeableRow.IdColumn;
        WokePrivateProxy wokenProxy = asWokeProxy(row);
        wokenProxy.setIdentifierQuoteString(cn.get().getIdentifierQuoteString());
        Map<String, Object> map = wokenProxy.get();
        List<String> columns = wokenProxy.getColumns(!hasId);
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

  private List<Row> doSessionFind() {
    LOG.trace("sessionFind");
    try {
      return doSessionFindOp();
    } finally {
      LOG.trace("out sessionFind");
    }
  }

  private List<Row> doSessionFindOp() {
    Row keyRow = create(clz);
    asWokeProxy(keyRow);
    key.get().accept(keyRow);
    return doTableFind(keyRow);
  }

  private void doSessionInsertOp(Row row, Consumer<Row> updator, boolean isUpsert) {
    asWokeProxy(row);
    if (key.get() != null) {
      key.get().accept(row);
    }
    updator.accept(row);
    doTableInsert(row, isUpsert);
  }

  private Row doSessionInsertRow(Row row, Consumer<Row> updator, boolean isUpsert) {
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
      key.set(r -> {});
    }
  }

  private List<Row> doTableFind(Row row) {
    return require(() -> doTableFindSql(row));
  }

  private List<Row> doTableFindSql(Row row) {
    try (ConnectionHold cn = session.get().reserve()) {
      boolean hasId = row instanceof WakeableRow.IdColumn;
      WokePrivateProxy woke = asWokeProxy(row);
      woke.setIdentifierQuoteString(cn.getIdentifierQuoteString());
      String table = nonNull(tableName, woke.getTableName());
      Map<String, Object> map = woke.get();
      List<String> columns = woke.getColumns(!hasId);
      String sql = buildSelect(new Object[] { row }, 2);
      sql += "from " + table + " ";
      sql += "where ";
      int i = 0;
      for (String key : map.keySet()) {
        if (i++ != 0) {
          sql += " and ";
        }
        sql += key + " = ?";
      }
      try {
        try (PreparedStatement ps = cn.get().prepareStatement(sql)) {
          setupStatement(map, map.keySet(), ps);
          return consumeResultSet(hasId, columns, ps, woke.getIdentifierQuoteString());
        }
      } catch (SQLException e) {
        LOG.warn(e.getMessage(), sql, e);
        throw asRuntimeException(e);
      }
    }
  }

  private void doTableInsert(Row row, boolean isUpsert) {
    require(() -> doTableInsertSql(row, isUpsert));
  }

  private void doTableInsertSql(Row row, boolean isUpsert) throws SQLException {
    int[] identityColumn = { 1 };
    try (ConnectionHold hold = session.get().reserve()) {
      doInsertRowWithConnection(row, isUpsert, identityColumn, hold);
    }
  }

  private Row enterSessionInsertRow(Row row, Consumer<Row> updator, boolean isUpsert) {
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
      List<Row> list = doSessionFind();
      return list.isEmpty() ? null : list.get(0);
    });
  }

  @Override
  public String getTableName() {
    return asWokeProxy(create(clz)).getTableName();
  }

  @Override
  public WokenWithRow<Row> id(Long id) {
    return this.key(r -> {
      ((WakeableRow.IdColumnAsLong) r).setId(id);
    });
  }

  @Override
  public WokenWithRow<Row> id(String id) {
    return this.key(r -> {
      ((WakeableRow.IdColumnAsString) r).setId(id);
    });
  }

  @Override
  public WokenWithRow<Row> id(UUID id) {
    return this.key(r -> {
      ((WakeableRow.IdColumnAsUUID) r).setId(id);
    });
  }

  @Override
  public Row insert() {
    return insert(r -> {});
  }

  @Override
  public Row insert(Consumer<Row> updator) {
    return doSessionInsertRow(create(clz), updator, false);
  }

  @Override
  public Row insert(Row row) {
    key.set(r -> {});
    return doSessionInsertRow(row, r -> {}, false);
  }

  @Override
  public WokeResultSet<Row> iterator(String tableish, Object... params) {
    return require(() -> doIterator(tableish, params));
  }

  @Override
  public WokenWithRow<Row> key(Consumer<Row> r) {
    this.key.set(r);
    return this;
  }

  @Override
  public List<Row> list() {
    return require(() -> doSessionFind());
  }

  @Override
  public List<Row> list(String tableish, Object... params) {
    return wake(iterator(tableish, params));
  }

  @Override
  public WokenWithSession<Row> of(DataSource ds) {
    return of(new WokeDataSourceSession(ds));
  }

  @Override
  public WokenWithSession<Row> of(WokeSessionBase session) {
    this.session.set(session);
    return this;
  }

  private void setupStatement(Map<String, Object> map, Set<String> columns, PreparedStatement ps) throws SQLException {
    int i = 0;
    for (String column : columns) {
      ps.setObject(++i, map.get(column));
    }
  }

  private String stripTicks(String quote, String string) {
    return string.replaceAll(quote, "");
  }

  @Override
  public void update(Row row) {
    session.get().update(row);
  }

  @Override
  public Row upsert() {
    return upsert(r -> {});
  }

  @Override
  public Row upsert(Consumer<Row> updator) {
    return doSessionInsertRow(create(clz), updator, true);
  }

  @Override
  public Row upsert(Row row) {
    key.set(r -> {});
    return doSessionInsertRow(row, r -> {}, true);
  }
}
