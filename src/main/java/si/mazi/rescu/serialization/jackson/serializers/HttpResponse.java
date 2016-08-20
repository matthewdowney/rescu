package si.mazi.rescu.serialization.jackson.serializers;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class HttpResponse {
	private int status;
	private String body;
	private long time;

	public HttpResponse() {}

	public HttpResponse(int status, String body, long originTimeNanos, long startNano) {
		this.status = status;
		this.body = body;
		time = originTimeNanos + (System.nanoTime() - startNano);
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
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
