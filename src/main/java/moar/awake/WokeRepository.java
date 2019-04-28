package moar.awake;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.ThreadLocal.withInitial;
import static moar.awake.InterfaceUtil.asWokeProxy;
import static moar.awake.InterfaceUtil.use;
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
public class WokeRepository<Row>
    implements
    WokenRepository<Row>,
    WokenWithoutSession<Row>,
    WokenWithRow<Row> {

  private static PropertyAccessor props = new PropertyAccessor(WokeRepository.class);
  private static int TX_TRIES = props.getInteger("txTries", 3);
  private static long TX_RETRY_TIME_DELAY = props.getLong("txRetryDelay", 1000);
  private static ThreadLocal<Boolean> inInsert = withInitial(() -> false);
  private static MoarLogger LOG = new MoarLogger(WokeRepository.class);

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
    ClassLoader c = WokeRepository.class.getClassLoader();
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
  static void runWokeTransaction(WokeSessionBase session, Consumer<WokeTxSession> tx) {
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

  private final Class<Row> clz;
  private final String tableName;

  private WokeSessionBase session;

  private final ThreadLocal<Consumer<Row>> key = new ThreadLocal<>();

  public WokeRepository(Class<Row> clz) {
    this(clz, null);
  }

  public WokeRepository(Class<Row> clz, String tableName) {
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
  public void delete() {
    //TODO: delete via SQL.
    List<Row> list = list();
    for (Row row : list) {
      delete(row);
    }
  }

  @Override
  public void delete(Row row) {
    session.delete(row);
  }

  @Override
  public void delete(String where, Object... params) {
    require(() -> {
      /* TODO Implement delete without using iterator. */
      try (WokeResultSet<Row> resultSet = iterator(where, params)) {
        while (resultSet.next()) {
          delete(resultSet.get());
        }
      }
    });
  }

  private synchronized Row doInsert(Row row) {
    key.set(r -> {});
    return doSessionInsertRow(row, r -> {}, false);
  }

  private synchronized void doInsertBatch(List<Row> rows) throws SQLException {
    try (ConnectionHold hold = session.reserve()) {
      Row row0 = rows.get(0);
      boolean hasId = row0 instanceof WakeableRow.IdColumn;
      WokePrivateProxy woke = asWokeProxy(row0);
      woke.setIdentifierQuoteString(hold.getIdentifierQuoteString());
      List<String> columns = woke.getColumns(!hasId);
      String q = hold.getIdentifierQuoteString();
      String idColumn = q + "id" + q;
      String table = nonNull(this.tableName, woke.getTableName());
      String sql = "insert into \n" + table + " (\n";
      if (hasId) {
        sql += idColumn + "\n,";
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
      try (PreparedStatement ps = hold.get().prepareStatement(sql)) {
        for (Row row : rows) {
          setObjects(hold, ps, false, row);
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }
  }

  @SuppressWarnings("resource")
  private void doInsertRowWithConnection(Row row, boolean isUpsert, ConnectionHold hold) throws SQLException {
    boolean hasId = row instanceof WakeableRow.IdColumn;
    WokePrivateProxy woke = asWokeProxy(row);
    woke.setIdentifierQuoteString(hold.getIdentifierQuoteString());
    List<String> columns = woke.getColumns(!hasId);
    String q = hold.getIdentifierQuoteString();
    String idColumn = q + "id" + q;
    String table = nonNull(this.tableName, woke.getTableName());
    String sql = "insert into \n" + table + " (\n";
    if (hasId) {
      sql += idColumn + "\n,";
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
        sql += idColumn + "=last_insert_id(" + idColumn + ") ";
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

    boolean auto = isUpsert && row instanceof WakeableRow.IdColumnAsAutoLong;
    Connection cn = hold.get();
    int[] identityColumn = { 1 };
    try (PreparedStatement ps = auto ? cn.prepareStatement(sql, identityColumn) : cn.prepareStatement(sql)) {
      setObjects(hold, ps, isUpsert, row);
      WokePrivateProxy woke3 = asWokeProxy(row);
      Map<String, Object> map3 = woke3.get();
      try {
        int upResult = ps.executeUpdate();
        swallow(() -> require(upResult == 0 || upResult == 1 || upResult == 2));
        if (auto) {
          try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
              Object id = rs.getObject(1);
              map3.put(idColumn, id);
              woke.set(map3);
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
    cn.set(session.reserve());
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

  private List<Row> doSessionFind(String orderBy) {
    LOG.trace("sessionFind");
    try {
      return doSessionFindOp(orderBy);
    } finally {
      LOG.trace("out sessionFind");
    }
  }

  private List<Row> doSessionFindOp(String orderBy) {
    Row keyRow = create(clz);
    asWokeProxy(keyRow);
    key.get().accept(keyRow);
    return doTableFind(keyRow, orderBy);
  }

  private synchronized void doSessionInsertOp(Row row, Consumer<Row> updator, boolean isUpsert) {
    asWokeProxy(row);
    if (key.get() != null) {
      key.get().accept(row);
    }
    updator.accept(row);
    doTableInsert(row, isUpsert);
  }

  private synchronized Row doSessionInsertRow(Row row, Consumer<Row> updator, boolean isUpsert) {
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

  private List<Row> doTableFind(Row row, String orderBy) {
    return require(() -> doTableFindSql(row, orderBy));
  }

  private synchronized List<Row> doTableFindSql(Row row, String orderBy) {
    try (ConnectionHold cn = session.reserve()) {
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
      if (orderBy != null) {
        sql += " order by " + orderBy;
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

  private synchronized void doTableInsertSql(Row row, boolean isUpsert) throws SQLException {
    try (ConnectionHold hold = session.reserve()) {
      doInsertRowWithConnection(row, isUpsert, hold);
    }
  }

  private synchronized Row doUpsert(Row row) {
    key.set(r -> {});
    return doSessionInsertRow(row, r -> {}, true);
  }

  private synchronized Row enterSessionInsertRow(Row row, Consumer<Row> updator, boolean isUpsert) {
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
      List<Row> list = doSessionFind(null);
      return list.isEmpty() ? null : list.get(0);
    });
  }

  @Override
  public String getTableName() {
    return asWokeProxy(create(clz)).getTableName();
  }

  @Override
  public WokenWithRow<Row> id(Long id) {
    require(id != null);
    return this.where(r -> {
      ((WakeableRow.IdColumnAsLong) r).setId(id);
    });
  }

  @Override
  public WokenWithRow<Row> id(String id) {
    return this.where(r -> {
      ((WakeableRow.IdColumnAsString) r).setId(id);
    });
  }

  @Override
  public WokenWithRow<Row> id(UUID id) {
    return this.where(r -> {
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
    return doInsert(row);
  }

  @Override
  public void insertBatch(List<Row> rows) {
    require(() -> doInsertBatch(rows));
  }

  @Override
  public WokeResultSet<Row> iterator(String tableish, Object... params) {
    return require(() -> doIterator(tableish, params));
  }

  @Override
  public List<Row> list() {
    return list(null);
  }

  @Override
  public List<Row> list(String orderBy) {
    return require(() -> doSessionFind(orderBy));
  }

  @Override
  public List<Row> list(String tableish, Object... params) {
    return use(iterator(tableish, params));
  }

  @Override
  public WokenRepository<Row> of(DataSource ds) {
    return of(new WokeDataSourceSession(ds));
  }

  @Override
  public Row of(Map<String, Object> map) {
    Row row = define(r -> {});
    asWokeProxy(row).set(map);
    return row;
  }

  @Override
  public WokenRepository<Row> of(WokeSessionBase session) {
    this.session = session;
    return this;
  }

  private void setObjects(ConnectionHold hold, PreparedStatement ps, boolean isUpsert, Row row) throws SQLException {
    int p = 0;
    String q = hold.getIdentifierQuoteString();
    WokePrivateProxy woke = asWokeProxy(row);
    String idColumn = q + "id" + q;
    boolean hasId = row instanceof WakeableRow.IdColumn;
    Map<String, Object> map = woke.get();
    List<String> columns = woke.getColumns(!hasId);
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
    session.update(row);
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
    return doUpsert(row);
  }

  @Override
  public WokenWithRow<Row> where(Consumer<Row> r) {
    this.key.set(r);
    return this;
  }
}
