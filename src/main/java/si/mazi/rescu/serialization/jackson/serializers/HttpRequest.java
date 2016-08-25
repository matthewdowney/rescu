package si.mazi.rescu.serialization.jackson.serializers;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class HttpRequest {
  private String url;
  private String method;
  private Map<String, List<String>> headers;
  private String body;
  private long time;

  public HttpRequest() {
  }

  public HttpRequest(String url, String method, Map<String, List<String>> headers, String body, long originTimeNanos,
      long startNano) {
    create(url, method, headers, body, originTimeNanos, startNano);
  }

  public HttpRequest(HttpRequest other) {
    this.url = other.url;
    this.method = other.method;
    this.headers = other.headers;
    this.body = other.body;
    this.time = other.time;
  }

  public void create(String url, String method, Map<String, List<String>> headers, String body, long originTimeNanos,
      long startNano) {
    this.url = url.trim();
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

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
  }
}
