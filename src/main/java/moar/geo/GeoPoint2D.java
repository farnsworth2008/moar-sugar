package moar.geo;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

public class GeoPoint2D
    implements
    GeoPoint {

  public static GeoPoint2D maxGeoPoint(GeoPoint a, GeoPoint b) {
    return new GeoPoint2D(max(a.getLat(), b.getLat()), max(a.getLon(), b.getLon()));
  }
  public static GeoPoint2D minGeoPoint(GeoPoint a, GeoPoint b) {
    return new GeoPoint2D(min(a.getLat(), b.getLat()), min(a.getLon(), b.getLon()));
  }

  private final float lat;
  private final float lon;

  public GeoPoint2D(float lat, float lon) {
    this.lat = lat;
    this.lon = lon;
  }

  public GeoPoint2D(GeoPoint other) {
    this(other.getLat(), other.getLon());
  }

  @Override
  public float getLat() {
    return lat;
  }

  @Override
  public float getLon() {
    return lon;
  }

  @Override
  public String toString() {
    return format("(%s,%s)", lat, lon);
  }

}
