package org.reactome.addlinks.test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;

import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.reactome.addlinks.IdentifiersDotOrgUtil;

public class TestIdentifiersDotOrgUtilIT
{

	@Mock
	CloseableHttpClient mockClient;

	@Mock
	CloseableHttpResponse mockResponse;

	@Mock
	StatusLine mockStatusLine;


	@Before
	public void setup()
	{
		MockitoAnnotations.openMocks(this);
	}

	@Test
	public void testGetResourceIT()
	{
		String resourceIdentifier = "MIR:00100374";
		String accessURL = IdentifiersDotOrgUtil.getAccessUrlForResource(resourceIdentifier);
		assertNotNull(accessURL);
		//...of course, if the resource accessURL ever changes, this test will fail. ;)
		assertEquals("https://hamap.expasy.org/unirule/{$id}",accessURL);
		System.out.println("Access URL for " + resourceIdentifier + " is " +accessURL);
	}

	@Test
	public void testGetUnknownResourceIT()
	{
		String accessURL = IdentifiersDotOrgUtil.getAccessUrlForResource("abc123");
		assertNull(accessURL);
	}

	@Test
	public void testDeactivatedResourceIT()
	{
		String accessURL = IdentifiersDotOrgUtil.getAccessUrlForResource("MIR:00100113");
		assertNull(accessURL);
	}

	@Test
	public void testOtherErrorCode() throws ClientProtocolException, IOException
	{
		// Mocks are used to test other response codes, such as 500, which normally doesn't happen when integration tests are working, so
		// this is the only way to test the code that handles responses other than 200/404.
		try(MockedStatic<HttpClients> mockedStaticClient = Mockito.mockStatic(HttpClients.class);
				MockedStatic<EntityUtils> mockedStaticUtils = Mockito.mockStatic(EntityUtils.class); )
		{

			Mockito.when(mockStatusLine.getStatusCode()).thenReturn(500);
			Mockito.when(mockResponse.getStatusLine()).thenReturn(mockStatusLine );
			Mockito.when(mockClient.execute( any(HttpGet.class) )).thenReturn(mockResponse );
			Mockito.when(HttpClients.createDefault()).thenReturn(mockClient);

			// Now that the mocks are set up, call the method.
			String accessURL = IdentifiersDotOrgUtil.getAccessUrlForResource("abc123");
			assertNull(accessURL);
		}
	}
}
