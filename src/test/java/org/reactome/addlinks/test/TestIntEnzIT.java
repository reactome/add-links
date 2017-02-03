package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.fileprocessors.IntEnzFileProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestIntEnzIT
{

	@Autowired
	FileRetriever intEnzFileRetriever;
	
	@Autowired
	IntEnzFileProcessor intEnzFileProcessor;
	
	@Test
	public void testGetIntEnzFile() throws Exception
	{
		intEnzFileRetriever.fetchData();
	}
	
	
	@Test
	public void testProcessIntEnzFile() throws Exception
	{
		intEnzFileRetriever.fetchData();
		
		Map<String, List<String>> mappings = intEnzFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
	}
}
