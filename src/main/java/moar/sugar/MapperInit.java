package moar.sugar;

import java.util.Map;

public interface MapperInit {
  MapperWithValue copy(String key);

  Map<String, Object> getMap();
}
