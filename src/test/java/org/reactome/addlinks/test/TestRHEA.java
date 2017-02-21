package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.dataretrieval.RHEAFileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.RHEAFileProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestRHEA
{
	@Autowired
	ReferenceObjectCache objectCache;
	
	@Autowired
	FileRetriever rheaRetriever;
	
	@Autowired
	RHEAFileProcessor rheaFileProcessor;
	
	@Test
	public void testGetRheaFiles()
	{
		try
		{
			rheaRetriever.fetchData();
		} catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail();
		}
		
	}
	
	@Test
	public void testGetProcessFiles()
	{
		try
		{
			rheaRetriever.fetchData();
			Map<String, List<String> > mappings = rheaFileProcessor.getIdMappingsFromFile();
			assertNotNull(mappings);
			assertTrue(mappings.keySet().size() > 0);
			
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail();
		}
		
	}
}
