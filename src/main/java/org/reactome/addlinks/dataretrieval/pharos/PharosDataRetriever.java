package org.reactome.addlinks.dataretrieval.pharos;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
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

	private static final String APPLICATION_JSON = "application/json";

	// Pharos requested that we use the DEV endpoint for large requests, since it's not used by
	// as many users, and I guess they don't want us slowing down the main endpoint.
	private static String pharosEndpoint = "https://ncatsidg-dev.appspot.com/graphql";
//	private static String pharosEndpoint = "https://pharos-api.ncats.io/graphql";

	// Some examples of testing Pharos' GraphQL endpoint.
	//curl 'https://pharos-api.ncats.io/graphql' -H 'Accept-Encoding: gzip, deflate, br' -H 'Content-Type: application/json' -H 'Accept: application/json' -H 'Connection: keep-alive' -H 'DNT: 1' -H 'Origin: https://pharos-api.ncats.io' --data-binary '{"query":"query {\n  targets(filter: {facets: [{facet:\"Data Source\" values:[\"UniProt\"]}]}) {\n    count\n    filter\n    {\n      term\n    \tfacets {\n        facet\n        values\n      }\n    }\n    facets {\n      facet\n      values {\n        name\n        value\n      }\n    }\n    targets(top:100 skip:2 ) {\n      name\n      synonyms {\n        name\n        value\n      }\n    }\n  }\n}"}' --compressed
	// curl 'https://ncatsidg-dev.appspot.com/graphql' -H 'Accept-Encoding: gzip, deflate, br' -H 'Content-Type: application/json' -H 'Accept: application/json' -H 'Connection: keep-alive' -H 'DNT: 1' -H 'Origin: https://ncatsidg-dev.appspot.com' --data-binary '{"query":"query {  targets {    targets(top:100) {      uniprot    }  }}\n"}' --compressed
	private static int batchSize = 1000;

	@Override
	protected void doHttpDownload(Path path, HttpClientContext context) throws Exception, HttpHostConnectException, IOException
	{
		int numRequests = 0;
		boolean moreRecords = true;
		try(FileWriter fw = new FileWriter(this.destination))
		{
			while (moreRecords)
			{
				// TODO: In the future, make FileRetriever in release-common-lib more generic so that it deals with HttpEntityEnclosingRequestBase
				// and let subclasses optionally inject their own request object (HttpGet or HttpPost).
				// But there just isn't time before Release 77 for that sort of elegant solution, so yes, there *is* some duplication of effort in this
				// doHttpDownload method and the method it overrode.
				HttpPost post = new HttpPost(PharosDataRetriever.pharosEndpoint);


				// We will need to keep making posts until we have them all. Requests will get 100 items at a time.
				RequestConfig config = RequestConfig.copy(RequestConfig.DEFAULT)
											.setConnectTimeout(1000 * (int)this.timeout.getSeconds())
											.setSocketTimeout(1000 * (int)this.timeout.getSeconds())
											.setConnectionRequestTimeout(1000 * (int)this.timeout.getSeconds())
											.setContentCompressionEnabled(true)
											.build();
				post.setConfig(config);
				// update the query: we just increment the "skip" value each time through the loop, until we send a query that returns an empty list.
				// We *could* use the "count" value that comes back in the first query to request one huge batch, but I think this is nicer to their server.
				String updatedQuery = this.getQuery()
										.replace(PharosDataRetriever.TOP_AMOUNT_TOKEN, Integer.toString(PharosDataRetriever.batchSize))
										.replace(PharosDataRetriever.SKIP_AMOUNT_TOKEN, Integer.toString(numRequests * PharosDataRetriever.batchSize));
				StringEntity stringEntity = new StringEntity(updatedQuery);
				stringEntity.setContentType(PharosDataRetriever.APPLICATION_JSON);
				post.setEntity(stringEntity);
				post.setHeader(HttpHeaders.ACCEPT, PharosDataRetriever.APPLICATION_JSON);
				post.setHeader(HttpHeaders.CONTENT_TYPE, PharosDataRetriever.APPLICATION_JSON);
				int retries = this.numRetries;
				boolean done = retries + 1 <= 0;
				while (!done)
				{
					try (CloseableHttpClient client = HttpClients.createDefault();
						CloseableHttpResponse response = client.execute(post, context); )
					{
						int statusCode = response.getStatusLine().getStatusCode();
						// If status code was not 200, we should print something so that the users know that an unexpected response was received.
						if (statusCode != HttpStatus.SC_OK)
						{
							if (String.valueOf(statusCode).startsWith("4") || String.valueOf(statusCode).startsWith("5"))
							{
								logger.error("Response code was 4xx/5xx: {}, Status line is: {}", statusCode, response.getStatusLine());
							}
							else
							{
								logger.warn("Response was not \"200\". It was: {}", response.getStatusLine());
							}
						}
						else
						{
							// result could be flattened with jq using the jq expression: ".data.targets.targets[].uniprot"
							String jsonResponse = EntityUtils.toString(response.getEntity());
							JSONObject jsonObj = new JSONObject(jsonResponse);

							int numProcessed = processJSONArray(fw, jsonObj);
							moreRecords = numProcessed >= 1;
							logger.info("Completed request #{}", numRequests);
							numRequests++;
						}
						done = true;
					}
					catch (ConnectTimeoutException e)
					{
						// we will only be retrying the connection timeouts, defined as the time required to establish a connection.
						// we will not handle socket timeouts (inactivity that occurs after the connection has been established).
						// we will not handle connection manager timeouts (time waiting for connection manager or connection pool).
						e.printStackTrace();
						logger.info("Failed due to ConnectTimeout, but will retry {} more time(s).", retries);
						retries--;
						done = retries + 1 <= 0;
						if (done)
						{
							throw new Exception("Connection timed out. Number of retries ("+this.numRetries+") exceeded. No further attempts will be made.", e);
						}
					}
					catch (HttpHostConnectException e)
					{
						logger.error("Could not connect to host {} !",post.getURI().getHost());
						e.printStackTrace();
						throw e;
					}
					catch (IOException e) {
						logger.error("Exception caught: {}",e.getMessage());
						throw e;
					}
				}
			}
		}
	}

	/**
	 * Implementors must return a specific query to be executed by this class' doHttpDownload
	 * @return
	 */
	protected abstract String getQuery();

	/**
	 * Implementors must process an array of JSON objects.
	 * @param fw A FileWriter - this can be used to write data to the output file.
	 * @param jsonObj - the JSON object to process.
	 * @return The number of items processed.
	 * @throws IOException
	 */
	protected abstract int processJSONArray(FileWriter fw, JSONObject jsonObj) throws IOException;

}
