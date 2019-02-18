package moar.driver;

import static java.lang.System.currentTimeMillis;
import java.sql.DatabaseMetaData;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

class ConnectionSpec {
  private final String backendUrl;
  private final Properties props;
  private final String config;
  private final long createdMillis;
  private final AtomicLong validCheck = new AtomicLong();
  private DatabaseMetaData metaData;

  ConnectionSpec(String backendUrl, Properties props, String config) {
    this.backendUrl = backendUrl;
    this.props = props;
    this.config = config;
    createdMillis = currentTimeMillis();
  }

  long createdMillis() {
    return createdMillis;
  }

  String getConfig() {
    return config;
  }

  public DatabaseMetaData getMetaData() {
    return metaData;
  }

  Properties getProps() {
    return new Properties(props);
  }

  String getUrl() {
    return backendUrl;
  }

  public AtomicLong getValidCheck() {
    return validCheck;
  }

  public void setMetaData(DatabaseMetaData metaData) {
    this.metaData = metaData;
  }

}
