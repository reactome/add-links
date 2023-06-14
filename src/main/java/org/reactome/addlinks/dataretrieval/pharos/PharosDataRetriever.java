package org.reactome.addlinks.dataretrieval.pharos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.reactome.release.common.dataretrieval.FileRetriever;

/**
 * Pharos data is accessed via a GraphQL API: https://pharos.nih.gov/api
 * Data will be downloaded and then stored into simple text files.
 *
 * @author sshorser
 *
 */
public abstract class PharosDataRetriever extends FileRetriever
{
	protected static final String SKIP_AMOUNT_TOKEN = "##SKIPAMOUNT##";

	protected static final String TOP_AMOUNT_TOKEN = "##TOPAMOUNT##";

	// Pharos requested that we use the DEV endpoint for large requests, since it's not used by
	// as many users, and I guess they don't want us slowing down the main endpoint.
//	private static String pharosEndpoint = "https://ncatsidg-dev.appspot.com/graphql";
//	private static String pharosEndpoint = "https://pharos-api.ncats.io/graphql"; // the non-dev endpoint.

	// Some examples of testing Pharos' GraphQL endpoint.
	// curl 'https://pharos-api.ncats.io/graphql' -H 'Accept-Encoding: gzip, deflate, br' -H 'Content-Type: application/json' -H 'Accept: application/json' -H 'Connection: keep-alive' -H 'DNT: 1' -H 'Origin: https://pharos-api.ncats.io' --data-binary '{"query":"query {\n  targets(filter: {facets: [{facet:\"Data Source\" values:[\"UniProt\"]}]}) {\n    count\n    filter\n    {\n      term\n    \tfacets {\n        facet\n        values\n      }\n    }\n    facets {\n      facet\n      values {\n        name\n        value\n      }\n    }\n    targets(top:100 skip:2 ) {\n      name\n      synonyms {\n        name\n        value\n      }\n    }\n  }\n}"}' --compressed
	// curl 'https://ncatsidg-dev.appspot.com/graphql' -H 'Accept-Encoding: gzip, deflate, br' -H 'Content-Type: application/json' -H 'Accept: application/json' -H 'Connection: keep-alive' -H 'DNT: 1' -H 'Origin: https://ncatsidg-dev.appspot.com' --data-binary '{"query":"query {  targets {    targets(top:100) {      uniprot    }  }}\n"}' --compressed
	private static int batchSize = 1000;

	public PharosDataRetriever(String retrieverName)
	{
		super(retrieverName);
	}

	public PharosDataRetriever()
	{
		super(null);
	}

	/**
	 * Performs a download of a file via http.
	 * @param path This is where the file gets downloaded to.
	 * @throws IOException - Could be caused by an error when creating the output file.
	 * @throws Exception - Could happen if the number of retries is reached and still no data has been retrieved
	 */
	@Override
	protected void doHttpDownload(Path path) throws Exception
	{
		try (BufferedWriter writer = Files.newBufferedWriter(path))
		{
			final int timeoutInMilliseconds = 1000 * (int)this.timeout.getSeconds();
			boolean moreRecords = true;
			int numRequests = 0;
			while (moreRecords)
			{
				// TODO: In the future, make FileRetriever in release-common-lib more generic so that it deals with HttpEntityEnclosingRequestBase
				// and let subclasses optionally inject their own request object (HttpGet or HttpPost).
				// But there just isn't time before Release 77 for that sort of elegant solution, so yes, there *is* some duplication of effort in this
				// doHttpDownload method and the method it overrode.
				int retries = this.numRetries;
				boolean done = retries < 0;
				while (!done)
				{
					try
					{
						HttpURLConnection urlConnection = createPOSTRequest(timeoutInMilliseconds, numRequests);
						// If status code was not 200, we should print something so that the users know that an unexpected response was received.
						if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK)
						{
							reportNotOKResponse(urlConnection);
						}
						else
						{
							String content = getContent(urlConnection);
							JSONObject jsonObj = new JSONObject(content);
							int numProcessed = processJSONArray(writer, jsonObj);
							moreRecords = numProcessed >= 1;
							logger.info("Completed request #{}", numRequests);
							numRequests++;
						}
						done = true;
					}
					catch (SocketTimeoutException e)
					{
						// we will only be retrying the connection timeouts, defined as the time required to establish a connection.
						// we will not handle socket timeouts (inactivity that occurs after the connection has been established).
						// we will not handle connection manager timeouts (time waiting for connection manager or connection pool).
						e.printStackTrace();
						logger.info("Failed due to ConnectTimeout, but will retry {} more time(s).", retries);
						retries--;
						done = retries < 0;
						if (done)
						{
							// TODO: implement better custom exceptions in release-common-lib
							throw new Exception("Connection timed out. Number of retries ("+this.numRetries+") exceeded. No further attempts will be made.", e);
						}
					}
//					catch (PharosDataException e)
//					{
//						logger.error("There was a problem with data from Pharos: " + e.getMessage(), e);
//						throw e;
//					}
//					catch (JSONException e)
//					{
//						logger.error("There was a problem with the JSON: {}", jsonResponse);
//						throw new PharosDataException("There was a problem with the JSON from Pharos: " + e.getMessage());
//					}
					catch (IOException e)
					{
						logger.error("IOException caught: {}", e.getMessage());
						throw e;
					}
				}
			}
		}
	}

	/**
	 * Reports on a not-OK (non-200) HTTP response. Nothing will be logged if response is 200.
	 * @param urlConnection - HttpURLConnection.
	 */
	private void reportNotOKResponse(HttpURLConnection urlConnection) throws IOException {
		int statusCode = urlConnection.getResponseCode();
		if (String.valueOf(statusCode).startsWith("4") || String.valueOf(statusCode).startsWith("5"))
		{
			logger.error("Response code was 4xx/5xx: {}", urlConnection.getResponseMessage());
		}
		else
		{
			logger.warn("Response was not \"200\". It was: {}", urlConnection.getResponseMessage());
		}
	}

	/**
	 * Creates the POST request to send to Pharos.
	 * @param timeoutInMilliseconds - timeout value, in milliseconds.
	 * @param requestCounter - what request # is this? Used to control window of records from Pharos.
	 * @return - an HttpURLConnection object for the post request
	 * @throws IOException - thrown if the URLConnection can not be opened, the request method can't be set as POST,
	 * or query string can not be submitted.
	 */
	private HttpURLConnection createPOSTRequest(final int timeoutInMilliseconds, int requestCounter) throws IOException
	{
		HttpURLConnection urlConnection = getHttpURLConnection();
		urlConnection.setConnectTimeout(timeoutInMilliseconds);
		urlConnection.setReadTimeout(timeoutInMilliseconds);
		// We will need to keep making posts until we have them all. Requests will get 100 items at a time.
		urlConnection.setRequestMethod("POST");
		urlConnection.setDoOutput(true);
		urlConnection.setRequestProperty("Accept", "application/json");
		urlConnection.setRequestProperty("Accept-Encoding", "gzip");
		urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");
		// update the query: we just increment the "skip" value each time through the loop, until we send a query that
		// returns an empty list.  We *could* use the "count" value that comes back in the first query to request one
		// huge batch, but I think this is nicer to their server.
		String updatedQuery = this.getQuery()
			.replace(PharosDataRetriever.TOP_AMOUNT_TOKEN, Integer.toString(PharosDataRetriever.batchSize))
			.replace(PharosDataRetriever.SKIP_AMOUNT_TOKEN, Integer.toString(requestCounter * PharosDataRetriever.batchSize));
		byte[] postDataBytes = updatedQuery.getBytes();
		urlConnection.getOutputStream().write(postDataBytes);

		return urlConnection;
	}

	/**
	 * Implementors must return a specific query to be executed by this class' doHttpDownload
	 * @return a string that will be used as graphql query.
	 */
	protected abstract String getQuery();

	/**
	 * Implementors must process an array of JSON objects and write it to a file.
	 * @param writer A FileWriter - this can be used to write data to the output file.
	 * @param jsonObj - the JSON object to process.
	 * @return The number of items processed.
	 * @throws IOException Thrown when there is a problem writing the file.
	 * @throws PharosDataException - Thrown when there is a problem with the data from Pharos, <em>possibly</em> caused by problems with JSON.
	 */
	protected abstract int processJSONArray(BufferedWriter writer, JSONObject jsonObj) throws IOException, PharosDataException;

	private String getContent(HttpURLConnection urlConnection) throws IOException {
		BufferedReader bufferedReader =
			new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
		return bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
	}
}
