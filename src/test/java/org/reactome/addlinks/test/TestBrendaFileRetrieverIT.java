package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.BRENDAFileRetriever;
import org.reactome.addlinks.fileprocessors.BRENDAFileProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestBrendaFileRetrieverIT
{

	@Autowired
	BRENDAFileRetriever brendaRetriever;
	
	@Autowired
	BRENDAFileProcessor brendaProcessor;
	
	@Test
	public void testBrendaRetriever() throws Exception
	{
		
		Map<String, List<String>> identifiers = new HashMap<String, List<String>>();
		
		identifiers.put("Homo sapiens", Arrays.asList("Q8N5Z0", "O00116"));
		
		brendaRetriever.setIdentifiers(identifiers);
		
		brendaRetriever.fetchData();
	}
	
	@Test
	public void testBrendaProcessor() throws Exception
	{
		
		Map<String, List<String>> identifiers = new HashMap<String, List<String>>();
		
		identifiers.put("Homo sapiens", Arrays.asList("Q8N5Z0", "O00116"));
		
		brendaRetriever.setIdentifiers(identifiers);
		
		brendaRetriever.fetchData();
		
		Map<String, List<String>> mappings = brendaProcessor.getIdMappingsFromFile();

		System.out.println(mappings);
		
		assertNotNull(mappings);
		
		assertTrue(mappings.keySet().size() > 0);
		
	}
	
}
