package moar.geo;

import static java.lang.String.format;

public class Point2dC
    implements
    GeoPoint2 {

  private final float lat;
  private final float lon;

  public Point2dC(float lat, float lon) {
    this.lat = lat;
    this.lon = lon;
  }

  public Point2dC(GeoPoint2 other) {
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
