package org.reactome.addlinks.dataretrieval;	

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.sftp.SftpClientFactory;
import org.apache.commons.vfs2.provider.sftp.SftpFileProvider;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystem;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class FileRetriever implements DataRetriever {

	protected URI uri;
	protected String destination;
	protected Duration maxAge;
	private Duration timeout = Duration.ofSeconds(30);
	private int numRetries = 1;
	protected String retrieverName;
	protected Logger logger;
	
	
	public FileRetriever()
	{
		this(null);
	}
	
	/**
	 * This constructor takes a string that will be used to name the logging output file.
	 * 
	 * @param retrieverName - the name of this File Retriever - the log file produced by this class will be named "${retrieverName}.log"
	 */
	public FileRetriever(String retrieverName)
	{
		//logger.trace("Setting retrieverName to {}", retrieverName);
		this.setRetrieverName(retrieverName);
		this.logger = this.createLogger(this.retrieverName, "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG, this.logger, "Data Retriever");
	}
	
	@Override
	public void fetchData() throws Exception 
	{
		if (this.uri == null)
		{
			throw new RuntimeException("You must provide a URI from which the file will be downloaded!");
		}
		else if (this.destination.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a destination to which the file will be downloaded!");
		}
		//Before fetching anything, we need to check to see if the file already exists.
		Path pathToFile = Paths.get(this.destination);
		if (Files.exists(pathToFile))
		{
			BasicFileAttributes attributes = Files.readAttributes(pathToFile, BasicFileAttributes.class);
			
			Instant fileCreateTime = attributes.creationTime().toInstant();
			Instant now = Instant.now();
			//If the file is older than the maxAge...
			if (fileCreateTime.isBefore( now.minus(this.maxAge) ))
			{
				//TODO: Option to save back-ups of old files.
				logger.debug("File {} is older than allowed amount ({}) so it will be downloaded again.",this.destination,this.maxAge);
				downloadData();
				logger.debug("Download is complete.");
			}
			else
			{
				logger.debug("File {} is not older than allowed amount ({}) so it will not be downloaded.",this.destination,this.maxAge);
			}
		}
		else
		{
			logger.debug("File {} does not exist and must be downloaded.", pathToFile);
			//if file does not exist, get it from the URL.
			downloadData();
		}
		
		// Print some basic file stats.
		if (Files.exists(pathToFile))
		{
			if (Files.isReadable(pathToFile))
			{
				BasicFileAttributes attribs = Files.readAttributes(pathToFile, BasicFileAttributes.class);
				logger.info("File Info: Name: {}, Size: {}, Created: {}, Modified: {}",this.destination,attribs.size(), attribs.creationTime(), attribs.lastModifiedTime());
			}
			else
			{
				logger.error("File {} is not readable!", pathToFile);
			}
		}
		else
		{
			// If something failed during the data retrieval, the file might not exist. If that happens, let the user know.
			logger.error("File \"{}\" still does not exist after executing the file retriever!", pathToFile);
		}
	}

	protected void downloadData() throws Exception
	{
		logger.trace("Scheme is: "+this.uri.getScheme());
		Path path = Paths.get(new URI("file://"+this.destination));
		Files.createDirectories(path.getParent());
		if (this.uri.getScheme().equals("http"))
		{
			
			doHttpDownload(path);
		}
		else if (this.uri.getScheme().equals("ftp"))
		{
			doFtpDownload();
		}
		else
		{
			throw new UnsupportedSchemeException("URI "+this.uri.toString()+" uses an unsupported scheme: "+this.uri.getScheme());
		}
	}

	protected void doFtpDownload() throws SocketException, IOException, Exception, FileNotFoundException
	{
		doFtpDownload(null, null);
	}
	
	protected void doFtpDownload(String user, String password) throws SocketException, IOException, Exception, FileNotFoundException
	{
		user = user == null || user.trim().equals("") ? "anonymous" : user;
		password = password == null || password.trim().equals("") ? "" : password;
		FTPClient client = new FTPClient();

		client.connect(this.uri.getHost());
		client.login(user, password);
		logger.debug("connect/login reply code: {}",client.getReplyCode());
		client.setFileType(FTP.BINARY_FILE_TYPE);
		client.setFileTransferMode(FTP.COMPRESSED_TRANSFER_MODE);
		InputStream inStream = client.retrieveFileStream(this.uri.getPath());
		//Should probably have more/better reply-code checks.
		logger.debug("retreive file reply code: {}",client.getReplyCode());
		if (client.getReplyString().matches("^5\\d\\d.*") || (client.getReplyCode() >= 500 && client.getReplyCode() < 600) )
		{
			String errorString = "5xx reply code detected (" + client.getReplyCode() + "), reply string is: "+client.getReplyString();
			logger.error(errorString);
			throw new Exception(errorString);
		}
		writeInputStreamToFile(inStream);
		
		client.logout();
		client.disconnect();
	}

	protected void writeInputStreamToFile(InputStream inStream) throws IOException, FileNotFoundException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int b = inStream.read();
		while (b!=-1)
		{
			baos.write(b);
			b = inStream.read();
		}

		FileOutputStream file = new FileOutputStream(this.destination);
		baos.writeTo(file);
		file.flush();

		baos.close();
		inStream.close();
		file.close();
	}

	
	protected void doHttpDownload(Path path) throws HttpHostConnectException, IOException, Exception
	{
		this.doHttpDownload(path, HttpClientContext.create());
	}
	
	protected void doHttpDownload(Path path, HttpClientContext context) throws Exception, HttpHostConnectException, IOException
	{
		HttpGet get = new HttpGet(this.uri);
		//Need to multiply by 1000 because timeouts are in milliseconds.
		RequestConfig config = RequestConfig.copy(RequestConfig.DEFAULT)
											.setConnectTimeout(1000 * (int)this.timeout.getSeconds())
											.setSocketTimeout(1000 * (int)this.timeout.getSeconds())
											.setConnectionRequestTimeout(1000 * (int)this.timeout.getSeconds()).build();
		
		get.setConfig(config);
		
		int retries = this.numRetries;
		boolean done = retries + 1 <= 0;
		while(!done)
		{
			try( CloseableHttpClient client = HttpClients.createDefault();
				CloseableHttpResponse response = client.execute(get, context) )
			{
				Files.write(path, EntityUtils.toByteArray(response.getEntity()));
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
	}

	public Duration getMaxAge()
	{
		return this.maxAge;
	}
	
	public URI getDataURL()
	{
		return this.uri;
	}
	
	@Override
	public void setDataURL(URI uri) {
		this.uri = uri;
	}

	@Override
	public void setFetchDestination(String destination) {
		this.destination = destination;
	}

	@Override
	public void setMaxAge(Duration age) {
		this.maxAge = age;
	}

	public void setNumRetries(int i)
	{
		this.numRetries = i;
	}
	
	public void setTimeout(Duration timeout)
	{
		this.timeout = timeout;
	}

	@Override
	public void setRetrieverName(String retrieverName)
	{
		this.retrieverName = retrieverName;
	}
	
	public String getRetrieverName()
	{
		return this.retrieverName;
	}
}

