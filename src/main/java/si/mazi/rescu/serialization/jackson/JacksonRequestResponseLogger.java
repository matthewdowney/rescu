package si.mazi.rescu.serialization.jackson;

import org.slf4j.Logger;
import org.slf4j.MDC;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import si.mazi.rescu.RestInvocationHandler.Request;
import si.mazi.rescu.RestInvocationHandler.Response;

public class JacksonRequestResponseLogger {
  private Logger logger;
  private ObjectMapper mapper = new ObjectMapper();

  private static final String DESCRIMINATOR = "connection";
  private final String mdcDescriminatorValue;

  public JacksonRequestResponseLogger(Logger logger) {
    this.logger = logger;
    this.mdcDescriminatorValue = logger.getName();
    mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
  }

  public void logRequestResponse(Request request, Response response) throws JsonProcessingException {
    RequestResponse r = new RequestResponse(request, response);
    MDC.put(DESCRIMINATOR, mdcDescriminatorValue);
    logger.info(mapper.writeValueAsString(r));
  }

  public class RequestResponse {
    public Request request;
    public Response response;

    public RequestResponse(Request request, Response response) {
      this.request = request;
      this.response = response;
    }
  }

}
