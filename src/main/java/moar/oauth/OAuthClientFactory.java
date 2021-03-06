package moar.oauth;

public class OAuthClientFactory {

  public static OAuthClient getOAuthClient(String token, long expiresAt, String url) {
    return new BaseOAuthClient(url, token, expiresAt);
  }

}
