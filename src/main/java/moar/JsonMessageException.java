package moar;

import static moar.JsonUtil.toJson;

public class JsonMessageException
    extends
    RuntimeException {

  public JsonMessageException(final Object... args) {
    super(toJson(args));
  }

}
