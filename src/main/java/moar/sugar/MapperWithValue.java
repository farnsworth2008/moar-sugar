package moar.sugar;

import java.util.Date;
import java.util.function.Consumer;
import java.util.function.Function;

public interface MapperWithValue {
  Mapper addHours(int value);
  MapperWithValue capitalizeFully();
  MapperWithValue change(Object string, Object string2);
  MapperWithValue dateApply(Function<Object, Date> fun);
  MapperWithValue simpleDateFormat();
  MapperWithValue stringApply(Function<String, String> fun);
  <T> void to(Consumer<T> consumer);
}
