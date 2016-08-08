package si.mazi.rescu.serialization.jackson.serializers;

public class HttpRequestResponse {
  private HttpRequest request;
  private HttpResponse response;

  public HttpRequestResponse() {}

  public HttpRequestResponse(HttpRequest request, HttpResponse response) {
    this.request = request;
    this.response = response;
  }

  public HttpRequest getRequest() {
    return request;
  }

  public void setRequest(HttpRequest request) {
    this.request = request;
  }

  public HttpResponse getResponse() {
    return response;
  }

  public void setResponse(HttpResponse response) {
    this.response = response;
  }

}
