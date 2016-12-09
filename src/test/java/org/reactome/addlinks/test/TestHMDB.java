package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.HmdbProteinsFileProcessor;
import org.reactome.addlinks.referencecreators.HMDBProteinReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration tests for HMDB code.
 * @author sshorser
 *
 */
@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestHMDB
{
	@Autowired
	ReferenceObjectCache objectCache;
	
	@Autowired
	FileRetriever HmdbProteinsRetriever;
	
	@Autowired
	HmdbProteinsFileProcessor HmdbProteinsFileProcessor;

	@Autowired
	HMDBProteinReferenceCreator HMDBProtReferenceCreator;
	
	@Test
	public void testGetHMDBProteinsFile() throws Exception
	{
		HmdbProteinsRetriever.fetchData();
	}
	
	@Test
	public void testProcessHMDBProteinsFile() throws Exception
	{
		HmdbProteinsRetriever.fetchData();
		
		Map<String, List<String>> mappings = HmdbProteinsFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
	}
	
	@Test
	public void testCreateHMDBReferences() throws Exception
	{
		HmdbProteinsRetriever.fetchData();
		
		Map<String, List<String>> mappings = HmdbProteinsFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
		
		String refDb = "2";
		String species = "48887";
		String className = "ReferenceGeneProduct";
		List<GKInstance> sourceReferences = objectCache.getByRefDbAndSpecies(refDb, species, className);
		
		HMDBProtReferenceCreator.createIdentifiers(123456, mappings, sourceReferences);
	}
}
