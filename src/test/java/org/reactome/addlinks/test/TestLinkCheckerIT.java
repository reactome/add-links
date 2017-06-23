package org.reactome.addlinks.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.linkchecking.LinkCheckInfo;
import org.reactome.addlinks.linkchecking.LinkChecker;

@PowerMockIgnore({"javax.management.*","javax.net.ssl.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({ HttpClients.class })
public class TestLinkCheckerIT
{
	@Test
	public void testLinkCheckerGoogle()
	{
		URI uri = null;
		try
		{
			String keyword = "Google";
			uri = new URI("http://www.google.com");
			LinkChecker checker = new LinkChecker(uri, keyword);
			
			LinkCheckInfo info = checker.checkLink();
			
			assertEquals(200,info.getStatusCode());
			assertTrue(info.isKeywordFound());
			System.out.println(info.getResponseTime());
			assertTrue(info.getResponseTime().compareTo(Duration.ZERO) > 1);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testLinkCheckerGoogleKeywordNotFound()
	{
		URI uri = null;
		try
		{
			String keyword = "Gosdfhawfhlqiuh489p3hiulgkah w[pqu34890asdfg]d:ogle";
			uri = new URI("http://www.google.com");
			LinkChecker checker = new LinkChecker(uri, keyword);
			
			LinkCheckInfo info = checker.checkLink();
			
			assertEquals(200,info.getStatusCode());
			assertFalse(info.isKeywordFound());
			System.out.println(info.getResponseTime());
			assertTrue(info.getResponseTime().compareTo(Duration.ZERO) > 1);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testLinkCheckerInvalidURL()
	{
		URI uri = null;
		try
		{
			String keyword = "Google";
			uri = new URI("http://www.jklzsdasdfjkluioaeruiq4g.com");
			LinkChecker checker = new LinkChecker(uri, keyword);
			
			LinkCheckInfo info = checker.checkLink();
			
			assertEquals(200,info.getStatusCode());
			assertTrue(info.isKeywordFound());
			System.out.println(info.getResponseTime());
			assertTrue(info.getResponseTime().compareTo(Duration.ZERO) > 1);
		}
		catch (UnknownHostException e)
		{
			assertTrue(true);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testLinkCheckerGoogleWithTimeout() throws ClientProtocolException, IOException
	{
		// Need to mock CloseableHttpClient and have execute method timeout once.
		CloseableHttpClient mockedClient = PowerMockito.mock(CloseableHttpClient.class);
		PowerMockito.when(mockedClient.execute(any(HttpUriRequest.class), any(HttpContext.class) )).thenThrow(new ConnectTimeoutException()).thenThrow(new ConnectTimeoutException()).thenCallRealMethod();
		
		PowerMockito.mockStatic(HttpClients.class);
		PowerMockito.when(HttpClients.createDefault()).thenReturn(mockedClient).thenReturn(mockedClient).thenCallRealMethod();
		
		URI uri = null;
		try
		{
			String keyword = "Google";
			uri = new URI("http://www.google.com");
			LinkChecker checker = new LinkChecker(uri, keyword);
			
			LinkCheckInfo info = checker.checkLink();
			
			assertEquals(200,info.getStatusCode());
			assertTrue(info.isKeywordFound());
			System.out.println(info.getResponseTime());
			assertTrue(info.getResponseTime().compareTo(Duration.ZERO) > 1);
			System.out.println(info.getNumRetries());
			assertEquals(2,info.getNumRetries());
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
	
	
}
