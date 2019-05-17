package org.reactome.addlinks.test;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.IdentifiersDotOrgUtil;

@PowerMockIgnore({"javax.management.*","javax.net.ssl.*", "javax.security.*"})
@PrepareForTest({IdentifiersDotOrgUtil.class, HttpClients.class, EntityUtils.class})
@RunWith(PowerMockRunner.class)
public class TestIdentifiersDotOrgUtilIT
{

	@Test
	public void testGetResourceIT()
	{
		String accessURL = IdentifiersDotOrgUtil.getAccessUrlForResource("MIR:00100374");
		assertNotNull(accessURL);
		//...of course, if the resource accessURL ever changes, this test will fail. ;)
		assertTrue("https://hamap.expasy.org/unirule/{$id}".equals(accessURL));
		System.out.println(accessURL);
	}
	
	@Test
	public void testGetUnknownResourceIT()
	{
		String accessURL = IdentifiersDotOrgUtil.getAccessUrlForResource("abc123");
		assertNull(accessURL);
	}
	
	@Test
	public void testOtherErrorCode() throws ClientProtocolException, IOException
	{
		// Mocks are used to test other response codes, such as 500, which normally doesn't happen when integration tests are working, so
		// this is the only way to test the code that handles responses other than 200/404.
		PowerMockito.mockStatic(HttpClients.class);
		CloseableHttpClient mockClient = Mockito.mock(CloseableHttpClient.class);
		CloseableHttpResponse mockResponse = Mockito.mock(CloseableHttpResponse.class);
		HttpEntity mockEntity = Mockito.mock(HttpEntity.class);
		PowerMockito.mockStatic(EntityUtils.class);
		Mockito.when(EntityUtils.toString(mockEntity)).thenReturn("blah blah blah");
		Mockito.when(mockResponse.getEntity()).thenReturn(mockEntity );
		StatusLine mockStatusLine = Mockito.mock(StatusLine.class);
		Mockito.when(mockStatusLine.getStatusCode()).thenReturn(500);
		Mockito.when(mockResponse.getStatusLine()).thenReturn(mockStatusLine );
		PowerMockito.when(mockClient.execute( any(HttpGet.class) )).thenReturn(mockResponse );
		Mockito.when(HttpClients.createDefault()).thenReturn(mockClient);
		
		// Now that the mocks are set up, call the method.
		String accessURL = IdentifiersDotOrgUtil.getAccessUrlForResource("abc123");
		assertNull(accessURL);
	}
}
