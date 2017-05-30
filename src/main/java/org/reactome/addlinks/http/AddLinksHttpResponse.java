package org.reactome.addlinks.http;

import java.time.Duration;

import org.apache.http.client.methods.CloseableHttpResponse;

public class AddLinksHttpResponse
{
	private CloseableHttpResponse httpResponse;
	private int numAttempts;
	private Duration responseTime;
	private String responseBody;
	
	public AddLinksHttpResponse(CloseableHttpResponse response)
	{
		this.httpResponse = response;
	}
	
	public AddLinksHttpResponse(CloseableHttpResponse response, int numAttempts)
	{
		this.numAttempts = numAttempts;
		this.httpResponse = response;
	}
	
	public AddLinksHttpResponse(CloseableHttpResponse response, int numAttempts, Duration responseTime)
	{
		this.responseTime = responseTime;
		this.numAttempts = numAttempts;
		this.httpResponse = response;
	}
	
	public CloseableHttpResponse getHttpResponse()
	{
		return this.httpResponse;
	}
	
	public void setHttpResponse(CloseableHttpResponse httpResponse)
	{
		this.httpResponse = httpResponse;
	}
	
	public int getNumAttempts()
	{
		return this.numAttempts;
	}
	
	public void setNumAttempts(int numAttempts)
	{
		this.numAttempts = numAttempts;
	}
	
	public Duration getResponseTime()
	{
		return this.responseTime;
	}
	
	public void setResponseTime(Duration responseTime)
	{
		this.responseTime = responseTime;
	}

	public String getResponseBody()
	{
		return this.responseBody;
	}

	public void setResponseBody(String responseBody)
	{
		this.responseBody = responseBody;
	}
}
