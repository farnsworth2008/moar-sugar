package moar.geo;

public class GeoBound2C
    implements
    GeoBound2 {

  private GeoPoint2 northEast;
  private GeoPoint2 southWest;

  public GeoBound2C(GeoPoint2 sw, GeoPoint2 ne) {
    setSouthWest(sw);
    setNorthEast(ne);
  }

  @Override
  public GeoPoint2 getNorthEast() {
    return northEast;
  }

  @Override
  public GeoPoint2 getSouthWest() {
    return southWest;
  }

  public void setNorthEast(GeoPoint2 northEast) {
    this.northEast = northEast;
  }

  public void setSouthWest(GeoPoint2 southWest) {
    this.southWest = southWest;
  }

}
