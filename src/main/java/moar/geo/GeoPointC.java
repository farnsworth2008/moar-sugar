package moar.geo;

import static java.lang.String.format;
import java.util.concurrent.atomic.AtomicReference;

public class GeoPointC
    implements
    GeoPoint {

  private final float lat;
  private final float lon;
  private final Float ele;
  private final AtomicReference<GeoDescription> description = new AtomicReference<GeoDescription>();

  public GeoPointC(float lat, float lon, Float ele) {
    this.lat = lat;
    this.lon = lon;
    this.ele = ele;
  }

  public GeoPointC(GeoPoint other) {
    this(other.getLat(), other.getLon(), other.getEle());
  }

  @Override
  public GeoDescription getDescription() {
    return description.get();
  }

  @Override
  public Float getEle() {
    return ele;
  }

  @Override
  public float getLat() {
    return lat;
  }

  @Override
  public float getLon() {
    return lon;
  }

  void setDescription(GeoDescription value) {
    description.set(value);
  }

  @Override
  public String toString() {
    return format("(%s,%s)", lat, lon);
  }

}
