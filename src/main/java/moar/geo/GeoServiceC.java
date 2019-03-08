package moar.geo;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.lang.System.getenv;
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
import com.google.gson.JsonObject;
import com.mashape.unirest.http.Unirest;
import moar.sugar.MoarJson;
import moar.sugar.Sugar;

final class GeoServiceC
    implements
    GeoService {
  static final String MOAR_OPEN_CAGE_API_KEY = getenv("MOAR_OPEN_CAGE_API_KEY");

  private final GeoUtil polygonUtil = new GeoUtil();

  /**
   * Method to decode polyline points Courtesy :
   *
   * @see <a href=
   * 'http://jeffreysambells.com/2010/05/27/decoding-polylines-from-google-maps-direction-api-with-java'>Jeffrey
   * Sambells</a>
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
  public String describe(GeoPoint point) {
    return Sugar.swallow(() -> {
      StringBuilder url = new StringBuilder();
      url.append("https://api.opencagedata.com/geocode/v1/json?");
      url.append("q=");
      url.append(point.getLat());
      url.append("%2C");
      url.append(point.getLon());
      url.append(format("&key=%s", MOAR_OPEN_CAGE_API_KEY));
      String bodyRaw = Sugar.require(() -> Unirest.get(url.toString()).asString().getBody());
      JsonObject body = MoarJson.getMoarJson().getJsonParser().parse(bodyRaw).getAsJsonObject();
      JsonObject result = body.get("results").getAsJsonArray().get(0).getAsJsonObject();
      String formatted = result.get("formatted").getAsString();
      String clean = formatted.replaceAll(", United States of America$", "");
      return clean;
    });
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
  public boolean inside(GeoPoint point, List<GeoPoint> points) {
    GeoPoint[] pointsArray = points.toArray(new GeoPoint[0]);
    return polygonUtil.isInside(pointsArray, point);
  }
  @Override
  public double miles(GeoPoint p1, GeoPoint p2) {
    float lat1 = p1.getLat();
    float lon1 = p1.getLon();
    float ele1 = p1.getElevation();
    float lat2 = p2.getLat();
    float lon2 = p2.getLon();
    float ele2 = p2.getElevation();
    double meters = polygonUtil.distance(lat1, lon1, ele1, lat2, lon2, ele2);
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
    return Sugar.require(() -> doReadKml(kmlFile));
  }
  @Override
  public GeoPoint southWestPoint(GeoPoint a, GeoPoint b) {
    float lat = min(a.getLat(), b.getLat());
    float lon = min(a.getLon(), b.getLon());
    GeoPoint p = point(lat, lon, 0F);
    return p;
  }

}