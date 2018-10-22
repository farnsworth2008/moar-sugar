package moar;

import static moar.Exceptional.$;
import static moar.Exceptional.expect;
import static moar.Exceptional.require;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import org.slf4j.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * A simple way to get json representations from objects
 */
public class JsonUtil {
  private static final String FMT = "{} {}";
  private static final Gson gson = new GsonBuilder().setLenient().create();
  private static final Gson gsonPretty = new GsonBuilder().setLenient().setPrettyPrinting().create();
  private static final JsonParser jsonParser = new JsonParser();

  public static void debug(final Logger log, Object... args) {
    if (args[args.length - 1] instanceof Throwable) {
      final Object t = args[args.length - 1];
      args = Arrays.copyOf(args, args.length - 1);
      log.debug(FMT, $(1), toJson(unpack(args)), t);
    } else {
      log.debug(FMT, $(1), toJson(unpack(args)));
    }
  }

  public static void error(final Logger log, Object... args) {
    if (args[args.length - 1] instanceof Throwable) {
      final Object t = args[args.length - 1];
      args = Arrays.copyOf(args, args.length - 1);
      log.error(FMT, $(1), toJson(unpack(args)), t);
    } else {
      log.error(FMT, $(1), toJson(unpack(args)));
    }
  }

  public static JsonElement fromJson(final InputStream s) {
    return (JsonElement) require(() -> {
      final JsonElement o = jsonParser.parse(new InputStreamReader(s));
      return o;
    });
  }

  public static <T extends JsonElement> T fromJson(final String json) {
    return (T) jsonParser.parse(json);
  }

  public static Gson getGson() {
    return gson;
  }

  public static void info(final Logger log, Object... args) {
    if (args[args.length - 1] instanceof Throwable) {
      final Object t = args[args.length - 1];
      args = Arrays.copyOf(args, args.length - 1);
      log.info(FMT, $(1), toJson(unpack(args)), t);
    } else {
      log.info(FMT, $(1), toJson(unpack(args)));
    }
  }

  public static String prettyJson(final Object... args) {
    try {
      final Object[] safe = new Object[args.length];
      for (int i = 0; i < args.length; i++) {
        final Object a = args[i];
        try {
          gsonPretty.toJson(a);
          safe[i] = a;
        } catch (final RuntimeException e) {
          safe[i] = gsonPretty.toJson(a.toString());
        }
      }
      return gsonPretty.toJson(safe);
    } catch (final RuntimeException e) {
      return null;
    }
  }

  public static String prettyJson(final String ugly) {
    return ugly == null ? null : expect(() -> prettyJson(fromJson(ugly)));
  }

  public static String toJson(final Object... args) {
    try {
      final Object[] safe = new Object[args.length];
      for (int i = 0; i < args.length; i++) {
        final Object a = args[i];
        try {
          gson.toJson(a);
          safe[i] = a;
        } catch (final RuntimeException e) {
          safe[i] = gson.toJson(a.toString());
        }
      }
      return gson.toJson(safe);
    } catch (final RuntimeException e) {
      return null;
    }
  }

  public static void trace(final Logger log, Object... args) {
    if (args[args.length - 1] instanceof Throwable) {
      final Object t = args[args.length - 1];
      args = Arrays.copyOf(args, args.length - 1);
      log.trace(FMT, $(1), toJson(unpack(args)), t);
    } else {
      log.trace(FMT, $(1), toJson(unpack(args)));
    }
  }

  private static Object[] unpack(final Object[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof String) {
        try {
          args[i] = jsonParser.parse((String) args[i]);
        } catch (final RuntimeException e) {
          // swallow
        }
      }
    }
    return args;
  }

  public static void warn(final Logger log, Object... args) {
    if (args[args.length - 1] instanceof Throwable) {
      final Object t = args[args.length - 1];
      args = Arrays.copyOf(args, args.length - 1);
      log.warn(FMT, $(1), toJson(unpack(args)), t);
    } else {
      log.warn(FMT, $(1), toJson(unpack(args)));
    }
  }

}
