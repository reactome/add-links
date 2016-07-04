/**
 * 
 */
package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import org.apache.commons.net.ftp.FTPClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
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
@PrepareForTest({ java.net.URI.class, org.apache.commons.net.ftp.FTPClient.class, org.reactome.addlinks.dataretrieval.FileRetriever.class})
public class TestFileRetriever {

	@Mock
	FTPClient mockFtpClient ;
	

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
		String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt());
		retriever.setFetchDestination(dest);
		Duration age = Duration.of(1,ChronoUnit.SECONDS);
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
	

}
