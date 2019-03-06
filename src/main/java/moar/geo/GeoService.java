package moar.geo;

public interface GeoLocationService {

  String getDescription(GeoPoint2 point);

  GeoPoint2 point(Float lat, Float lon);

}
