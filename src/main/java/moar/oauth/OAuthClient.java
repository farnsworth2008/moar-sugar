package moar.oauth;

import moar.sugar.CallableVoid;
import moar.sugar.SafeResult;

public interface OAuthClient {
  <T> SafeResult<T> get(Class<T> clz, String resourceQuery);

  Integer getAvailable();

  Integer getAvailableDaily();

  String getXRateDesc();

  void on429(CallableVoid call);

  void setThrottle(Integer value);

  void setThrottleRate(Double value);
}
