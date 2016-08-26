/**
 * Copyright (C) 2013 Matija Mazi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package si.mazi.rescu;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import si.mazi.rescu.serialization.PlainTextResponseReader;
import si.mazi.rescu.serialization.ToStringRequestWriter;
import si.mazi.rescu.serialization.jackson.DefaultJacksonObjectMapperFactory;
import si.mazi.rescu.serialization.jackson.JacksonObjectMapperFactory;
import si.mazi.rescu.serialization.jackson.JacksonRequestResponseLogger;
import si.mazi.rescu.serialization.jackson.JacksonRequestWriter;
import si.mazi.rescu.serialization.jackson.JacksonResponseReader;
import si.mazi.rescu.serialization.jackson.serializers.HttpRequest;
import si.mazi.rescu.serialization.jackson.serializers.HttpResponse;

/**
 * @author Matija Mazi
 */
public class RestInvocationHandler implements InvocationHandler {

	private static final Logger log = LoggerFactory.getLogger(RestInvocationHandler.class);

	private final ResponseReaderResolver responseReaderResolver;
	private final RequestWriterResolver requestWriterResolver;

	private final HttpTemplate httpTemplate;
	private final String intfacePath;
	private final String baseUrl;
	private final ClientConfig config;
	private final JacksonRequestResponseLogger archiver;
	private final JacksonRequestResponseLogger errorArchiver;
	private final long startNano;
	private final long originTimeNanos;

	private final Map<Method, RestMethodMetadata> methodMetadataCache = new HashMap<>();

	// Polling threads
	private final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("RestPollingThread-%d")
			.build();
	private final ExecutorService pollingThreads = Executors.newFixedThreadPool(3, namedThreadFactory);

	RestInvocationHandler(Class<?> restInterface, String url, ClientConfig config, Logger requestResponseLogger,
			Logger errorLogger) {
		intfacePath = restInterface.getAnnotation(Path.class).value();
		baseUrl = url;
		archiver = requestResponseLogger == null ? null : new JacksonRequestResponseLogger(requestResponseLogger);
		errorArchiver = errorLogger == null ? null : new JacksonRequestResponseLogger(errorLogger);
		originTimeNanos = System.currentTimeMillis() * 1_000_000;
		startNano = System.nanoTime();

		if (config == null) {
			config = new ClientConfig(); // default config
		}

		this.config = config;

		// setup default readers/writers
		JacksonObjectMapperFactory mapperFactory = config.getJacksonObjectMapperFactory();
		if (mapperFactory == null) {
			mapperFactory = new DefaultJacksonObjectMapperFactory();
		}
		ObjectMapper mapper = mapperFactory.createObjectMapper();

		requestWriterResolver = new RequestWriterResolver();
		/*
		 * requestWriterResolver.addWriter(null, new NullRequestWriter());
		 */
		requestWriterResolver.addWriter(MediaType.APPLICATION_FORM_URLENCODED, new FormUrlEncodedRequestWriter());
		requestWriterResolver.addWriter(MediaType.APPLICATION_JSON, new JacksonRequestWriter(mapper));
		requestWriterResolver.addWriter(MediaType.TEXT_PLAIN, new ToStringRequestWriter());

		responseReaderResolver = new ResponseReaderResolver();
		responseReaderResolver.addReader(MediaType.APPLICATION_JSON,
				new JacksonResponseReader(mapper, this.config.isIgnoreHttpErrorCodes()));
		responseReaderResolver.addReader(MediaType.TEXT_PLAIN,
				new PlainTextResponseReader(this.config.isIgnoreHttpErrorCodes()));

		// setup http client
		httpTemplate = new HttpTemplate(this.config.getHttpConnTimeout(), this.config.getHttpReadTimeout(),
				this.config.getProxyHost(), this.config.getProxyPort(), this.config.getSslSocketFactory(),
				this.config.getHostnameVerifier(), this.config.getOAuthConsumer());
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		HttpRequest request = new HttpRequest();
		HttpResponse response = new HttpResponse();
		if (method.getDeclaringClass().equals(Object.class)) {
			return method.invoke(this, args);
		}

		RestMethodMetadata methodMetadata = getMetadata(method);

		Object lock = getValueGenerator(args);
		if (lock == null) {
			lock = new Object(); // effectively no locking
		}
		synchronized (lock) {
			RestInvocation invocation = RestInvocation.create(requestWriterResolver, methodMetadata, args, config.getDefaultParamsMap());
			final HttpURLConnection connection = invokeHttp(invocation, request);

			Future<Object> resultFuture = pollingThreads.submit(() -> {
				try {
					Object returned = receiveAndMap(methodMetadata, connection, response);
					if (archiver != null) {
						archiver.logRequestResponse(request, response);
					}
					return returned;
				} catch (Exception e) {
					e.printStackTrace();
					if (errorArchiver != null) {
						errorArchiver.logRequestResponse(request, response);
					}
					boolean shouldWrap = config.isWrapUnexpectedExceptions();
					if (e instanceof InvocationAware) {
						try {
							((InvocationAware) e).setInvocation(invocation);
							shouldWrap = false;
						} catch (Exception ex) {
							log.warn("Failed to set invocation on the InvocationAware", ex);
						}
					}
					if (e instanceof HttpResponseAware && connection != null) {
						try {
							((HttpResponseAware) e).setResponseHeaders(connection.getHeaderFields());
							shouldWrap = false;
						} catch (Exception ex) {
							log.warn("Failed to set response headers on the HttpReponseAware", ex);
						}
					}
					if (shouldWrap) {
						return new AwareException(e, invocation);
					}
					throw e;
				}
			});
			// If the result is an exception, throw it, otherwise return the value
			Object result = resultFuture.get();
			if (result instanceof Throwable) {
				throw (Throwable) result;
			} else {
				return result;
			}
		}
	}

	protected HttpURLConnection invokeHttp(final RestInvocation invocation, HttpRequest request) throws IOException {
		RestMethodMetadata methodMetadata = invocation.getMethodMetadata();

		RequestWriter requestWriter = requestWriterResolver.resolveWriter(invocation.getMethodMetadata());
		final String requestBody = requestWriter.writeBody(invocation);

		// this doesn't connect the connection
		HttpURLConnection conn = httpTemplate.send(invocation.getInvocationUrl(), requestBody,
				invocation.getHttpHeadersFromParams(), methodMetadata.getHttpMethod());
		// log the request data
		request.create(invocation.getInvocationUrl(), conn.getRequestMethod(), httpTemplate.getRecentRequestProperties(),
				requestBody, originTimeNanos, startNano);
		return conn;
	}

	protected Object receiveAndMap(RestMethodMetadata methodMetadata, HttpURLConnection connection, HttpResponse response)
			throws IOException {
		InvocationResult invocationResult = httpTemplate.receive(connection);
		// log the response data
		response.create(invocationResult.getStatusCode(), invocationResult.getHttpBody(), originTimeNanos, startNano);
		return mapInvocationResult(invocationResult, methodMetadata);
	}

	private static SynchronizedValueFactory getValueGenerator(Object[] args) {
		if (args != null) {
			for (Object arg : args) {
				if (arg instanceof SynchronizedValueFactory) {
					return (SynchronizedValueFactory) arg;
				}
			}
		}
		return null;
	}

	protected Object mapInvocationResult(InvocationResult invocationResult, RestMethodMetadata methodMetadata)
			throws IOException {
		return responseReaderResolver.resolveReader(methodMetadata).read(invocationResult, methodMetadata);
	}

	private RestMethodMetadata getMetadata(Method method) {
		RestMethodMetadata metadata = methodMetadataCache.get(method);
		if (metadata == null) {
			metadata = RestMethodMetadata.create(method, baseUrl, intfacePath);
			methodMetadataCache.put(method, metadata);
		}
		return metadata;
	}
}
