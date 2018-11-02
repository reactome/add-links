package org.reactome.addlinks.http.client;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;

import org.apache.http.ConnectionClosedException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactome.addlinks.http.AddLinksHttpResponse;


public class AddLinksHttpClient 
{
	private int numAttempts;
	private int maxNumAttempts = 5;
	private Duration timeout = Duration.ofSeconds(30);
	private Duration timeoutRetryIncrement = Duration.ofSeconds(10);
	private URI uri;
	private static final Logger logger = LogManager.getLogger();
	private boolean allowRedirects = true;

	
	public AddLinksHttpResponse executeRequest() throws HttpHostConnectException, IOException, Exception
	{	
		AddLinksHttpResponse addLinksResponse = null;
		HttpGet get = new HttpGet(this.uri);
		
		boolean done = this.numAttempts + 1 <= 0;
		
		while(!done)
		{
			//Need to multiply by 1000 because timeouts are in milliseconds.
			RequestConfig config = RequestConfig.copy(RequestConfig.DEFAULT)
					.setConnectTimeout(1000 * (int)this.timeout.getSeconds())
					.setSocketTimeout(1000 * (int)this.timeout.getSeconds())
					.setRedirectsEnabled(allowRedirects)
					.setRelativeRedirectsAllowed(allowRedirects)
					.setConnectionRequestTimeout(1000 * (int)this.timeout.getSeconds()).build();
			get.setConfig(config);
			Duration responseTime = Duration.ZERO;
			long startTime = System.currentTimeMillis();
			try( CloseableHttpClient client = HttpClients.createDefault();
				CloseableHttpResponse response = client.execute(get,  HttpClientContext.create()); )
			{
				long endtime = System.currentTimeMillis();
				responseTime = Duration.ofMillis(endtime - startTime);
				addLinksResponse = new AddLinksHttpResponse(response, this.numAttempts, responseTime);
				addLinksResponse.setResponseBody(EntityUtils.toString(response.getEntity()));
				done = true;
			}
			catch (ConnectionClosedException | ConnectTimeoutException | SocketTimeoutException e)
			{
				// we will ONLY be retrying the connection timeouts, defined as the time required to establish a connection, or socket timeouts (inactivity that occurs after the connection has been established).
				// we will *not* handle connection manager timeouts (time waiting for connection manager or connection pool).
				long endtime = System.currentTimeMillis();
				e.printStackTrace();
				this.numAttempts++;
				this.timeout = this.timeout.plus(this.timeoutRetryIncrement);
				logger.info("Failed to connect to {} after {} due to cause \"{}\", but will retry {} more time(s) with a longer timeout of {}.", uri.toString(), Duration.ofMillis(endtime - startTime), e.getMessage(), maxNumAttempts - this.numAttempts, this.timeout);
				done = this.numAttempts > maxNumAttempts;
				if (done)
				{
					throw new Exception("Connection timed out. Number of retries ("+this.numAttempts+") exceeded. No further attempts will be made.", e);
				}
			}
			catch (HttpHostConnectException e)
			{
				logger.error("Could not connect to host {} !",get.getURI().getHost());
				e.printStackTrace();
				throw e;
			}
			catch (IOException e) {
				logger.error("Exception caught: {}",e.getMessage());
				throw e;
			}
		}
		
		
		return addLinksResponse;
	}


	public URI getUri()
	{
		return this.uri;
	}


	public void setUri(URI uri)
	{
		this.uri = uri;
	}


	public boolean isAllowRedirects()
	{
		return this.allowRedirects;
	}


	public void setAllowRedirects(boolean allowRedirects)
	{
		this.allowRedirects = allowRedirects;
	}


	public int getMaxNumAttempts()
	{
		return this.maxNumAttempts;
	}


	public void setMaxNumAttempts(int maxNumAttempts)
	{
		this.maxNumAttempts = maxNumAttempts;
	}


	public Duration getTimeoutRetryIncrement()
	{
		return this.timeoutRetryIncrement;
	}


	public void setTimeoutRetryIncrement(Duration timeoutRetryIncrement)
	{
		this.timeoutRetryIncrement = timeoutRetryIncrement;
	}


	public Duration getTimeout()
	{
		return this.timeout;
	}


	public void setTimeout(Duration timeout)
	{
		this.timeout = timeout;
	}
}
