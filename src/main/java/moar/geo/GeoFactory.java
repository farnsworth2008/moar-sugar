package moar.geo;

public class GeoFactory {
  public static GeoService getGeoService() {
    return new GeoServiceC();
  }
}
