package org.reactome.addlinks.test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
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
import org.reactome.addlinks.dataretrieval.BatchWebServiceDataRetriever;
import org.powermock.modules.junit4.PowerMockRunner;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({ org.apache.http.impl.client.HttpClients.class})
public class TestWSRetrieval {

	private static final String MESSAGE_CONTENT = "this is a test";

	@Mock
	CloseableHttpClient mockClient;

	@Mock
	CloseableHttpResponse response;

	HttpEntity entity = new ByteArrayEntity(MESSAGE_CONTENT.getBytes());

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testGetFromWebService() throws ClientProtocolException, IOException, Exception {

		Mockito.when(response.getEntity()).thenReturn(entity);

		Mockito.when(mockClient.execute(any(HttpUriRequest.class))).thenReturn(response);

		PowerMockito.mockStatic(HttpClients.class);
		PowerMockito.when(HttpClients.createDefault()).thenReturn(mockClient);

		BatchWebServiceDataRetriever wsRetriever = new BatchWebServiceDataRetriever();

		wsRetriever.setDataURL("http://www.test.url/");
		wsRetriever.setFetchDestination("/tmp/unittests/");
		wsRetriever.setMaxAge(Duration.of(1, ChronoUnit.SECONDS));
		wsRetriever.setName(this.getClass().getName());
		
		Supplier<List<String>> supplier = () -> Arrays.asList("123");
		wsRetriever.setInputSupplier(supplier );
		
		wsRetriever.fetchData();
		
		//TODO: Proper test assertions.
		assertEquals(new String(Files.readAllBytes(Paths.get("/tmp/unittests/"+this.getClass().getName()+"_123"))),MESSAGE_CONTENT);
	}

}
