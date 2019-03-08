package moar.geo;

import java.io.File;
import java.util.List;

public interface GeoService {

  String city(GeoPoint point);

  List<GeoPoint> decode(String polyline);

  GeoDescription describe(GeoPoint point);

  GeoBound getBounds(List<GeoPoint> points);

  long getDescribeServiceRemaining();

  boolean inside(GeoPoint point, List<GeoPoint> points);

  double meters(GeoPoint p1, GeoPoint p2);

  double metersToMiles(double meters);

  GeoPoint northEastPoint(GeoPoint min, GeoPoint point);

  GeoPoint point(Float lat, Float lon, Float altitude);

  GeoPoint point(GeoPoint southWest);

  List<GeoPoint> readKml2(File kmlFile);

  GeoPoint southWestPoint(GeoPoint min, GeoPoint point);

}
