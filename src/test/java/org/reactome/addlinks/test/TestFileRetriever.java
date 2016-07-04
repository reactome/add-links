/**
 * 
 */
package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.dataretrieval.DataRetriever;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import static org.mockito.Matchers.*;

/**
 * @author sshorser
 *
 */
@PowerMockIgnore({"javax.management.*","javax.net.ssl.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({ java.net.URI.class,
				org.apache.commons.net.ftp.FTPClient.class,
				org.reactome.addlinks.dataretrieval.FileRetriever.class,
				org.apache.http.impl.client.HttpClients.class })
public class TestFileRetriever {

	private static final String MESSAGE_CONTENT = "this is a test";
	
	@Mock
	FTPClient mockFtpClient ;
	
	@Mock
	CloseableHttpClient mockClient;
	
	@Mock
	CloseableHttpResponse mockResponse;

	
	HttpEntity entity = new ByteArrayEntity(MESSAGE_CONTENT.getBytes());
	/**
	 * Test method for {@link org.reactome.addlinks.dataretrieval.FileRetriever#fetchData()}.
	 * @throws Exception 
	 */
	@Test
	public void testFetchData() throws Exception {
		DataRetriever retriever = new FileRetriever();
		//retrieve google - it should be pretty easy.
		URI uri = new URI("http://www.google.com");
		retriever.setDataURL(uri);
		
		Mockito.when(mockResponse.getEntity()).thenReturn(entity);

		Mockito.when(mockClient.execute(any(HttpUriRequest.class))).thenReturn(mockResponse);
		
		PowerMockito.mockStatic(HttpClients.class);
		PowerMockito.when(HttpClients.createDefault()).thenReturn(mockClient);
		
		String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
		retriever.setFetchDestination(dest);
		Duration age = Duration.of(5,ChronoUnit.SECONDS);
		retriever.setMaxAge(age);
		
		retriever.fetchData();
		assertTrue(Files.exists(Paths.get(dest)));
		
		//Sleep for two seconds, and then re-download because the file is stale
		Thread.sleep(Duration.of(2,ChronoUnit.SECONDS).toMillis());
		retriever.fetchData();
		assertTrue(Files.exists(Paths.get(dest)));
		//now set a longer maxAge.
		age = Duration.of(100,ChronoUnit.SECONDS);
		retriever.setMaxAge(age);
		// this time, the file will not be stale (because maxAge is larger) so nothing will be downloaded.
		retriever.fetchData();
		//check that the file exists.
		assertTrue(Files.exists(Paths.get(dest)));
	}

	
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
	}

	
	@Test
	public void testFetchFTPData() throws Exception
	{
		//set up the mock client
		//FTPClient mockFtpClient = PowerMockito.mock(FTPClient.class);
		PowerMockito.doNothing().when(mockFtpClient).connect(anyString());
		PowerMockito.when(mockFtpClient.login(anyString(),anyString())).thenReturn(true);
		PowerMockito.when(mockFtpClient.getReplyCode()).thenReturn(220);
		PowerMockito.when(mockFtpClient.getReplyString()).thenReturn("220 reply string");
		InputStream inStream = new ByteArrayInputStream("this is a test".getBytes());
		PowerMockito.when(mockFtpClient.retrieveFileStream(anyString())).thenReturn(inStream);
		//when creating a new FTPClient, always return the mockClient.
		PowerMockito.whenNew(FTPClient.class).withAnyArguments().thenReturn(mockFtpClient);
		PowerMockito.whenNew(FTPClient.class).withNoArguments().thenReturn(mockFtpClient);

		DataRetriever retriever = new FileRetriever();
		PowerMockito.mockStatic(URI.class);
		URI mockUri = PowerMockito.mock(URI.class);
		
		PowerMockito.doReturn("ftp").when(mockUri).getScheme();
		PowerMockito.when(mockUri.getHost()).thenReturn("testhost");
		PowerMockito.doReturn("/some/path").when(mockUri).getPath();
		
		retriever.setDataURL(mockUri);
		String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
		retriever.setFetchDestination(dest);
		Duration age = Duration.of(1,ChronoUnit.SECONDS);
		retriever.setMaxAge(age);
		
		retriever.fetchData();
		assertTrue(Files.exists(Paths.get(dest)));
	}
	
	@Test
	public void testFetchFTPErr() throws Exception
	{
		//set up the mock client
		//FTPClient mockFtpClient = PowerMockito.mock(FTPClient.class);
		PowerMockito.doNothing().when(mockFtpClient).connect(anyString());
		PowerMockito.when(mockFtpClient.login(anyString(),anyString())).thenReturn(true);
		PowerMockito.when(mockFtpClient.getReplyCode()).thenReturn(500);
		PowerMockito.when(mockFtpClient.getReplyString()).thenReturn("500 ERROR");
		InputStream inStream = new ByteArrayInputStream("this is a test".getBytes());
		PowerMockito.when(mockFtpClient.retrieveFileStream(anyString())).thenReturn(inStream);
		//when creating a new FTPClient, always return the mockClient.
		PowerMockito.whenNew(FTPClient.class).withAnyArguments().thenReturn(mockFtpClient);
		PowerMockito.whenNew(FTPClient.class).withNoArguments().thenReturn(mockFtpClient);

		DataRetriever retriever = new FileRetriever();
		PowerMockito.mockStatic(URI.class);
		URI mockUri = PowerMockito.mock(URI.class);
		
		PowerMockito.doReturn("ftp").when(mockUri).getScheme();
		PowerMockito.when(mockUri.getHost()).thenReturn("testhost");
		PowerMockito.doReturn("/some/path").when(mockUri).getPath();
		
		retriever.setDataURL(mockUri);
		String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
		retriever.setFetchDestination(dest);
		Duration age = Duration.of(1,ChronoUnit.SECONDS);
		retriever.setMaxAge(age);
		
		try
		{
			retriever.fetchData();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			assertTrue(e.getMessage().startsWith("500 ERROR"));
		}
	}
	
	@Test
	public void testHttpErr() throws ClientProtocolException, IOException, Exception
	{
		Mockito.when(mockClient.execute(any(HttpUriRequest.class))).thenThrow(new ClientProtocolException("MOCK Generic Error"));
		PowerMockito.mockStatic(HttpClients.class);
		PowerMockito.when(HttpClients.createDefault()).thenReturn(mockClient);
		
		DataRetriever retriever = new FileRetriever();
		//retrieve google - it should be pretty easy.
		URI uri = new URI("http://www.google.com");
		retriever.setDataURL(uri);
		String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
		retriever.setFetchDestination(dest);
		Duration age = Duration.of(1,ChronoUnit.SECONDS);
		retriever.setMaxAge(age);
		((FileRetriever)retriever).setNumRetries(0);
		((FileRetriever)retriever).setTimeout(Duration.of(1, ChronoUnit.SECONDS));
		try
		{
			retriever.fetchData();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			assertTrue(e.getMessage().contains("MOCK Generic Error"));
		}
	}
	
	@Test
	public void testHttpRetry() throws ClientProtocolException, IOException, Exception
	{
		Mockito.when(mockClient.execute(any(HttpUriRequest.class))).thenThrow(new ConnectTimeoutException("MOCK Timeout Error"));
		PowerMockito.mockStatic(HttpClients.class);
		PowerMockito.when(HttpClients.createDefault()).thenReturn(mockClient);
		
		DataRetriever retriever = new FileRetriever();
		//retrieve google - it should be pretty easy.
		URI uri = new URI("http://www.google.com");
		retriever.setDataURL(uri);
		String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
		retriever.setFetchDestination(dest);
		Duration age = Duration.of(1,ChronoUnit.SECONDS);
		retriever.setMaxAge(age);
		((FileRetriever)retriever).setNumRetries(1);
		((FileRetriever)retriever).setTimeout(Duration.of(1, ChronoUnit.SECONDS));
		try
		{
			retriever.fetchData();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			assertTrue(e.getMessage().contains("Connection timed out. Number of retries (1) exceeded. No further attempts will be made."));
		}
	}
}
