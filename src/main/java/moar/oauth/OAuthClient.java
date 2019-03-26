package moar.oauth;

import moar.sugar.CallableVoid;
import moar.sugar.SafeResult;

public interface OAuthClient {
  <T> SafeResult<T> get(Class<T> clz, String resourceQuery);

  Integer getAvailable();

  Integer getAvailableDaily();

  long getExpiresAt();

  String getXRateDesc();

  void on429(CallableVoid call);

  void setAccessToken(String token, long expiresAt);

  void setThrottleRate(Double value);

  void setThrottleWhen(Integer value);
}
