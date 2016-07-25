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
					// .addBinaryBody("file", new
					// File("/home/sshorser/workspaces/reactome/new_add_links/AddLinks/uniprot_ids.txt"))
					// .addTextBody("file", text, ContentType.TEXT_PLAIN)
					.addPart("format", new StringBody("tab", ContentType.MULTIPART_FORM_DATA))
					.addPart("from", new StringBody("ACC+ID", ContentType.MULTIPART_FORM_DATA))
					.addPart("to", new StringBody("ACC", ContentType.MULTIPART_FORM_DATA)).build();
			// post.setEntity(new UrlEncodedFormEntity(parameters, "UTF-8"));
			post.setEntity(attachment);
			// post.setHeader("format", "tab");
			// post.setHeader("from","ACC+ID");
			// post.setHeader("to","ACC");

			try (CloseableHttpClient client = HttpClients.createDefault();
					CloseableHttpResponse response = client.execute(post);)
			{

				logger.debug("Status: {}", response.getStatusLine());

				//The uniprot response will contain the actual URL for the data in the "Location" header.
				String location = response.getHeaders("Location")[0].getValue();
				logger.debug("Location of data: {}",location);
				URIBuilder builder = new URIBuilder();
				
				String[] parts = location.split("\\?");
				builder.setScheme("http");
				builder.setHost(parts[0].replace("http://", ""));
				
				String[] params = parts[1].split("&");
				
				for(String s : params)
				{
					String[] nameAndValue = s.split("=");
					builder.addParameter(nameAndValue[0], nameAndValue[1]);
				}
				
				HttpGet get = new HttpGet(builder.build());
				try (CloseableHttpClient client2 = HttpClients.createDefault();
						CloseableHttpResponse response2 = client.execute(get);)
				{
					Path path = Paths.get(new URI("file://" + this.destination));
					Files.createDirectories(path.getParent());
	
					// byte buffer[] = new byte[1024];
					// StringBuilder sb = new StringBuilder();
					// while (-1 != response.getEntity().getContent().read(buffer))
					// {
					// sb.append(new String(buffer));
					// }
					//
					Files.write(path, EntityUtils.toByteArray(response2.getEntity()));
					// Files.write(path, sb.toString().getBytes());
					// client.close();
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
}
