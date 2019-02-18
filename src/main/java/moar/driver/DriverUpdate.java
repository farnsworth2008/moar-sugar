package moar.driver;
import static java.lang.System.currentTimeMillis;
import static moar.driver.Driver.getDriverProps;
import static moar.sugar.MoarStringUtil.toSnakeCase;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import moar.sugar.MoarException;
import moar.sugar.MoarLogger;

public class DriverUpdate {

  private final static MoarLogger LOG = new MoarLogger(DriverUpdate.class);
  private static Class<?> loader = DriverUpdate.class;
  private static final long timeoutMillis
      = getDriverProps().getLong("timeoutMillis", 1000 * 60 * 5L);

  public static void setLoader(final Class<?> loader) {
    DriverUpdate.loader = loader;
  }

  private final int tableDoesNotExistErrorCode = 1146;
  private final String track;
  private final Connection connection;
  private final long restPeriod = 1000 * 1;
  private final long startMillis;
  private final String q;

  private final String instance = UUID.randomUUID().toString();
  private final String trackTableName;

  DriverUpdate(final String config, final String url,
      final Connection connection) throws SQLException {
    q = url.startsWith("jdbc:mysql://") ? "`" : "\"";
    this.connection = connection;
    startMillis = currentTimeMillis();
    final String[] param = config.split(";");
    int i = 0;
    String trackConfig = i < param.length ? param[i++] : "default";
    trackConfig = trackConfig.replace('.', '/');
    track = trackConfig;
    trackTableName = "moar_track_" + toSnakeCase(track.replace('/', '_'));
  }

  private void execute(final PreparedStatement find,
      final PreparedStatement register, final Statement statement,
      final PreparedStatement record) throws SQLException, IOException {
    try {
      final List<Integer> scriptsRun = new ArrayList<>();
      int id;
      while (-1 != (id = find(find, register, statement, record))) {
        final int scriptId = id;
        if (!run(register, statement, record, scriptId)) {
          LOG.warn("unable to run (will retry)", id);
          rest(scriptId);
        }
        scriptsRun.add(id);
      }
      if (!scriptsRun.isEmpty()) {
        LOG.trace("DB Update", scriptsRun, connection.getCatalog());
      }
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private int find(final PreparedStatement find,
      final PreparedStatement register, final Statement statement,
      final PreparedStatement record) throws SQLException, IOException {
    int id;
    try {
      try (final ResultSet r = find.executeQuery()) {
        id = r.next() ? r.getInt(1) + 1 : 1000;
      }
    } catch (final SQLException ex) {
      final int errorCode = ex.getErrorCode();
      final String sqlState = ex.getSQLState();
      if (sqlState.equals("42P01") || tableDoesNotExistErrorCode == errorCode) {
        String sql = "CREATE TABLE " + q + "%s" + q + " (" + q + "id" + q
            + " BIGINT, " + q + "instance" + q + " VARCHAR(255)," + q
            + "run_event" + q + " VARCHAR(255),";
        sql += q + "created" + q + " TIMESTAMP," + q + "completed" + q
            + " TIMESTAMP, PRIMARY KEY(" + q + "id" + q + "));";
        sql = String.format(sql, trackTableName);
        statement.execute(sql);
        id = 1000;
      } else {
        throw ex;
      }
    }
    if (getResource(id) != null) {
      return id;
    }
    return -1;
  }

  private InputStream getResource(final int id) {
    final String resource = String.format("/%s/%d.sql", track, id);
    InputStream stream = loader.getResourceAsStream(resource);
    return stream;
  }

  void init() throws SQLException {
    synchronized (Object.class) {
      String findSql = "select " + q + "id" + q + " from " + q + "%s" + q
          + " where not " + q + "completed" + q + " is null order by " + q
          + "id" + q + " desc";
      findSql = String.format(findSql, trackTableName);
      try (PreparedStatement find = connection.prepareStatement(findSql)) {
        String registerSql = "insert into " + q + "%s" + q + " (" + q + "id" + q
            + ", " + q + "instance" + q + ", " + q + "created" + q + ", " + q
            + "run_event" + q + ") values (?, ?, CURRENT_TIMESTAMP, ?)";
        registerSql = String.format(registerSql, trackTableName);
        try (PreparedStatement register
            = connection.prepareStatement(registerSql)) {
          final String recordSql = String.format(
              "update " + q + "%s" + q
                  + " set completed=CURRENT_TIMESTAMP WHERE id=?",
              trackTableName);
          try (PreparedStatement record
              = connection.prepareStatement(recordSql)) {
            try (Statement statement = connection.createStatement()) {
              execute(find, register, statement, record);
            }
          }
        }
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void record(final PreparedStatement record, final int id)
      throws SQLException {
    record.setInt(1, id);
    record.execute();
  }

  private void register(final PreparedStatement register, final int id,
      final String runEvent) throws SQLException {
    int i = 0;
    register.setInt(++i, id);
    register.setString(++i, instance);
    register.setString(++i, runEvent);
    register.execute();
  }

  /**
   * The script manager goes into rest mode if it finds that it can not run a
   * required script.
   * <p>
   * This can occur if another script manager is currently running scripts using
   * the same track table. If this is the case then resting will allow the other
   * script manager to complete it's work.
   * <p>
   * If the other script manager never completes it's work then we can never
   * allow our connection to proceed. One case where this can be very bad is if
   * a script manager crashes after registering a script and before recording
   * the script. If this is the case then we are stuck and after our time out we
   * throw an SQL exception.
   * <p>
   * This occurs under conditions where multiple drivers attempt to start
   * migrations at the same time.
   */
  private void rest(final int id) throws SQLException {
    try {
      Thread.sleep(restPeriod);
    } catch (final InterruptedException e) {
      LOG.warn(e.getMessage());
    }
    if (currentTimeMillis() - startMillis > timeoutMillis) {
      final String msg = "Timeout while waiting on script " + id;
      throw new SQLException(msg);
    }
  }

  /**
   * Register the script
   * <p>
   * In theory it is possible that more then one script runner will attempt to
   * do this at the exact time time with the exact same script number (i.e. in a
   * cluster environment we may have more then one system running at the same
   * time).
   * <p>
   * Regardless of environment only one process can succeed in the race to
   * register a script due to the database primary key restriction.
   */
  private boolean run(final PreparedStatement register,
      final Statement statement, final PreparedStatement record, final int id)
      throws Exception {
    final String runEvent = UUID.randomUUID().toString();
    try {
      register(register, id, runEvent);
    } catch (final SQLException ex) {
      LOG.warn("unable to register script", id, ex.getErrorCode(),
          ex.getSQLState(), ex.getMessage());
      return false;
    }
    final StatementReader stream = new StatementReader(getResource(id));
    String sql;
    long statementNumber = 0;
    while (null != (sql = stream.readStatement())) {
      try {
        sql = sql.replaceAll("%3B", ";");
        if (!sql.equals("DELIMITER ;")) {
          statement.execute(sql);
        }
        statementNumber++;
      } catch (final SQLException e) {
        throw new MoarException("script failed", id, statementNumber, instance,
            runEvent);
      }
    }
    record(record, id);
    return true;
  }
}
