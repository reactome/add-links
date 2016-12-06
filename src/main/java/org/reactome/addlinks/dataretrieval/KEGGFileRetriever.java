package org.reactome.addlinks.dataretrieval;

import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Retrieves data from http://rest.kegg.jp/get/hsa:$kegg_gene_id
 * Then extract the DBLINKS section from the result.
 * @author sshorser
 *
 */
public class KEGGFileRetriever extends FileRetriever
{
	private static final Logger logger = LogManager.getLogger();

	private List<String> identifiers;
	
	@Override
	protected void downloadData() throws Exception
	{
		// Could these API requests be made in parallel?
		for (int i = 0; i < this.identifiers.size(); i+=10)
		{
			String identifiersForRequest = "";
			// KEGG only accepts 10 identifiers at a time.
			for (int j = i; j < i+10; j++ )
			{
				// The HSA is because we're only supposed to map Human identifiers
				identifiersForRequest += ("hsa:"+this.identifiers.get(j) + "+");
			}
			
			URIBuilder builder = new URIBuilder();
			// Append the list of identifiers to the URL string
			builder.setHost(this.uri.getHost())
					.setPath(this.uri.getPath() + identifiersForRequest)
					.setScheme(this.uri.getScheme());
			HttpGet get = new HttpGet(builder.build());
			logger.debug("URI: "+get.getURI());
			
			try (CloseableHttpClient getClient = HttpClients.createDefault();
					CloseableHttpResponse getResponse = getClient.execute(get);)
			{

				switch (getResponse.getStatusLine().getStatusCode())
				{
				
					case HttpStatus.SC_OK:
						break;
					case HttpStatus.SC_NOT_FOUND:
						break;
					case HttpStatus.SC_BAD_GATEWAY:
						break;
					default:
						logger.error("Unexpected response code: {} ; full response message: {}", getResponse.getStatusLine().getStatusCode(), getResponse.getStatusLine().toString());
						break;
				}
			}
		}
		
	}
	
	public void setIdentifiers(List<String> identifiers)
	{
		this.identifiers = identifiers;
	}
}
