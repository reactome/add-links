package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.HGNCFileProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestHGNC
{

	@Autowired
	MySQLAdaptor dbAdapter;
	
	@Autowired
	ReferenceObjectCache objectCache;
	
	@Autowired
	FileRetriever HGNCRetriever;
	
	@Autowired
	HGNCFileProcessor HGNCProcessor;
	
	@Test
	public void testHGNCDownload() throws Exception
	{
		this.HGNCRetriever.fetchData();
	}

	@Test
	public void testHGNCProcessing() throws Exception
	{
		this.HGNCRetriever.fetchData();
		Map<String, List<String>> mappings = this.HGNCProcessor.getIdMappingsFromFile();
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
	}	
}
