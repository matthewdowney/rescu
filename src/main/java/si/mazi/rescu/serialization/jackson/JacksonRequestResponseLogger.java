package si.mazi.rescu.serialization.jackson;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.MDC;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonRequestResponseLogger {

  private Logger logger;
  private ObjectMapper mapper = new ObjectMapper();

  private long startNano;
  private long originTimeNanos;
  private static final String DESCRIMINATOR = "connection";
  private final String mdcDescriminatorValue;

  public JacksonRequestResponseLogger(Logger logger) {
    this.logger = logger;
    this.originTimeNanos = System.currentTimeMillis() * 1000;
    this.startNano = System.nanoTime();
    this.mdcDescriminatorValue = logger.getName();
    mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
  }

  public void logResponse(int status, String body) throws JsonProcessingException {
    Response r = new Response(status, body);
    long time = originTimeNanos + (System.nanoTime() - startNano);
    MDC.put(DESCRIMINATOR, mdcDescriminatorValue);
    logger.info("{} >> {}", time, mapper.writeValueAsString(r));
  }

  public void logRequest(String url, String method, Map<String, List<String>> headers, String body) throws JsonProcessingException {
    Request r = new Request(url, method, headers, body);
    long time = originTimeNanos + (System.nanoTime() - startNano);
    MDC.put(DESCRIMINATOR, mdcDescriminatorValue);
    logger.info("{} << {}", time, mapper.writeValueAsString(r));
  }

  class Request {
    String url;
    String method;
    Map<String, List<String>> headers;
    String body;

    public Request(String url, String method, Map<String, List<String>> headers, String body) {
      this.url = url;
      this.method = method;
      this.headers = headers;
      this.body = body;
    }
  }

  class Response {
    int status;
    String body;

    public Response(int status, String body) {
      this.status = status;
      this.body = body;
    }
  }
}
