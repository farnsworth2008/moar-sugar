package moar.geo;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;

public class GeoUtil {

  /**
   * Adapted from the <a href= 'https://tinyurl.com/y3vd6yah'>Sanfoundry</a>
   * site.
   */
  private static int orientation(GeoPoint p, GeoPoint q, GeoPoint r) {
    float qX = q.getLat();
    float pX = p.getLat();
    float rX = r.getLat();
    float qY = q.getLon();
    float pY = p.getLon();
    float rY = r.getLon();
    float val = (qY - pY) * (rX - qX) - (qX - pX) * (rY - qY);

    if (val == 0) {
      return 0;
    }

    return val > 0 ? 1 : 2;
  }

  /**
   * Adapted from <a href= 'https://tinyurl.com/y3urk9rt'>From Stack
   * Overflow</a>
   */
  public double distance(double lat1, double lon1, double el1, double lat2, double lon2, double el2) {

    final int R = 6371; // Radius of the earth

    double latDist = toRadians(lat2 - lat1);
    double lonDist = toRadians(lon2 - lon1);
    double a = sin(latDist / 2) * sin(latDist / 2)
        + cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(lonDist / 2) * sin(lonDist / 2);
    double c = 2 * atan2(sqrt(a), sqrt(1 - a));

    double dist = R * c * 1000; // convert to meters

    double height = el1 - el2;

    dist = pow(dist, 2) + pow(height, 2);

    return sqrt(dist);
  }

  /**
   * Adapted from the <a href= 'https://tinyurl.com/y3vd6yah'>Sanfoundry</a>
   * site.
   */
  private boolean doIntersect(GeoPoint p1, GeoPoint q1, GeoPoint p2, GeoPoint q2) {

    int o1 = orientation(p1, q1, p2);
    int o2 = orientation(p1, q1, q2);
    int o3 = orientation(p2, q2, p1);
    int o4 = orientation(p2, q2, q1);

    if (o1 != o2 && o3 != o4) {
      return true;
    }

    if (o1 == 0 && onSegment(p1, p2, q1)) {
      return true;
    }

    if (o2 == 0 && onSegment(p1, q2, q1)) {
      return true;
    }

    if (o3 == 0 && onSegment(p2, p1, q2)) {
      return true;
    }

    if (o4 == 0 && onSegment(p2, q1, q2)) {
      return true;
    }

    return false;
  }

  /**
   * Adapted from the <a href= 'https://tinyurl.com/y3vd6yah'>Sanfoundry</a>
   * site.
   */
  boolean isInside(GeoPoint polygon[], GeoPoint p) {
    int n = polygon.length;
    if (n < 3) {
      return false;
    }

    Float INF = Float.MAX_VALUE;
    GeoPoint extreme = new GeoPointC(INF, p.getLon(), 0F);

    int count = 0, i = 0;
    do {
      int next = (i + 1) % n;
      if (doIntersect(polygon[i], polygon[next], p, extreme)) {
        if (orientation(polygon[i], p, polygon[next]) == 0) {
          return onSegment(polygon[i], p, polygon[next]);
        }

        count++;
      }
      i = next;
    } while (i != 0);

    return (count & 1) == 1 ? true : false;
  }

  /**
   * Adapted from the <a href= 'https://tinyurl.com/y3vd6yah'>Sanfoundry</a>
   * site.
   */
  private boolean onSegment(GeoPoint p, GeoPoint q, GeoPoint r) {
    float qX = q.getLat();
    float pX = p.getLat();
    float rX = r.getLat();
    float qY = q.getLon();
    float pY = p.getLon();
    float rY = r.getLon();
    return qX <= max(pX, rX) && qX >= min(pX, rX) && qY <= max(pY, rY) && qY >= min(pY, rY);
  }

}
