package moar.sugar;

import static moar.sugar.Sugar.require;
import static moar.sugar.Sugar.swallow;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * A simple way to get json without having to construct Gson and without checked
 * exceptions.
 *
 * @author Mark Farnsworth
 */
public class MoarJson {

  /**
   * Static instance that is potentially shared with all threads.
   */
  private static MoarJson staticInstance = new MoarJson();

  /**
   * Thread instance, can be altered if a thread needs something special.
   */
  private static ThreadLocal<MoarJson> instance = ThreadLocal.withInitial(() -> {
    return staticInstance;
  });

  /**
   * @return instance for the thread
   */
  public static MoarJson getMoarJson() {
    return instance.get();
  }

  private final Gson gson;

  private final Gson gsonPretty;

  private final JsonParser jsonParser;

  /**
   * Create a standard instance.
   */
  public MoarJson() {
    GsonBuilder builder = new GsonBuilder().setLenient();
    gson = builder.create();
    gsonPretty = builder.setPrettyPrinting().create();
    jsonParser = new JsonParser();
  }

  /**
   * Create Moar JSON with custom dependencies.
   *
   * @param gson
   *   Gson instance
   * @param gsonPretty
   *   Gson Pretty instance.
   * @param jsonParser
   *   Json Parser.
   */
  public MoarJson(Gson gson, Gson gsonPretty, JsonParser jsonParser) {
    this.gson = gson;
    this.gsonPretty = gsonPretty;
    this.jsonParser = jsonParser;
  }

  /**
   * Parse from an input stream.
   *
   * @param stream
   *   Input stream.
   * @return Content (or null)
   */
  public JsonElement fromJson(InputStream stream) {
    return swallow(() -> {
      try (InputStreamReader isr = new InputStreamReader(stream)) {
        JsonElement o = jsonParser.parse(isr);
        return o;
      }
    });
  }

  /**
   * Parse a string.
   *
   * @param json
   *   String of json.
   * @return Content or null.
   */
  @SuppressWarnings("unchecked")
  public <T extends JsonElement> T fromJson(String json) {
    return swallow(() -> {
      return (T) jsonParser.parse(json);
    });
  }

  /**
   * Get a class instance from a file that contains compatible JSON.
   *
   * @param filename
   *   Filename for the file.
   * @param clz
   *   Class for the instance.
   * @return Instance from JSON
   */
  public <T> T fromJsonFile(String filename, Class<T> clz) {
    String json = require(() -> {
      try (FileInputStream input = new FileInputStream(filename)) {
        return IOUtils.toString(input, Charset.defaultCharset());
      }
    });
    return gson.fromJson(json, clz);
  }

  /**
   * @return Gson instance
   */
  public Gson getGson() {
    return gson;
  }

  /**
   * @return the parser.
   */
  public JsonParser getJsonParser() {
    return jsonParser;
  }

  /**
   * Create some pretty json.
   *
   * @param args
   *   Some objects
   * @return Pretty JSON.
   */
  public String prettyJson(Object... args) {
    try {
      Object[] safe = new Object[args.length];
      for (int i = 0; i < args.length; i++) {
        Object a = args[i];
        try {
          gsonPretty.toJson(a);
          safe[i] = a;
        } catch (RuntimeException e) {
          safe[i] = gsonPretty.toJson(a.toString());
        }
      }
      return gsonPretty.toJson(safe);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * @param ugly
   *   Some ugly json
   * @return Some pretty json
   */
  public String prettyJson(String ugly) {
    return ugly == null ? null : swallow(() -> prettyJson(fromJson(ugly)));
  }

  /**
   * Convert objects to JSON safely. Objects that create exceptions are stored
   * using their toString representations.
   *
   * @param args
   *   Objects.
   * @return JSON string.
   */
  public String toJsonSafely(Object... args) {
    return swallow(() -> {
      Object[] safe = new Object[args.length];
      for (int i = 0; i < args.length; i++) {
        Object a = args[i];
        try {
          gson.toJson(a);
          safe[i] = a;
        } catch (RuntimeException e) {
          Map<String, String> toStringMap = new HashMap<>();
          toStringMap.put(a.getClass().getSimpleName() + ".toString()", gson.toJson(a.toString()));
          safe[i] = toStringMap;
        }
      }
      return gson.toJson(safe);
    });
  }

}
