package moar.sugar;

import java.util.Map;

public interface MapperForMap {
  MapperWithValue copy(String key);

  Map<String, Object> getMap();
}
