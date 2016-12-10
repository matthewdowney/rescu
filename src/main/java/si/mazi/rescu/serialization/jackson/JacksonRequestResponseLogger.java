package si.mazi.rescu.serialization.jackson;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import si.mazi.rescu.serialization.jackson.serializers.HttpRequest;
import si.mazi.rescu.serialization.jackson.serializers.HttpRequestResponse;
import si.mazi.rescu.serialization.jackson.serializers.HttpResponse;

/**
 * A logger for {@link HttpRequestResponse} items which uses a buffered writing
 * pattern to ensure logging in order of response time.
 * {@code HttpRequestResponse}s that have been buffered for longer than the
 * configured {@link #BUFFER_TIME} will be serialized and then written using the
 * provided {@link Logger}. While {@code HttpRequestResponse}s are buffering, a
 * sort order is imposed. Therefore, any {@code HttpRequestResponse}s submitted
 * for logging within each {@code BUFFER_TIME} millisecond cycle will be written
 * in order.
 *
 * @author Matthew Downey
 *
 */
public class JacksonRequestResponseLogger {

  /**
   * Purely internal representation of a {@link HttpRequestResponse} to be
   * logged and the time at which it entered the
   * {@link JacksonRequestResponseLogger#loggingBuffer}.
   */
  private class LogItem {
    HttpRequestResponse requestResponse;
    long time;
  }

  private static final Logger log = LoggerFactory.getLogger(JacksonRequestResponseLogger.class);

  /**
   * The user provided {@code Logger} to use with all logging statements.
   */
  private Logger logger;

  /**
   * To serialize the {@link HttpRequestResponse} objects into text for logging.
   */
  private ObjectMapper mapper = new ObjectMapper();

  /**
   * A constant used by the logger indicating that we're logging connection
   * data.
   */
  private static final String DESCRIMINATOR = "connection";

  /**
   * The amount of time (milliseconds) after which buffered outgoing logging
   * statements will be written to the logger.
   */
  private static final long BUFFER_TIME = 1000;

  /**
   * All instances share a single logging thread because the logging happens so
   * quickly.
   */
  private static final ScheduledExecutorService loggingExecutor = Executors
      .newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("LoggingThread-%d").build());

  /**
   * Allows sifting appenders to generate file names for disk output.
   */
  private final String mdcDescriminatorValue;

  /**
   * Used to check if logs are being written in order.
   */
  private long mostRecentLogTime = 0;

  /**
   * A sorted buffer for {@link LogItem}s to be logged.
   */
  private final Queue<LogItem> loggingBuffer = new PriorityQueue<>(
      (a, b) -> (int) (a.requestResponse.getResponse().getTime() - b.requestResponse.getResponse().getTime()));

  public JacksonRequestResponseLogger(Logger logger) {
    this.logger = logger;
    this.mdcDescriminatorValue = logger.getName();

    loggingExecutor.scheduleAtFixedRate(this::flushBuffer, 0, BUFFER_TIME, TimeUnit.MILLISECONDS);
  }

  /**
   * Write queued {@link LogItem}s that have been buffered for more than
   * {@link #BUFFER_TIME} to the provided logger.
   */
  private void flushBuffer() {
    final long current = System.currentTimeMillis();
    LogItem li;
    while ((li = loggingBuffer.peek()) != null) {
      if (current - li.time > BUFFER_TIME) {
        try {
          _log(loggingBuffer.poll().requestResponse);
        } catch (JsonProcessingException e) {
          log.error("Failed to parse JSON while logging {}", li.requestResponse);
          e.printStackTrace();
        }
      } else {
        break;
      }
    }
  }

  /**
   * Submit a {@link HttpRequest} and a matching {@link HttpResponse} to be
   * logged at some time in the future.
   *
   * @param request
   * @param response
   */
  public void logRequestResponse(HttpRequest request, HttpResponse response) {
    LogItem li = new LogItem();
    li.requestResponse = new HttpRequestResponse(request, response);
    li.time = System.currentTimeMillis();
    loggingBuffer.add(li);
  }

  /**
   * Actually log a {@link HttpRequestResponse}.
   *
   * @param rr
   * @throws JsonProcessingException
   */
  private void _log(HttpRequestResponse rr) throws JsonProcessingException {
    validateTimestamp(rr);
    MDC.put(DESCRIMINATOR, mdcDescriminatorValue);
    logger.trace(mapper.writeValueAsString(rr));
  }

  /**
   * Verify that {@link HttpRequestResponse}s are being logged in chronological
   * order according to the response time.
   *
   * @param rr
   */
  private void validateTimestamp(HttpRequestResponse rr) {
    if (this.mostRecentLogTime > rr.getResponse().getTime()) {
      log.error("Request/response being logged out of order");
      System.err.println("Request/response being logged out of order");
    }
    this.mostRecentLogTime = rr.getResponse().getTime();
  }

}
