package org.reactome.addlinks.dataretrieval;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UniprotFileRetreiver extends FileRetriever
{
	private static final Logger logger = LogManager.getLogger();

	private String mapFromDb="";
	private String mapToDb="";
	
	@Override
	public void downloadData()
	{
		HttpPost post = new HttpPost(this.uri);
		try
		{
			logger.debug("URI: {}", post.getURI().toURL());
		} catch (MalformedURLException e2)
		{
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		try
		{
			HttpEntity attachment = MultipartEntityBuilder.create()
					.addBinaryBody("file",
							new FileInputStream(new File(
									"/home/sshorser/workspaces/reactome/new_add_links/AddLinks/uniprot_ids.txt")),
							ContentType.TEXT_PLAIN, "uniprot_ids.txt")
					.addPart("format", new StringBody("tab", ContentType.MULTIPART_FORM_DATA))
					.addPart("from", new StringBody(this.mapFromDb, ContentType.MULTIPART_FORM_DATA))
					.addPart("to", new StringBody(this.mapToDb, ContentType.MULTIPART_FORM_DATA)).build();
			post.setEntity(attachment);

			try (CloseableHttpClient postClient = HttpClients.createDefault();
					CloseableHttpResponse postResponse = postClient.execute(post);)
			{

				logger.debug("Status: {}", postResponse.getStatusLine());

				//The uniprot response will contain the actual URL for the data in the "Location" header.
				String location = postResponse.getHeaders("Location")[0].getValue();
				logger.debug("Location of data: {}",location);
				URIBuilder builder = new URIBuilder();
				
				String[] parts = location.split("\\?");
				String[] schemeAndHost = parts[0].split("://");
				builder.setScheme(schemeAndHost[0]);
				builder.setHost(schemeAndHost[1]);
				
				if (parts.length>1)
				{
					// If the Location header string contains query information, we need to properly reformat that before requesting it. 
					String[] params = parts[1].split("&");
					for(String s : params)
					{
						String[] nameAndValue = s.split("=");
						builder.addParameter(nameAndValue[0], nameAndValue[1]);
					}
				}
				else
				{
					//Add .tab to get table.
					builder.setHost(builder.getHost() + ".tab");
				}
				
				HttpGet get = new HttpGet(builder.build());
				try (CloseableHttpClient getClient = HttpClients.createDefault();
						CloseableHttpResponse getResponse = postClient.execute(get);)
				{
					Path path = Paths.get(new URI("file://" + this.destination));
					Files.createDirectories(path.getParent());
					Files.write(path, EntityUtils.toByteArray(getResponse.getEntity()));
				}	

			}
		} catch (URISyntaxException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (ClientProtocolException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void setMapFromDb(String mapFromDb)
	{
		this.mapFromDb = mapFromDb;
	}

	public void setMapToDb(String mapToDb)
	{
		this.mapToDb = mapToDb;
	}
}
