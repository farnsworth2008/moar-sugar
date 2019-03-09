package moar.geo;

public interface GeoPoint {
  GeoDescription getDescription();
  float getElevation();
  float getLat();
  float getLon();
  void setDescription(GeoDescription value);
}
