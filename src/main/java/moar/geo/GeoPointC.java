package moar.geo;

import static java.lang.String.format;

public class GeoPointC
    implements
    GeoPoint {

  private final float lat;
  private final float lon;
  private final float altitude;
  private GeoDescription description;

  public GeoPointC(float lat, float lon, float altitude) {
    this.lat = lat;
    this.lon = lon;
    this.altitude = altitude;
  }

  public GeoPointC(GeoPoint other) {
    this(other.getLat(), other.getLon(), other.getElevation());
  }

  @Override
  public GeoDescription getDescription() {
    return description;
  }

  @Override
  public float getElevation() {
    return altitude;
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
  public void setDescription(GeoDescription value) {
    description = value;
  }

  @Override
  public String toString() {
    return format("(%s,%s)", lat, lon);
  }

}
