package org.reactome.addlinks.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.time.Duration;

import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.reactome.addlinks.dataretrieval.pharos.PharosDataException;
import org.reactome.addlinks.dataretrieval.pharos.PharosDataRetriever;
import org.reactome.addlinks.dataretrieval.pharos.PharosLigandDataRetriever;
import org.reactome.addlinks.dataretrieval.pharos.PharosTargetsDataRetriever;

/**
 * Integration tests for the Pharos data retriever - will make calls to live Pharos webservice.
 * @author sshorser
 *
 */
public class PharosDataRetrieverIT
{

	@Mock
	CloseableHttpClient mockClient;

	@Mock
	CloseableHttpResponse response;

	@Mock
	StatusLine mockStatusLine;

	private static final String PHAROS_ENDPOINT = "https://ncatsidg-dev.appspot.com/graphql";

	@Test
	public void testPharosTargetsDataRetriever() throws URISyntaxException
	{
		String pathToPharosTargetsFile = "/tmp/pharos_targets_data.txt";
		PharosDataRetriever retriever = new PharosTargetsDataRetriever();
		testRetriever(pathToPharosTargetsFile, retriever);
	}

	@Test
	public void testPharosLigandsDataRetriever() throws URISyntaxException
	{
		String pathToPharosLigandsFile = "/tmp/pharos_ligand_data.txt";
		PharosDataRetriever retriever = new PharosLigandDataRetriever();
		testRetriever(pathToPharosLigandsFile, retriever);
	}

	@Before
	public void setup()
	{
		MockitoAnnotations.openMocks(this);
	}


	/**
	 * Test bad JSON
	 * @throws URISyntaxException
	 * @throws ParseException
	 * @throws IOException
	 */
	@Test
	public void testPharosBadJSON() throws URISyntaxException, ParseException, IOException
	{
		String pathToPharosTargetsFile = "/tmp/pharos_targets_data.txt";
		PharosDataRetriever retriever = new PharosTargetsDataRetriever();

		// malformed JSON string (missing some closing braces)
		String badData = 	"{"+
						  "\"data\": {"+
						    "\"targets\": {"+
						      "\"count\": 20412,"+
						      "\"targets\": ["+
						        "{"+
						          "\"asdfasfd\": \"A8MVA2\""+ // No "uniprot" key, sure to cause an exception
						        "},"+
						        "]}";
		setUpMocksForException();

		try(MockedStatic<HttpClients> mockedStaticClient = Mockito.mockStatic(HttpClients.class);
			MockedStatic<EntityUtils> mockedStaticUtils = Mockito.mockStatic(EntityUtils.class); )
		{
			testExceptionThrowingRequest(retriever, badData);
		}
		catch (JSONException | PharosDataException e)
		{
			assertTrue(e.getMessage().startsWith("There was a problem with the JSON from Pharos:"));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Sets up mocks.
	 * @throws IOException - declared as thrown by mockClient.execute
	 */
	private void setUpMocksForException() throws IOException
	{
		HttpEntity entity = new ByteArrayEntity("".getBytes());
		Mockito.when(mockStatusLine.getStatusCode()).thenReturn(200);
		Mockito.when(response.getStatusLine()).thenReturn(mockStatusLine);
		Mockito.when(response.getEntity()).thenReturn(entity);
		Mockito.when(mockClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenReturn(response);
	}

	/**
	 * Test bad data
	 * @throws URISyntaxException
	 * @throws ParseException
	 * @throws IOException
	 */
	@Test
	public void testPharosBadData() throws URISyntaxException, ParseException, IOException
	{
		PharosDataRetriever retriever = new PharosTargetsDataRetriever();

		String badData = 	"{"+
						  "\"data\": {"+
						    "\"targets\": {"+
						      "\"count\": 20412,"+
						      "\"targets\": ["+
						        "{"+
						          "\"asdfasfd\": \"A8MVA2\""+ // No "uniprot" key, sure to cause an exception
						        "},"+
						        "]}}}";
		setUpMocksForException();

		try(MockedStatic<HttpClients> mockedStaticClient = Mockito.mockStatic(HttpClients.class);
			MockedStatic<EntityUtils> mockedStaticUtils = Mockito.mockStatic(EntityUtils.class); )
		{
			testExceptionThrowingRequest(retriever, badData);
		}
		catch (PharosDataException e)
		{
			assertEquals("The \"uniprot\" key was not present, but it should be (in fact, it should be the ONLY key at this depth in the document). Check your query!", e.getMessage());
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Executes a retriever that is expected to throw an exception.
	 * @param retriever - the retriever.
	 * @param badData - A string representing data that will cause a problem.
	 * @throws IOException - declared as thrown by EntityUtils.toString
	 * @throws URISyntaxException - declared as thrown by URI constructor
	 * @throws Exception - declared as thrown by fetchData
	 */
	private void testExceptionThrowingRequest(PharosDataRetriever retriever, String badData) throws IOException, URISyntaxException, Exception
	{
		Mockito.when(HttpClients.createDefault()).thenReturn(mockClient);
		Mockito.when(EntityUtils.toString(any(HttpEntity.class))).thenReturn(badData);
		retriever.setDataURL(new URI(PHAROS_ENDPOINT));
		retriever.setFetchDestination("/tmp/testfile");
		// we always want to download when testing....
		retriever.setMaxAge(Duration.ofSeconds(0));
		retriever.fetchData();
	}

	/**
	 * Tests a Pharos retriever.
	 * @param pathToPharosFile Path to output file.
	 * @param retriever A retriever object to test.
	 * @throws URISyntaxException - as the name suggests, this gets thrown when there is a problem with syntax in the URI for Pharos.
	 */
	private void testRetriever(String pathToPharosFile, PharosDataRetriever retriever) throws URISyntaxException
	{
		retriever.setDataURL(new URI(PHAROS_ENDPOINT));
		retriever.setFetchDestination(pathToPharosFile);
		// we always want to download when testing....
		retriever.setMaxAge(Duration.ofSeconds(0));
		try
		{
			retriever.fetchData();
			assertTrue(Files.exists(Paths.get(pathToPharosFile), LinkOption.NOFOLLOW_LINKS));
			assertTrue(Files.size(Paths.get(pathToPharosFile)) > 0);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}

}
