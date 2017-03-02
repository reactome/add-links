package org.reactome.addlinks.dataretrieval;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthenticatingFileRetriever extends FileRetriever {

	//private static final Logger logger = LogManager.getLogger();
	
	private String userName;
	private String password;

	@Override
	protected void downloadData() throws Exception {
		HttpGet get = new HttpGet(this.uri);
		Credentials creds = new UsernamePasswordCredentials(userName, password);
		CredentialsProvider credProvider = new BasicCredentialsProvider();
		AuthScope authScope = new AuthScope(uri.getHost(), uri.getPort());
		credProvider.setCredentials(authScope, creds);
		HttpClientContext context = HttpClientContext.create();
		context.setCredentialsProvider(credProvider);
		try( CloseableHttpClient client = HttpClients.createDefault();
			CloseableHttpResponse response = client.execute(get,context) )
		{
			Path path = Paths.get(new URI("file://"+this.destination));
			Files.write(path, EntityUtils.toByteArray(response.getEntity()));
		} catch (IOException | URISyntaxException e) {
			logger.error("Exception caught: {}",e.getMessage());
			throw e;
		}
	}
	
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	
	
}
