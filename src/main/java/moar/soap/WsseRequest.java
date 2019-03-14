package moar.soap;

import static java.lang.String.format;
import static moar.sugar.Sugar.require;
import java.util.Map;
import org.json.JSONObject;
import org.json.XML;

public class WsseRequest {
  private static final String SOAP_ENVELOPE = "http://schemas.xmlsoap.org/soap/envelope/";
  private static final String UTILITY_1_0_XSD = "utility-1.0.xsd";
  private static final String SECEXT_1_0_XSD = "secext-1.0.xsd";
  private static final String OASIS_SECURITY = "oasis-200401-wss-wssecurity-";
  private static final String OASIS_WSS_2004_01 = "http://docs.oasis-open.org/wss/2004/01/";
  private static final String WSSE_HEADER_TEMPLATE = createWsseHeaderTemplate();

  private static String createWsseHeaderTemplate() {
    String wsse = OASIS_WSS_2004_01 + OASIS_SECURITY + SECEXT_1_0_XSD;
    String wsu = OASIS_WSS_2004_01 + OASIS_SECURITY + UTILITY_1_0_XSD;
    return require(() -> {
      StringBuilder b = new StringBuilder();
      b.append("<soapenv:Header>");
      b.append("<wsse:Security xmlns:wsse=\"" + wsse + "\">");
      b.append("<wsse:UsernameToken xmlns:wsu=\"" + wsu + "\">");
      b.append("<wsse:Username>%s</wsse:Username>");
      b.append("<wsse:Password>%s</wsse:Password>");
      b.append("</wsse:UsernameToken>");
      b.append("</wsse:Security>");
      b.append("</soapenv:Header>");
      return b.toString();
    });
  }

  public static String wsseHeader(String username, String password) {
    return format(WSSE_HEADER_TEMPLATE, username, password);
  }

  private final String namespace;
  private final String namespaceUrl;
  private String username;
  private String password;
  private String body;

  public WsseRequest(String namespace, String namespaceUrl) {
    this.namespace = namespace;
    this.namespaceUrl = namespaceUrl;
  }

  public void setBody(Map<String, Object> value) {
    JSONObject bodyJson = new JSONObject(value);
    body = XML.toString(bodyJson);
  }

  public void setPassword(String value) {
    password = value;
  }

  public void setUsername(String value) {
    username = value;
  }

  public String toXml() {
    return require(() -> {
      StringBuilder b = new StringBuilder();
      b.append("<soapenv:Envelope xmlns:soapenv=\"" + SOAP_ENVELOPE + "\" ");
      b.append("xmlns:" + namespace + "=\"" + namespaceUrl + "\"");
      b.append(">");
      b.append(wsseHeader(username, password));
      b.append("<soapenv:Body>");
      b.append(body);
      b.append("</soapenv:Body>");
      b.append("</soapenv:Envelope>");
      return b.toString();
    });
  }

}
