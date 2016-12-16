package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.HmdbMetabolitesFileProcessor;
import org.reactome.addlinks.fileprocessors.HmdbProteinsFileProcessor;
import org.reactome.addlinks.referencecreators.HMDBMoleculeReferenceCreator;
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
	FileRetriever HmdbMoleculesRetriever;
	
	@Autowired
	HmdbProteinsFileProcessor HmdbProteinsFileProcessor;

	@Autowired
	HMDBProteinReferenceCreator HMDBProtReferenceCreator;
	
	@Autowired
	HmdbMetabolitesFileProcessor HmdbMoleculesFileProcessor;
	
//	@Autowired
//	HMDBMoleculeReferenceCreator HmdbMoleculeReferenceCreator;
	
	@Test
	public void testGetHMDBProteinsFile() throws Exception
	{
		HmdbProteinsRetriever.fetchData();
	}

	@Test
	public void testGetHMDBMoleculessFile() throws Exception
	{
		HmdbMoleculesRetriever.fetchData();
	}
	
	@Test
	public void testProcessHMDBMoleculesFile() throws Exception
	{
		HmdbMoleculesRetriever.fetchData();
		
		Map<String, Map<String, Collection<String>>> mappings = HmdbMoleculesFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
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
	public void testCreateHMDBProteinsReferences() throws Exception
	{
		HmdbProteinsRetriever.fetchData();
		
		Map<String, List<String>> mappings = HmdbProteinsFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
		
		String refDB = objectCache.getRefDbNamesToIds().get("UniProt").get(0);
		String species = "48887";
		String className = "ReferenceGeneProduct";
		List<GKInstance> sourceReferences = objectCache.getByRefDbAndSpecies(refDB, species, className);
		
		HMDBProtReferenceCreator.createIdentifiers(123456, mappings, sourceReferences);
	}
	
//	@Test
//	public void testCreateHMDBMoleculesReferences() throws Exception
//	{
//		HmdbMoleculesRetriever.fetchData();
//		
//		Map<String, String> mappings = HmdbMoleculesFileProcessor.getIdMappingsFromFile();
//		
//		assertNotNull(mappings);
//		assertTrue(mappings.keySet().size() > 0);
//		String refDB = objectCache.getRefDbNamesToIds().get("ChEBI").get(0);
//		String species = "48887";
//		String className = "ReferenceMolecule";
//		List<GKInstance> sourceReferences = objectCache.getByRefDbAndSpecies(refDB, species, className);
//		
//		HmdbMoleculeReferenceCreator.createIdentifiers(123456, mappings, sourceReferences);
//	}
}
