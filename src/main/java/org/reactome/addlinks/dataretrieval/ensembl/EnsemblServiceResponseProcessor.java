package org.reactome.addlinks.dataretrieval.ensembl;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class EnsemblServiceResponseProcessor
{

	class EnsemblServiceResult
	{
		private Duration waitTime = Duration.ZERO;
		private String result;
		private boolean okToRetry;
		private int status;
		
		public Duration getWaitTime()
		{
			return this.waitTime;
		}
		public void setWaitTime(Duration waitTime)
		{
			this.waitTime = waitTime;
		}
		public String getResult()
		{
			return this.result;
		}
		public void setResult(String result)
		{
			this.result = result;
		}
		public boolean isOkToRetry()
		{
			return this.okToRetry;
		}
		public void setOkToRetry(boolean okToRetry)
		{
			this.okToRetry = okToRetry;
		}
		public int getStatus()
		{
			return this.status;
		}
		public void setStatus(int status)
		{
			this.status = status;
		}
	}
	
	// Assume a quote of 10 to start. This will get set properly with ever response from the service.
	private static final AtomicInteger numRequestsRemaining = new AtomicInteger(10);
	
	private static final Logger logger = LogManager.getLogger();
	
	public static EnsemblServiceResult processResponse(HttpResponse response)
	{
		EnsemblServiceResult result = (new EnsemblServiceResponseProcessor()).new EnsemblServiceResult();
		result.setStatus(response.getStatusLine().getStatusCode());
		boolean okToQuery = false;
		// First check to see if we got a "Retry-After" header. This is most likely to happen if we send SO many requests
		// that we used up our quota with the service, and need to wait for it to reset.
		if ( response.containsHeader("Retry-After") )
		{
			logger.debug("Response code: {}", response.getStatusLine().getReasonPhrase() );
			Duration waitTime = Duration.ofSeconds(Integer.valueOf(response.getHeaders("Retry-After")[0].getValue().toString()));
			logger.info("The server told us to wait, so we will wait for {} before trying again.",waitTime);
			result.setWaitTime(waitTime);
			// It's ok to re-query the sevice, as long as you wait for the time the server wants you to wait.
			okToQuery = true;
		}
		// Else... no "Retry-After" so we haven't gone over our quota.
		else
		{
			String content = "";
			switch (response.getStatusLine().getStatusCode())
			{
				case HttpStatus.SC_OK:
					try
					{
						//ContentType.get(response.getEntity()).getCharset().name();
						content = EntityUtils.toString(response.getEntity(), Charset.forName("UTF-8"));
					}
					catch (ParseException e)
					{
						e.printStackTrace();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					result.setResult(content);
					okToQuery = false;
					break;
				case HttpStatus.SC_NOT_FOUND:
					logger.error("Response code 404 (\"Not found\") received: ", response.getStatusLine().getReasonPhrase() );
					// If we got 404, don't retry.
					okToQuery = false;
					break;
				case HttpStatus.SC_INTERNAL_SERVER_ERROR:
					logger.error("Error 500 detected! Message: {}",response.getStatusLine().getReasonPhrase());
					// If we get 500 error then we should just get  out of here. Maybe throw an exception?
					okToQuery = false;
					break;
				case HttpStatus.SC_BAD_REQUEST:
					String s = "";
					try
					{
						s = EntityUtils.toString(response.getEntity());
					}
					catch (ParseException e)
					{
						e.printStackTrace();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					logger.trace("Response code was 400 (\"Bad request\"). Message from server: {}", s);
					okToQuery = false;
					break;
				case HttpStatus.SC_GATEWAY_TIMEOUT:
					logger.error("Request timed out! You should retry it.");
					okToQuery = true;
					break;
				default:
					// Log any other kind of response.
					okToQuery = false;
					try
					{
						content = EntityUtils.toString(response.getEntity(), Charset.forName("UTF-8"));
					}
					catch (ParseException e)
					{
						e.printStackTrace();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					result.setResult(content);
					logger.info("Unexpected response {} with message: {}",response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
					break;
			}
		}
		result.setOkToRetry(okToQuery);
		if (response.containsHeader("X-RateLimit-Remaining"))
		{
			int numRequestsRemaining = Integer.valueOf(response.getHeaders("X-RateLimit-Remaining")[0].getValue().toString());
			EnsemblServiceResponseProcessor.numRequestsRemaining.set(numRequestsRemaining);
		}
		return result;
	}
	
	public static int getNumRequestsRemaining()
	{
		return EnsemblServiceResponseProcessor.numRequestsRemaining.get();
	}
}
