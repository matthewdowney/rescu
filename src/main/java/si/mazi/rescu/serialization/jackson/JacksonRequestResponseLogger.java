package si.mazi.rescu.serialization.jackson;

import org.slf4j.Logger;
import org.slf4j.MDC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import si.mazi.rescu.serialization.jackson.serializers.HttpRequest;
import si.mazi.rescu.serialization.jackson.serializers.HttpRequestResponse;
import si.mazi.rescu.serialization.jackson.serializers.HttpResponse;

public class JacksonRequestResponseLogger {
  private Logger logger;
  private ObjectMapper mapper = new ObjectMapper();

  private static final String DESCRIMINATOR = "connection";
  private final String mdcDescriminatorValue;

  public JacksonRequestResponseLogger(Logger logger) {
    this.logger = logger;
    this.mdcDescriminatorValue = logger.getName();
  }

  public void logRequestResponse(HttpRequest request, HttpResponse response) throws JsonProcessingException {
    HttpRequestResponse r = new HttpRequestResponse(request, response);
    MDC.put(DESCRIMINATOR, mdcDescriminatorValue);
    logger.info(mapper.writeValueAsString(r));
  }

}
