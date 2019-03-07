package moar.oauth;

public class OAuthClientFactory {

  public static OAuthClient getOAuthClient(String token, String url) {
    return new BaseOAuthClient(url, token);
  }

}
