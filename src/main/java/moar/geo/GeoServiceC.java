package moar.geo;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.valueOfFloat;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class GeoServiceC
    implements
    GeoService {
  private final GeoUtil geoUtil = new GeoUtil();

  @Override
  public String city(GeoPoint point) {
    GeoDescription described = describe(point);
    return described.getCity();
  }

  /**
   * Method to decode polyline points Courtesy :
   *
   * @see <a href= 'https://tinyurl.com/y6be5mh6'>Jeffrey Sambells</a>
   */
  @Override
  public List<GeoPoint> decode(String encoded) {
    List<GeoPoint> poly = new ArrayList<GeoPoint>();
    int index = 0, len = encoded.length();
    int lat = 0, lng = 0;

    while (index < len) {
      int b, shift = 0, result = 0;
      do {
        b = encoded.charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      int dlat = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
      lat += dlat;

      shift = 0;
      result = 0;
      do {
        b = encoded.charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      int dlng = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
      lng += dlng;

      float latitude = (float) (lat / 1E5);
      float longitude = (float) (lng / 1E5);

      GeoPoint p = new GeoPointC(latitude, longitude, 0);
      poly.add(p);
    }

    return poly;
  }

  @Override
  public GeoDescription describe(GeoPoint point) {
    return geoUtil.describe(point);
  }
  private List<GeoPoint> doReadKml(File kmlFile) throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse(kmlFile);
    doc.getDocumentElement().normalize();
    Element elDoc = doc.getDocumentElement();
    NodeList nlPlacemark = elDoc.getElementsByTagName("Placemark");
    Element elPlacemark = (Element) nlPlacemark.item(0);
    NodeList nlPolygon = elPlacemark.getElementsByTagName("Polygon");
    Element elPolygon = (Element) nlPolygon.item(0);
    NodeList nl_outerBoundaryIs = elPolygon.getElementsByTagName("outerBoundaryIs");
    Element el_outerBoundaryIs = (Element) nl_outerBoundaryIs.item(0);
    NodeList nlLinearRing = el_outerBoundaryIs.getElementsByTagName("LinearRing");
    Element elLinearRing = (Element) nlLinearRing.item(0);
    NodeList nl_coordinates = elLinearRing.getElementsByTagName("coordinates");
    Element el_coordinates = (Element) nl_coordinates.item(0);
    String textContent = el_coordinates.getTextContent().trim();
    StringReader sr = new StringReader(textContent);
    String string = IOUtils.toString(sr);
    String[] cords = string.split("[ ,]");
    List<GeoPoint> points = new ArrayList<GeoPoint>();
    for (int i = 0; i < cords.length; i += 3) {
      Float lon = valueOfFloat(cords[i + 0]);
      Float lat = valueOfFloat(cords[i + 1]);
      Float altitude = valueOfFloat(cords[i + 1]);
      points.add(point(lat, lon, altitude));
    }
    return points;
  }

  @Override
  public GeoBound getBounds(List<GeoPoint> points) {
    GeoPoint sw = points.get(0);
    GeoPoint ne = point(sw);
    for (GeoPoint point : points) {
      sw = southWestPoint(sw, point);
      ne = northEastPoint(ne, point);
    }
    return new GeoBoundC(sw, ne);
  }

  @Override
  public long getDescribeServiceCount() {
    return geoUtil.getOpenCageCount();
  }

  @Override
  public long getDescribeServiceRemaining() {
    return geoUtil.getOpenCageRemaining();
  }
  @Override
  public boolean inside(GeoPoint point, List<GeoPoint> points) {
    GeoPoint[] pointsArray = points.toArray(new GeoPoint[0]);
    return geoUtil.isInside(pointsArray, point);
  }

  @Override
  public double meters(GeoPoint p1, GeoPoint p2) {
    float lat1 = p1.getLat();
    float lon1 = p1.getLon();
    float ele1 = p1.getElevation();
    float lat2 = p2.getLat();
    float lon2 = p2.getLon();
    float ele2 = p2.getElevation();
    double meters = geoUtil.meters(lat1, lon1, ele1, lat2, lon2, ele2);
    return meters;
  }

  @Override
  public double metersToMiles(double meters) {
    return meters * 0.000621371192;
  }
  @Override
  public GeoPoint northEastPoint(GeoPoint a, GeoPoint b) {
    float lat = max(a.getLat(), b.getLat());
    float lon = max(a.getLon(), b.getLon());
    GeoPoint p = point(lat, lon, 0F);
    return p;
  }

  @Override
  public GeoPoint point(Float lat, Float lon, Float altitude) {
    return new GeoPointC(lat, lon, altitude);
  }

  @Override
  public GeoPoint point(GeoPoint point) {
    return new GeoPointC(point);
  }

  @Override
  public List<GeoPoint> readKml2(File kmlFile) {
    return require(() -> doReadKml(kmlFile));
  }

  @Override
  public void setDescribeRateLimit(double d) {
    geoUtil.setOpenCageRateLimit(d);
  }

  @Override
  public GeoPoint southWestPoint(GeoPoint a, GeoPoint b) {
    float lat = min(a.getLat(), b.getLat());
    float lon = min(a.getLon(), b.getLon());
    GeoPoint p = point(lat, lon, 0F);
    return p;
  }

}