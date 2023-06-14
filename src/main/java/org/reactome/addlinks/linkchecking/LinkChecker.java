package org.reactome.addlinks.linkchecking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.reactome.release.common.CustomLoggable;

public class LinkChecker implements CustomLoggable
{

	private static Logger logger;

	private String keyword;
	private URI uri;
	private static final int MAX_NUM_RETRIES = 5;
	private int numRetries = 0;
	private Duration timeout = Duration.ofSeconds(30);

	public LinkChecker(URI uri, String keyword)
	{
		this.uri = uri;
		this.keyword = keyword;
		if (LinkChecker.logger == null)
		{
			LinkChecker.logger = this.createLogger("LinkChecker", "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG);
		}
	}

	public LinkChecker(CheckableLink link)
	{
		this(link.getURI(), link.getSearchKeyword());
	}

	/**
	 * Tries to GET a URL and then checks that the result contains a keyword.
	 * @throws IOException
	 * @throws Exception
	 */
	public LinkCheckInfo checkLink() throws IOException, Exception
	{
		//TODO: maybe look into refactoring this with some of the FileRetriever code. Maybe a new  higher-up class could be used to get the data and let FileRetriever and
		// *this* class do what they want with the resulting bytestring.

		HttpURLConnection urlConnection = (HttpURLConnection) this.uri.toURL().openConnection();
		//Need to multiply by 1000 because timeouts are in milliseconds.
		int timeoutInMilliSeconds = 1000 * (int)this.timeout.getSeconds();

		urlConnection.setReadTimeout(timeoutInMilliSeconds);
		urlConnection.setConnectTimeout(timeoutInMilliSeconds);
		urlConnection.setInstanceFollowRedirects(true);

		String responseBody = null;

		boolean done = this.numRetries + 1 <= 0;

		LinkCheckInfo linkCheckInfo = new LinkCheckInfo();
		linkCheckInfo.setLinkData(this.uri, this.keyword);
		logger.trace("Checking link: {}", this.uri);
		while(!done)
		{
			Duration responseTime = Duration.ZERO;
			long startTime = System.currentTimeMillis();
			try
			{
				long endtime = System.currentTimeMillis();
				responseTime = Duration.ofMillis(endtime - startTime);

				linkCheckInfo.setResponseTime(responseTime);
				linkCheckInfo.setStatusCode(urlConnection.getResponseCode());
				responseBody = getContent(urlConnection);
				done = true;
			}
			catch (SocketTimeoutException e)
			{
				// we will ONLY be retrying the connection timeouts, defined as the time required to establish a connection.
				// we will *not* handle socket timeouts (inactivity that occurs after the connection has been established).
				// we will *not* handle connection manager timeouts (time waiting for connection manager or connection pool).
				long endtime = System.currentTimeMillis();
				this.numRetries++;
				this.timeout = this.timeout.plus(Duration.ofSeconds(30));
				logger.info("Failed to check {} after {} due to cause \"{}\", but will retry {} more time(s) with a longer timeout of {}.", this.uri, Duration.ofMillis(endtime - startTime), e.getMessage(), MAX_NUM_RETRIES - this.numRetries, this.timeout);
				done = this.numRetries > MAX_NUM_RETRIES;
				if (done)
				{
					logger.error("Connection Error!", e);
					throw new Exception("Connection timed out. Number of retries ("+this.numRetries+") exceeded. No further attempts will be made.", e);
				}
			}
			catch (IOException e) {
				logger.error("While trying to connect to {} an exception was caught: {}", this.uri, e.getMessage());
				throw e;
			}
		}
		linkCheckInfo.setNumRetries(this.numRetries);
		if (responseBody != null)
		{
			if (urlConnection.getResponseMessage() != null)
			{
				switch (urlConnection.getResponseCode())
				{
					// If response was OK, check the body to make sure the string we are checking for is there.
					case HttpURLConnection.HTTP_OK:
					{
						if (responseBody.contains(this.keyword))
						{
							// SPECIAL CASE FOR KEGG: Kegg will return "OK 200" AND the keyword, even if the keyword is not found.
							// The exact text to check for it: "No such data was found."
							//if (referenceDatabaseName.toLowerCase().contains("kegg"))
							// KEGG URLs have a domain name of genome.jp
							if (this.uri.getHost().toLowerCase().contains("genome.jp"))
							{
								if (responseBody.toLowerCase().contains("No such data was found.".toLowerCase()))
								{
									linkCheckInfo.setKeywordFound(false);
								}
								else
								{
									linkCheckInfo.setKeywordFound(true);
								}
							}
							else
							{
								// then the link is OK.
								linkCheckInfo.setKeywordFound(true);
							}
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

	private String getContent(HttpURLConnection urlConnection) throws IOException {
		BufferedReader bufferedReader =
			new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
		return bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
	}
}
