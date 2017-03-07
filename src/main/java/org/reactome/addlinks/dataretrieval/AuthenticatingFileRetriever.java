package org.reactome.addlinks.dataretrieval;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.impl.client.BasicCredentialsProvider;

public class AuthenticatingFileRetriever extends FileRetriever
{
	private String userName;
	private String password;

	public AuthenticatingFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}
	
	public AuthenticatingFileRetriever()
	{
		super();
	}
	
	@Override
	protected void downloadData() throws Exception
	{
		HttpClientContext context = createAuthenticatedContext();
		logger.trace("Scheme is: "+this.uri.getScheme());
		Path path = Paths.get(new URI("file://"+this.destination));
		Files.createDirectories(path.getParent());
		if (this.uri.getScheme().equals("http"))
		{
			doHttpDownload(path, context);
		}
		else if (this.uri.getScheme().equals("ftp"))
		{
			doFtpDownload(this.userName, this.password);
		}
		else
		{
			throw new UnsupportedSchemeException("URI "+this.uri.toString()+" uses an unsupported scheme: "+this.uri.getScheme());
		}

	}


	private HttpClientContext createAuthenticatedContext()
	{
		Credentials creds = new UsernamePasswordCredentials(userName, password);
		CredentialsProvider credProvider = new BasicCredentialsProvider();
		AuthScope authScope = new AuthScope(uri.getHost(), uri.getPort());
		credProvider.setCredentials(authScope, creds);
		HttpClientContext context = HttpClientContext.create();
		context.setCredentialsProvider(credProvider);
		return context;
	}
	
	public void setUserName(String userName)
	{
		this.userName = userName;
	}
	public void setPassword(String password)
	{
		this.password = password;
	}
	
	
}
