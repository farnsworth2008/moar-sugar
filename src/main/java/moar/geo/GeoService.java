package moar.geo;

import java.io.File;
import java.util.List;

public interface GeoService {

  List<GeoPoint> decode(String polyline);

  String describe(GeoPoint point);

  GeoBound getBounds(List<GeoPoint> points);

  boolean inside(GeoPoint point, List<GeoPoint> points);

  double miles(GeoPoint p1, GeoPoint p2);

  GeoPoint northEastPoint(GeoPoint min, GeoPoint point);

  GeoPoint point(Float lat, Float lon, Float altitude);

  GeoPoint point(GeoPoint southWest);

  List<GeoPoint> readKml2(File kmlFile);

  GeoPoint southWestPoint(GeoPoint min, GeoPoint point);

}
