package moar.geo;

import static com.mashape.unirest.http.Unirest.get;
import static java.lang.Long.parseLong;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;
import static java.lang.String.format;
import static java.lang.System.getenv;
import static moar.awake.InterfaceUtil.use;
import static moar.sugar.MoarStringUtil.urlEncode;
import static moar.sugar.Sugar.nonNull;
import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.swallow;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mashape.unirest.http.Headers;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.exceptions.UnirestException;
import moar.sugar.MoarException;
import moar.sugar.MoarJson;
import moar.sugar.PropertyAccessor;

public class GeoUtil {
  /**
   * Adapted from the <a href= 'https://tinyurl.com/y3vd6yah'>Sanfoundry</a>
   * site.
   */
  private static int orientation(GeoPoint p, GeoPoint q, GeoPoint r) {
    float qX = q.getLat();
    float pX = p.getLat();
    float rX = r.getLat();
    float qY = q.getLon();
    float pY = p.getLon();
    float rY = r.getLon();
    float val = (qY - pY) * (rX - qX) - (qX - pX) * (rY - qY);

    if (val == 0) {
      return 0;
    }

    return val > 0 ? 1 : 2;
  }

  private final AtomicLong openCageCount = new AtomicLong();
  private final AtomicLong openCageRemaining = new AtomicLong(1);
  private final AtomicReference<RateLimiter> openCageRateLimit = new AtomicReference<>(RateLimiter.create(1));
  private final Vector<String> openCageApiKeys;
  private final RateLimiter azureAtlasRateLimit;
  private String azureAtlasUrl;
  private String azureAtlasKey;

  public GeoUtil() {
    openCageApiKeys = new Vector<>();
    int k = 0;
    String key = null;
    do {
      key = getenv(format(format("MOAR_OPEN_CAGE_API_KEY%d", ++k)));
      if (key == null) {
        break;
      }
      openCageApiKeys.add(key);
    } while (true);
    PropertyAccessor props = new PropertyAccessor();
    azureAtlasUrl = props.getString("azureAtlasUrl") + "/search/address/json";
    azureAtlasKey = props.getString("azureAtlasKey");
    azureAtlasRateLimit = RateLimiter.create(props.getDouble("azureAtlasRate", 30));
  }

  private void copy(JsonObject comp, Map<String, Object> map, String key) {
    copy(comp, map, key, key);
  }

  private void copy(JsonObject comp, Map<String, Object> map, String mapFrom, String mapTo) {
    JsonElement compValue = comp.get(mapFrom);
    if (compValue == null) {
      map.remove(mapTo);
    } else {
      map.put(mapTo, swallow(() -> comp.get(mapFrom).getAsString()));
    }
  }

  public GeoDescription describe(GeoPoint point) {
    return swallow(() -> {
      synchronized (openCageApiKeys) {
        HttpResponse<String> response = null;
        int status = 0;
        int tries = 60;
        do {
          if (openCageApiKeys.size() == 0) {
            return null;
          }
          StringBuilder url = new StringBuilder();
          url.append("https://api.opencagedata.com/geocode/v1/json?no_annotations=1");
          url.append("&q=");
          url.append(point.getLat());
          url.append("%2C");
          url.append(point.getLon());
          url.append(format("&key=%s", openCageApiKeys.get(0)));
          openCageRateLimit.get().acquire();
          openCageCount.incrementAndGet();
          response = get(url.toString()).asString();
          status = response.getStatus();
          if (status == 401 || status == 402 || status == 403) {
            openCageApiKeys.remove(0);
          } else {
            HttpResponse<String> finalResponse = response;
            swallow(() -> {
              Headers headers = finalResponse.getHeaders();
              List<String> xRateLimitRemaining = headers.get("X-ratelimit-remaining");
              if (xRateLimitRemaining != null) {
                long remaining = parseLong(xRateLimitRemaining.get(0));
                if (remaining <= 0) {
                  openCageApiKeys.remove(0);
                }
                openCageRemaining.set(remaining);
              }
            });
          }
          if (tries-- < 1) {
            throw new MoarException();
          }
        } while (status != 200);
        HttpResponse<String> finalResponse = response;
        String bodyRaw = require(() -> finalResponse.getBody());
        JsonObject body = MoarJson.getMoarJson().getJsonParser().parse(bodyRaw).getAsJsonObject();
        JsonObject result = body.get("results").getAsJsonArray().get(0).getAsJsonObject();
        JsonObject comp = result.get("components").getAsJsonObject();
        Map<String, Object> map = new HashMap<>();
        copy(comp, map, "country");
        copy(comp, map, "_type", "type");
        copy(comp, map, "footway");
        copy(comp, map, "hamlet");
        copy(comp, map, "village");
        copy(comp, map, "city");
        copy(comp, map, "county");
        copy(comp, map, "state_code", "state");
        copy(comp, map, "county");
        copy(comp, map, "postcode");
        copy(comp, map, "road_type");
        copy(comp, map, "road");
        return use(GeoDescription.class).of(map);
      }
    });
  }

  /**
   * Adapted from the <a href= 'https://tinyurl.com/y3vd6yah'>Sanfoundry</a>
   * site.
   */
  private boolean doIntersect(GeoPoint p1, GeoPoint q1, GeoPoint p2, GeoPoint q2) {

    int o1 = orientation(p1, q1, p2);
    int o2 = orientation(p1, q1, q2);
    int o3 = orientation(p2, q2, p1);
    int o4 = orientation(p2, q2, q1);

    if (o1 != o2 && o3 != o4) {
      return true;
    }

    if (o1 == 0 && onSegment(p1, p2, q1)) {
      return true;
    }

    if (o2 == 0 && onSegment(p1, q2, q1)) {
      return true;
    }

    if (o3 == 0 && onSegment(p2, p1, q2)) {
      return true;
    }

    if (o4 == 0 && onSegment(p2, q1, q2)) {
      return true;
    }

    return false;
  }

  private JsonArray fetchAtlasResult(final String postalAddress) throws UnirestException {
    String url = azureAtlasUrl;
    if (url == null) {
      return null;
    }
    azureAtlasRateLimit.acquire();
    url += "/search/address/json";
    url += format("?subscription-key=%s", azureAtlasKey);
    url += format("&api-version=%s", "1.0");
    url += format("&query=%s", urlEncode(postalAddress));
    HttpResponse<String> result = get(url).asString();
    final JsonObject json = MoarJson.getMoarJson().fromJson(result.getBody());
    final JsonArray jsonResults = json == null ? new JsonArray() : json.getAsJsonArray("results");
    return jsonResults;
  }

  private String getAtlasAddress(String address, final String city, final String state) {
    final int commaPos = address.indexOf(",");
    if (commaPos != -1) {
      address = address.substring(0, commaPos);
    }
    final String postalAddress = address + "\n" + city + ", " + state;
    return postalAddress;
  }

  public long getOpenCageCount() {
    return openCageCount.get();
  }

  public long getOpenCageRemaining() {
    return openCageRemaining.get();
  }

  /**
   * Adapted from the <a href= 'https://tinyurl.com/y3vd6yah'>Sanfoundry</a>
   * site.
   */
  boolean isInside(GeoPoint polygon[], GeoPoint p) {
    int n = polygon.length;
    if (n < 3) {
      return false;
    }

    Float INF = Float.MAX_VALUE;
    GeoPoint extreme = new GeoPointC(INF, p.getLon(), 0F);

    int count = 0, i = 0;
    do {
      int next = (i + 1) % n;
      if (doIntersect(polygon[i], polygon[next], p, extreme)) {
        if (orientation(polygon[i], p, polygon[next]) == 0) {
          return onSegment(polygon[i], p, polygon[next]);
        }

        count++;
      }
      i = next;
    } while (i != 0);

    return (count & 1) == 1 ? true : false;
  }

  /**
   * Adapted from <a href= 'https://tinyurl.com/y3urk9rt'>From Stack
   * Overflow</a>
   */
  public double meters(double lat1, double lon1, Double el1, double lat2, double lon2, Double el2) {
    el1 = nonNull(el1, 0d);
    el2 = nonNull(el2, 0d);
    final int R = 6371; // Radius of the earth

    double latDist = toRadians(lat2 - lat1);
    double lonDist = toRadians(lon2 - lon1);
    double a = sin(latDist / 2) * sin(latDist / 2)
        + cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(lonDist / 2) * sin(lonDist / 2);
    double c = 2 * atan2(sqrt(a), sqrt(1 - a));

    double dist = R * c * 1000; // convert to meters

    double height = el1 - el2;

    dist = pow(dist, 2) + pow(height, 2);

    return sqrt(dist);
  }

  /**
   * Adapted from the <a href= 'https://tinyurl.com/y3vd6yah'>Sanfoundry</a>
   * site.
   */
  private boolean onSegment(GeoPoint p, GeoPoint q, GeoPoint r) {
    float qX = q.getLat();
    float pX = p.getLat();
    float rX = r.getLat();
    float qY = q.getLon();
    float pY = p.getLon();
    float rY = r.getLon();
    return qX <= max(pX, rX) && qX >= min(pX, rX) && qY <= max(pY, rY) && qY >= min(pY, rY);
  }

  public GeoPoint point(String address, String city, String state) {
    String postalAddress = getAtlasAddress(address, city, state);
    JsonArray atlasResult = swallow(() -> fetchAtlasResult(postalAddress));
    if (atlasResult == null) {
      return null;
    }
    if (atlasResult.size() < 1) {
      return null;
    }
    JsonObject jsonFirst = (JsonObject) atlasResult.get(0);
    if (jsonFirst == null) {
      return null;
    }
    final JsonObject jsonPosition;
    jsonPosition = (JsonObject) jsonFirst.get("position");
    if (jsonPosition == null) {
      return null;
    }
    float lat = jsonPosition.get("lat").getAsFloat();
    float lon = jsonPosition.get("lon").getAsFloat();
    return new GeoPointC(lat, lon, null);

  }

  public void setAzureAtlasKey(String value) {
    azureAtlasKey = value;
  }

  public void setAzureAtlasUrl(String value) {
    azureAtlasUrl = value;
  }

  public void setOpenCageRateLimit(double d) {
    openCageRateLimit.set(RateLimiter.create(d));
  }

}
