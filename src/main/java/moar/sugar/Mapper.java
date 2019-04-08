package moar.sugar;

import static moar.sugar.Sugar.swallow;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.text.WordUtils;

public class Mapper
    implements
    MapperWithValue,
    MapperInit {

  public static MapperInit defineMapper(Map<String, Object> map) {
    return new Mapper(map, null);
  }

  private final Map<String, Object> map;

  private Object value;

  private Mapper(Map<String, Object> map, Object value) {
    this.map = map;
    this.value = value;
  }

  @Override
  public Mapper addHours(int value) {
    dateApply(v -> {
      return v == null ? null : DateUtils.addHours((Date) v, value);
    });
    return this;
  }

  @Override
  public MapperWithValue capitalizeFully() {
    return stringApply(WordUtils::capitalizeFully);
  }

  @Override
  public MapperWithValue change(Object from, Object to) {
    if (from == null) {
      if (value == null) {
        value = to;
      }
      return this;
    }
    if (from.equals(value)) {
      value = to;
    }
    return this;
  }

  @Override
  public MapperWithValue copy(String key) {
    value = map.get(key);
    return new Mapper(map, value);
  }

  @Override
  public MapperWithValue dateApply(Function<Object, Date> fun) {
    value = fun.apply(value);
    return this;
  }

  @Override
  public MapperWithValue simpleDateFormat() {
    if (value instanceof String) {
      if (((String) value).isEmpty()) {
        value = null;
        return this;
      }
    }
    SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy");
    value = swallow(() -> format.parse((String) value));
    return this;
  }

  @Override
  public MapperWithValue stringApply(Function<String, String> fun) {
    value = fun.apply((String) value);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void to(Consumer<T> target) {
    target.accept((T) value);
  }

  @Override
  public Map<String, Object> getMap() {
    return map;
  }
}
