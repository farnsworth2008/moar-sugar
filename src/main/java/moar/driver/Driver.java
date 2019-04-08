package moar.driver;

import static java.lang.System.currentTimeMillis;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.sql.DriverManager.getConnection;
import static java.sql.DriverManager.registerDriver;
import static moar.sugar.Sugar.asRuntimeException;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.retry;
import static moar.sugar.Sugar.retryable;
import static moar.sugar.Sugar.silently;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import com.google.common.util.concurrent.RateLimiter;
import com.zaxxer.hikari.HikariDataSource;
import moar.sugar.MoarException;
import moar.sugar.MoarLogger;
import moar.sugar.PropertyAccessor;
import moar.sugar.RetryResult;
import moar.sugar.Sugar;

/**
 * Driver with the ability to run scripts and recover from connection errors
 */
@SuppressWarnings("resource")
public class Driver
    implements
    java.sql.Driver {
  private static RateLimiter retryRateLimiter = RateLimiter.create(10);
  private static PropertyAccessor props = new PropertyAccessor(Driver.class.getName());
  private static int CONNECTION_RETRY_LIMIT = getDriverProps().getInteger("connectionRetryLimit", 100);
  private static ClassLoader classLoader = Driver.class.getClassLoader();
  private static Map<String, Callable<Connection>> connectionSource = new HashMap<>();
  static MoarLogger LOG = new MoarLogger(Driver.class);
  static {
    try {
      registerDriver(new Driver());
    } catch (SQLException e) {
      throw new RuntimeException("failure static init " + Driver.class, e);
    }
  }
  private static java.util.logging.Logger javaLogger = java.util.logging.Logger.getLogger(Driver.class.getName());
  private static String PREFIX = "moar:";
  private static long VALID_CHECK_MILLIS = 1000 * 60;

  public static DataSource createDataSource(String jdbcUrl, String username, String password)
      throws Exception, IOException {
    return silently(() -> {
      HikariDataSource ds = new HikariDataSource();
      ds.setDriverClassName(Driver.class.getName());
      ds.setJdbcUrl(jdbcUrl);
      ds.setUsername(username);
      ds.setPassword(password);
      try (Connection cn = ds.getConnection()) {
        cn.isValid(1000);
      }
      return ds;
    }).get();
  }

  static PropertyAccessor getDriverProps() {
    return props;
  }

  /**
   * Initialization method to ensure class is loaded.
   */
  public static void init() {
  }
  private final DriverPropertyInfo[] driverProps = new DriverPropertyInfo[] {};

  final HashMap<String, ConnectionSpec> failFast = new HashMap<>();

  Timer recovery;

  private final long restMillis = 1000;

  private final TimerTask recoveryTask = new TimerTask() {
    private final long failFastRecoveryLimit = 1000 * 60 * 5;

    @Override
    public void run() {
      Iterator<ConnectionSpec> i = failFast.values().iterator();
      while (i.hasNext()) {
        ConnectionSpec item = i.next();
        if (currentTimeMillis() - item.createdMillis() > failFastRecoveryLimit) {
          failFast.remove(item.toString());
        } else {
          try {
            doConnect(item).close();
          } catch (SQLException e) {
            LOG.warn("doConnect", e.getMessage(), e);
          }
        }
      }
      if (failFast.size() == 0) {
        Timer removed = recovery;
        recovery = null;
        removed.cancel();
      }
    }
  };

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    boolean startsWith = url.startsWith(PREFIX);
    return startsWith;
  }

  private Connection checkConnection(String caller, AtomicReference<Connection> connection, ConnectionSpec cs)
      throws SQLException {
    if (currentTimeMillis() - cs.getValidCheck().get() > VALID_CHECK_MILLIS) {
      ensureConnectionIsValid(caller, cs, connection);
    }
    return connection.get();
  }

  @Override
  public Connection connect(String url, Properties props) throws SQLException {
    if (!acceptsURL(url)) {
      return null;
    }
    int pLen = PREFIX.length();
    int p = url.indexOf(":", pLen);
    String config = url.substring(pLen, p);
    String backendUrl = url.substring(p + 1);
    ConnectionSpec cs = new ConnectionSpec(backendUrl, props, config);
    ConnectionSpec fcs = failFast.get(cs.toString());
    if (null != fcs) {
      String msg = "The connection specification is in fail fast mode and has not yet recovered.";
      throw new SQLException(msg);
    }
    try {
      return doConnect(cs);
    } catch (SQLException e) {
      failFast.put(cs.toString(), cs);
      startRecovery();
      throw e;
    }
  }

  private PreparedStatement createPreparedStatement1(AtomicReference<Connection> connection, ConnectionSpec cs,
      String sql) throws SQLException {
    Connection c = checkConnection(Sugar.codeLocationAt(2), connection, cs);
    PreparedStatement s = c.prepareStatement(sql);
    return s;
  }

  private PreparedStatement createPreparedStatement2(AtomicReference<Connection> connection, ConnectionSpec cs,
      String sql, int i1) throws SQLException {
    Connection c = checkConnection(Sugar.codeLocationAt(2), connection, cs);
    PreparedStatement s = c.prepareStatement(sql, i1);
    return s;
  }

  private PreparedStatement createPreparedStatement3(AtomicReference<Connection> connection, ConnectionSpec cs,
      String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
    Connection c = checkConnection(Sugar.codeLocationAt(2), connection, cs);
    PreparedStatement s = c.prepareStatement(sql, resultSetType, resultSetConcurrency);
    return s;
  }

  private PreparedStatement createPreparedStatement4(AtomicReference<Connection> connection, ConnectionSpec cs,
      String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    Connection c = checkConnection(Sugar.codeLocationAt(2), connection, cs);
    PreparedStatement s = c.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    return s;
  }

  private PreparedStatement createPreparedStatement5(AtomicReference<Connection> connection, ConnectionSpec cs,
      String sql, int[] i1) throws SQLException {
    Connection c = checkConnection(Sugar.codeLocationAt(2), connection, cs);
    PreparedStatement s = c.prepareStatement(sql, i1);
    return s;
  }

  private PreparedStatement createPreparedStatement6(AtomicReference<Connection> connection, ConnectionSpec cs,
      String sql, String[] p1) throws SQLException {
    Connection c = checkConnection(Sugar.codeLocationAt(2), connection, cs);
    PreparedStatement s = c.prepareStatement(sql, p1);
    return s;
  }

  private Statement createStatement(AtomicReference<Connection> connection, ConnectionSpec cs) throws SQLException {
    Connection c = checkConnection(Sugar.codeLocationAt(2), connection, cs);
    Statement s = c.createStatement();
    return s;
  }

  private Statement createStatement(AtomicReference<Connection> connection, ConnectionSpec cs, int resultSetType,
      int resultSetConcurrency) throws SQLException {
    Connection c = checkConnection(Sugar.codeLocationAt(2), connection, cs);
    Statement s = c.createStatement(resultSetType, resultSetConcurrency);
    return s;
  }

  private Statement createStatement(AtomicReference<Connection> connection, ConnectionSpec cs, int resultSetType,
      int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    Connection c = checkConnection(Sugar.codeLocationAt(2), connection, cs);
    Statement s = c.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    return s;
  }

  Connection doConnect(ConnectionSpec cs) throws SQLException {
    init(cs);
    failFast.remove(cs.getUrl());
    return proxy(cs);
  }

  private void ensureConnectionIsValid(String caller, ConnectionSpec cs, AtomicReference<Connection> connection)
      throws SQLException {
    if (isAutoCommit(connection)) {
      // Don't mess with a transactional connection!
      return;
    }
    int retries = CONNECTION_RETRY_LIMIT;
    while (!isValid(caller, cs, connection) && retries-- > 0) {
      retryRateLimiter.acquire();
      try {
        connection.get().close();
      } catch (SQLException e) {
        LOG.warn("unable to close");
      }
      connection.set(getConnectionFromSource(cs));
    }
    cs.getValidCheck().set(currentTimeMillis());
  }

  private Connection getConnectionFromSource(ConnectionSpec cs) {
    Connection c = null;
    while (c == null) {
      try {
        synchronized (connectionSource) {
          c = connectionSource.get(cs.getUrl()).call();
        }
      } catch (InterruptedException e) {
        LOG.warn("getConnectionFromSource", e.getMessage());
      } catch (ExecutionException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw new RuntimeException(e);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return c;
  }

  @Override
  public int getMajorVersion() {
    return 0;
  }

  @Override
  public int getMinorVersion() {
    return 0;
  }

  @Override
  public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return javaLogger;
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String arg0, Properties arg1) throws SQLException {
    return driverProps;
  }

  private Connection getRealConnection(ConnectionSpec cs) {
    String url = cs.getUrl();
    Properties props = cs.getProps();
    RetryResult<Connection> result = require(() -> {
      return retry(CONNECTION_RETRY_LIMIT, () -> {
        return retryable(() -> getConnection(url, props));
      });
    });
    return result.get();
  }

  private void init(ConnectionSpec cs) throws SQLException {
    String url = cs.getUrl();
    try {
      silently(() -> {
        if (!connectionSource.containsKey(url)) {
          try (Connection cn = getRealConnection(cs)) {
            new DriverUpdate(cs.getConfig(), cs.getUrl(), cn).init();
          }
          connectionSource.put(url, () -> getRealConnection(cs));
        }
      });
    } catch (SQLException e) {
      throw e;
    } catch (Exception e) {
      throw asRuntimeException(e);
    }
  }

  private boolean isAutoCommit(AtomicReference<Connection> connection) throws SQLException {
    return connection.get().getAutoCommit() == false;
  }

  private boolean isPrimativeInt(Object o) {
    if (o == null) {
      return false;
    }
    Class<? extends Object> clz = o.getClass();
    Class<?> componentType = clz.getComponentType();
    if (componentType == null) {
      return false;
    }
    boolean isArray = clz.isArray();
    String name = componentType.getName();
    return componentType.isPrimitive() && !isArray && name.equals("int");
  }

  private boolean isPrimativeIntArray(Object o) {
    Class<? extends Object> clz = o.getClass();
    Class<?> componentType = clz.getComponentType();
    if (componentType == null) {
      return false;
    }
    boolean isArray = clz.isArray();
    return componentType.isPrimitive() && isArray && componentType.getName().equals("int");
  }

  private boolean isValid(String caller, ConnectionSpec cs, AtomicReference<Connection> connection) {
    Connection cn = connection.get();
    try {
      return cn.isValid(1000);
    } catch (SQLException e) {
      String desc = e.getMessage();
      String timeoutMsg = "The last packet successfully received from the server was";
      if (desc.startsWith(timeoutMsg)) {
        desc = desc.substring(timeoutMsg.length(), desc.indexOf("milliseconds ago.")) + "ms";
      }
      LOG.warn("!isValid", caller, e.getErrorCode(), e.getSQLState(), desc, cs.getUrl());
      return false;
    }
  }

  @Override
  public boolean jdbcCompliant() {
    return true;
  }

  private Connection proxy(ConnectionSpec cs) {
    AtomicReference<Connection> connection = new AtomicReference<>();
    connection.set(getConnectionFromSource(cs));
    return (Connection) newProxyInstance(classLoader, new Class<?>[] { Connection.class }, (proxy, method, a) -> {
      try {
        String methodName = method.getName();
        if (methodName.equals("close")) {
          if (connection.get() != null) {
            LOG.trace("close", cs.getUrl());
            silently(connection.get()::close);
            connection.set(null);
          }
          return null;
        } else if (methodName.equals("createStatement")) {
          if (a == null || a.length == 0) {
            return createStatement(connection, cs);
          } else if (a.length == 2) {
            return createStatement(connection, cs, (Integer) a[0], (Integer) a[1]);
          } else {
            return createStatement(connection, cs, (Integer) a[0], (Integer) a[1], (Integer) a[2]);
          }
        } else if (methodName.equals("prepareStatement")) {
          if (a.length == 1) {
            return createPreparedStatement1(connection, cs, (String) a[0]);
          } else if (a.length == 2 && a[1] instanceof Integer) {
            return createPreparedStatement2(connection, cs, (String) a[0], ((Integer) a[1]).intValue());
          } else if (a.length == 2 && isPrimativeInt(a[1])) {
            return createPreparedStatement2(connection, cs, (String) a[0], (int) a[1]);
          } else if (a.length == 2 && isPrimativeIntArray(a[1])) {
            return createPreparedStatement5(connection, cs, (String) a[0], (int[]) a[1]);
          } else if (a.length == 2 && a[1] instanceof String[]) {
            return createPreparedStatement6(connection, cs, (String) a[0], (String[]) a[1]);
          } else if (a.length == 3) {
            return createPreparedStatement3(connection, cs, (String) a[0], (int) a[1], (int) a[2]);
          } else if (a.length == 4) {
            return createPreparedStatement4(connection, cs, (String) a[0], (int) a[1], (int) a[2], (int) a[3]);
          } else {
            throw new MoarException("Not supported: ", method.getParameterCount(), a);
          }
        } else if (methodName.equals("prepareStatement") && a.length == 1) {
          return createPreparedStatement1(connection, cs, (String) a[0]);
        } else if (cs.getMetaData() != null && methodName.equals("getMetaData")) {
          return cs.getMetaData();
        } else if (methodName.equals("isValid") && a.length == 1) {
          Connection c = connection.get();
          Object result = method.invoke(c, a);
          return result;
        } else {
          Connection c = checkConnection(Sugar.codeLocationAt(1), connection, cs);
          Object result = method.invoke(c, a);
          if (methodName.equals("getMetaData")) {
            cs.setMetaData((DatabaseMetaData) result);
          }
          return result;
        }
      } catch (InvocationTargetException | UndeclaredThrowableException e) {
        throw e.getCause();
      }
    });

  }

  private synchronized void startRecovery() {
    if (recovery != null) {
      return;
    }
    recovery = new Timer(getClass().getName() + ":Recovery");
    recovery.schedule(recoveryTask, 0, restMillis);
  }

}
