package org.reactome.addlinks;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility methods for querying the service endpoints at http://identifiers.org
 * @author sshorser
 *
 */
public class IdentifiersDotOrgUtil
{

	private static final String ACCESS_URL_TOKEN = "urlPattern";
	private static final String RESOURCE_IDENTIFIER_ENDPOINT = "https://registry.api.identifiers.org/restApi/resources/search/findByMirId?mirId=";
	private static final Logger logger = LogManager.getLogger();

	/**
	 * Gets the accessURL for a resource identifier from identifiers.org. Resource identifiers usually have a prefix and identifier string.
	 * For example, "MIR:00100374" refers to the resource "HAPMAP". If you request <code>https://identifiers.org/rest/resources/MIR:00100374</code>
	 * then you should get a response that looks like this: <pre>
{
  "id": "MIR:00100374",
  "accessURL": "https://hamap.expasy.org/unirule/{$id}",
  "info": "HAPMAP at Swiss Institute of Bioinformatics",
  "institution": "Swiss Institute of Bioinformatics, Geneva",
  "location": "Switzerland",
  "official": false,
  "localId": "MF_01400",
  "resourceURL": "https://hamap.expasy.org/"
}</pre>
     * This function will extract and return the value of "accessURL".
	 * @param resourceIdentifier - The identifier of a resource, as per identifiers.org. If you are uncertain what the resourceIdentifier is,
	 * you can search the identifiers.org website, or see a complete list of resources here:
	 * <a href="https://identifiers.org/rest/resources">https://identifiers.org/rest/resources</a>
	 * @return The accessURL of the resource identified by <code>resourceIdentifier</code>. In cases where identifiers.org does not return an accessURL, or where the
	 * identifier is deactivated/deprecated (according to identifiers.org), NULL will be returned.
	 */
	public static String getAccessUrlForResource(String resourceIdentifier)
	{
		String accessUrl = null;

		String url = RESOURCE_IDENTIFIER_ENDPOINT + resourceIdentifier;

		try
		{
			HttpGet get = new HttpGet(new URI(url));
			try(CloseableHttpClient client = HttpClients.createDefault();
				CloseableHttpResponse response = client.execute(get))
			{
				int statusCode = response.getStatusLine().getStatusCode();
				String responseString = EntityUtils.toString(response.getEntity());
				switch (statusCode)
				{
					case HttpStatus.SC_OK:
						JsonReader reader = Json.createReader(new StringReader(responseString));
						JsonObject responseObject = reader.readObject();
						String responseAccessURL = responseObject.getString(ACCESS_URL_TOKEN).toString().replaceAll("\"", "");
						// Before accepting the AccessURL from the response,
						// check that the identifier is not deprecated. If identifiers.org
						// deprecates an identifier, the AccessURL in the response
						// will look something like this: https://registry.identifiers.org/deprecation/resources/MIR:00100113/{$id}
						// (this example uses the deactivated/deprecated Rhea identifier that caused a problem
						// during Releaes 77)
						// But the HTTP Response code is 200 so we can't just blindly replace our Access URL with the
						// one from identifiers.org.
						if (responseAccessURL.contains("registry.identifiers.org/deprecation"))
						{
							logger.error("De-activated/deprecated identifier detected for resource identifier {}\nYou should check on identifiers.org to find a new resource identifier for your resource.", resourceIdentifier);
						}
						else
						{
							// Leave the {$id} in the URL and let the caller replace it.
							accessUrl = responseAccessURL;
						}
						break;
					case HttpStatus.SC_NOT_FOUND:
						// For a 404, we want to tell the user specifically what happened.
						// This is the error code for invalid identifier strings.
						// It should also be accompanied by the message: "Required {prefix}:{identifier}"
						logger.error("Got 404 from identifiers.org for the resource identifier request: \"{}\". You might want to verify that the resource identifier you requested is correct.", url);
						break;
					default:
						logger.error("Non-200 status code: {} Response String is: {}", statusCode, responseString);
						break;
				}
			}
			catch (IOException e)
			{
				logger.error(e);
				e.printStackTrace();
			}
		}
		catch (URISyntaxException e)
		{
			logger.error(e);
			e.printStackTrace();
		}

		return accessUrl;
	}
}
