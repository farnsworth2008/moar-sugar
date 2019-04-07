package moar.oauth;

import static java.lang.Math.min;
import static java.lang.String.format;
import static moar.sugar.MoarJson.getMoarJson;
import static moar.sugar.Sugar.retry;
import static moar.sugar.Sugar.safely;
import static moar.sugar.Sugar.swallow;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.google.common.util.concurrent.RateLimiter;
import com.mashape.unirest.http.Headers;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.GetRequest;
import moar.sugar.CallableVoid;
import moar.sugar.RetryableException;
import moar.sugar.SafeResult;

final class BaseOAuthClient
    implements
    OAuthClient {

  private final AtomicInteger throttle = new AtomicInteger();
  private final AtomicInteger available = new AtomicInteger();
  private final AtomicInteger availableDaily = new AtomicInteger();
  private final String url;
  private final AtomicReference<RateLimiter> rate = new AtomicReference<>();
  private final AtomicReference<CallableVoid> on429 = new AtomicReference<>(() -> {
  });
  private final AtomicReference<String> xRateDesc = new AtomicReference<>("");
  private String token;
  private long expiresAt;

  BaseOAuthClient(String url, String token, long expiresAt) {
    this.url = url;
    this.token = token;
    this.expiresAt = expiresAt;
  }

  @Override
  public <T> SafeResult<T> get(Class<T> clz, String resourceQuery) {
    return safely(() -> {
      return retry(12, 100, () -> {
        synchronized (this) {
          int currentDaily = availableDaily.decrementAndGet();
          int currentAvailable = available.decrementAndGet();
          if (min(currentAvailable, currentDaily) < throttle.get()) {
            // we only use our request limit when our available usage is low.
            RateLimiter currentLimit = rate.get();
            if (currentLimit != null) {
              currentLimit.acquire();
            }
          }
        }
        GetRequest getRequest = Unirest.get(url + resourceQuery);
        getRequest.header("Authorization", format("Bearer %s", token));
        HttpResponse<String> response = getRequest.asString();
        Headers headers = response.getHeaders();
        List<String> limit = headers.get("X-RateLimit-Limit");
        List<String> usage = headers.get("X-RateLimit-Usage");
        if (limit != null && usage != null) {
          String xRateLimit = limit.size() == 0 ? null : limit.get(0);
          String xRateUsage = usage.size() == 0 ? null : usage.get(0);
          Integer limit0 = swallow(() -> Integer.valueOf(xRateLimit.split(",")[0]));
          Integer usage0 = swallow(() -> Integer.valueOf(xRateUsage.split(",")[0]));
          available.set(limit0 - usage0);
          Integer limit1 = swallow(() -> Integer.valueOf(xRateLimit.split(",")[1]));
          Integer usage1 = swallow(() -> Integer.valueOf(xRateUsage.split(",")[1]));
          int newValue = limit1 - usage1;
          availableDaily.set(newValue);
          xRateDesc.set("(" + xRateLimit + ") / (" + xRateUsage + ")");
        }
        int status = response.getStatus();
        if (status == 429) {
          synchronized (this) {
            available.set(0);
            on429.get().call();
          }
          throw new RetryableException("http 429");
        }
        String json = response.getBody();
        T result = getMoarJson().getGson().fromJson(json, clz);
        return result;
      }).get();
    });
  }

  @Override
  public Integer getAvailable() {
    return available.get();
  }

  @Override
  public Integer getAvailableDaily() {
    return availableDaily.get();
  }

  @Override
  public long getExpiresAt() {
    return expiresAt;
  }

  @Override
  public String getXRateDesc() {
    return xRateDesc.get();
  }

  @Override
  public void on429(CallableVoid call) {
    on429.set(call);
  }

  @Override
  public void setAccessToken(String token, long expiresAt) {
    this.token = token;
    this.expiresAt = expiresAt;
  }

  @Override
  public void setThrottleRate(Double permitsPerSecond) {
    rate.set(RateLimiter.create(permitsPerSecond));

  }

  @Override
  public void setThrottleWhen(Integer value) {
    throttle.set(value);
  }

}