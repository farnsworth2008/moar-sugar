package moar.geo;

public class GeoBoundC
    implements
    GeoBound {

  private GeoPoint northEast;
  private GeoPoint southWest;

  public GeoBoundC(GeoPoint sw, GeoPoint ne) {
    setSouthWest(sw);
    setNorthEast(ne);
  }

  @Override
  public GeoPoint getNorthEast() {
    return northEast;
  }

  @Override
  public GeoPoint getSouthWest() {
    return southWest;
  }

  public void setNorthEast(GeoPoint northEast) {
    this.northEast = northEast;
  }

  public void setSouthWest(GeoPoint southWest) {
    this.southWest = southWest;
  }

}
