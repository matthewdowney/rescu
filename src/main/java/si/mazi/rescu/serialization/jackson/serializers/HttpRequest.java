package si.mazi.rescu.serialization.jackson.serializers;

import java.util.List;
import java.util.Map;

public class HttpRequest {
  private String url;
  private String method;
  private Map<String, List<String>> headers;
  private String body;
  private long time;

  public HttpRequest() {}

  public HttpRequest(String url, String method, Map<String, List<String>> headers, String body, long originTimeNanos,
      long startNano) {
    this.url = url;
    this.method = method;
    this.headers = headers;
    this.body = body;
    this.time = originTimeNanos + (System.nanoTime() - startNano);
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public Map<String, List<String>> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, List<String>> headers) {
    this.headers = headers;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

}
