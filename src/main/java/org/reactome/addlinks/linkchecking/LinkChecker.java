package org.reactome.addlinks.linkchecking;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.TemporalUnit;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
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

public class LinkChecker
{

	private static final Logger logger = LogManager.getLogger();
	
	private String keyword;
	private URI uri;
	private static final int MAX_NUM_RETRIES = 5;
	private int numRetries = 0;
	private Duration timeout = Duration.ofSeconds(30);
	
	public LinkChecker(URI uri, String keyword)
	{
		this.uri = uri;
		this.keyword = keyword;
	}
	
	public LinkChecker(CheckableLink link)
	{
		this.uri = link.getURI();
		this.keyword = link.getSearchKeyword();
	}
	
	/**
	 * Tries to GET a URL and then checks that the result contains a keyword.
	 * @param keyword
	 * @throws HttpHostConnectException
	 * @throws IOException
	 * @throws Exception
	 */
	public LinkCheckInfo checkLink() throws HttpHostConnectException, IOException, Exception
	{
		//TODO: maybe look into refactoring this with some of the FileRetriever code. Maybe a new  higher-up class could be used to get the data and let FileRetriever and
		// *this* class do what they want with the resulting bytestring.
		StatusLine responseStatus = null;
		String responseBody = null;
		HttpGet get = new HttpGet(this.uri);
		//Need to multiply by 1000 because timeouts are in milliseconds.
		RequestConfig config = RequestConfig.copy(RequestConfig.DEFAULT)
											.setConnectTimeout(1000 * (int)this.timeout.getSeconds())
											.setSocketTimeout(1000 * (int)this.timeout.getSeconds())
											// Redirects should be enabled.
											.setRedirectsEnabled(true)
											.setRelativeRedirectsAllowed(true)
											.setConnectionRequestTimeout(1000 * (int)this.timeout.getSeconds()).build();
		get.setConfig(config);
		
		boolean done = this.numRetries + 1 <= 0;
		
		LinkCheckInfo linkCheckInfo = new LinkCheckInfo();
		linkCheckInfo.setLinkData(this.uri, this.keyword);
		while(!done)
		{
			Duration responseTime = Duration.ZERO;
			long startTime = System.currentTimeMillis();
			try( CloseableHttpClient client = HttpClients.createDefault();
				CloseableHttpResponse response = client.execute(get,  HttpClientContext.create()); )
			{
				long endtime = System.currentTimeMillis();
				responseTime = Duration.ofMillis(endtime - startTime);
				
				linkCheckInfo.setResponseTime(responseTime);
				linkCheckInfo.setStatusCode(response.getStatusLine().getStatusCode());
				responseBody = EntityUtils.toString(response.getEntity());
				responseStatus = response.getStatusLine();
				done = true;
			}
			catch (ConnectTimeoutException e)
			{
				// we will ONLY be retrying the connection timeouts, defined as the time required to establish a connection.
				// we will *not* handle socket timeouts (inactivity that occurs after the connection has been established).
				// we will *not* handle connection manager timeouts (time waiting for connection manager or connection pool).
				e.printStackTrace();
				this.numRetries++;
				logger.info("Failed due to ConnectTimeout, but will retry {} more time(s).", MAX_NUM_RETRIES - this.numRetries);
				done = this.numRetries > MAX_NUM_RETRIES;
				if (done)
				{
					throw new Exception("Connection timed out. Number of retries ("+this.numRetries+") exceeded. No further attempts will be made.",e);
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
		linkCheckInfo.setNumRetries(this.numRetries);
		if (responseBody != null)
		{
			if (responseStatus != null)
			{
				switch (responseStatus.getStatusCode())
				{
					// If response was OK, check the body to make sure the string we are checking for is there.
					case HttpStatus.SC_OK:
					{
						if (responseBody.contains(this.keyword))
						{
							// then the link is OK.
							linkCheckInfo.setKeywordFound(true);
						}
						else
						{
							// Record the fact that the link is valid but does not contain the keyword.
							linkCheckInfo.setKeywordFound(false);
						}
					}
					break;
					default:
					{
						// log the status code
						if (responseBody.contains(this.keyword))
						{
							// The response contains the keyword but *did* not have an "OK" status. strange...
							linkCheckInfo.setKeywordFound(true);
						}
						else
						{
							// record the fact that: the link is not valid; the response does not contain the keyword (which is expected).
							linkCheckInfo.setKeywordFound(false);
						}
					}
					break;
				}
			}
		}
		return linkCheckInfo;
	}
}
