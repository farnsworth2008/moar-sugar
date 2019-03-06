package moar.geo;

import java.io.File;
import java.util.List;

public interface GeoService {

  List<GeoPoint2> decodePoly(String polyline);

  String describe(GeoPoint2 point);

  GeoBound2 getBounds(List<GeoPoint2> points);

  GeoPoint2 northEastPoint(GeoPoint2 min, GeoPoint2 point);

  GeoPoint2 point(Float lat, Float lon);

  GeoPoint2 point(GeoPoint2 southWest);

  List<GeoPoint2> readKml2(File kmlFile);

  GeoPoint2 southWestPoint(GeoPoint2 min, GeoPoint2 point);

}
