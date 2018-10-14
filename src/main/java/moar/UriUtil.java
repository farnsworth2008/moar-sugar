package moar;

import java.net.URI;

public class UriUtil {
  /**
   * Provide a short version of the URI with only the host name and the last part of the path.
   */
  public static String shortUriDesc(final URI uri) {
    final String path = uri.getPath();
    String host = uri.getHost();
    host = host.substring(0, host.lastIndexOf('.'));
    final int p = host.lastIndexOf('.');
    if (p > -1) {
      host = host.substring(host.lastIndexOf('.') + 1);
    }
    return host + path.substring(path.lastIndexOf('/'));
  }

}
