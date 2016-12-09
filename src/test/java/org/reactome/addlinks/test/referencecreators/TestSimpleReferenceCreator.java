package org.reactome.addlinks.test.referencecreators;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.FlyBaseFileProcessor;
import org.reactome.addlinks.fileprocessors.OrphanetFileProcessor;
import org.reactome.addlinks.fileprocessors.PROFileProcessor;
import org.reactome.addlinks.fileprocessors.ZincMoleculesFileProcessor;
import org.reactome.addlinks.referencecreators.SimpleReferenceCreator;
import org.reactome.addlinks.referencecreators.OneToOneReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * INTEGRATION tests of file retrieval, file processing, reference creation.
 * @author sshorser
 *
 */
@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)

public class TestSimpleReferenceCreator
{
	@Autowired
	FileRetriever FlyBaseToUniprotReferenceDNASequence;
	
	@Autowired
	FlyBaseFileProcessor flyBaseFileProcessor; 
	
	@Autowired
	SimpleReferenceCreator<String> FlyBaseReferenceCreator;
	
	@Autowired
	ReferenceObjectCache objectCache;
	
	@Autowired
	FileRetriever OrphanetToUniprotReferenceDNASequence;
	
	@Autowired
	OrphanetFileProcessor orphanetFileProcessor; 
	
	@Autowired
	SimpleReferenceCreator<String> OrphanetReferenceCreator;

	@Autowired
	FileRetriever PROToReferencePeptideSequence;
	
	@Autowired
	PROFileProcessor proFileProcessor; 
	
	@Autowired
	SimpleReferenceCreator<String> proRefCreator;
	
	@Autowired
	OneToOneReferenceCreator geneCardsRefCreator;
	
	@Autowired
	FileRetriever ZincFileRetriever;
	
	@Autowired
	ZincMoleculesFileProcessor zincMolFileProcessor;
	
	@Autowired
	SimpleReferenceCreator<String> zincToChEBIReferenceCreator;
	
	@Test
	public void testFlyBaseReferenceCreator() throws Exception
	{
		FlyBaseToUniprotReferenceDNASequence.fetchData();
		
		Map<String, String> uniprotToFlyBaseMap = flyBaseFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(uniprotToFlyBaseMap);
		assertTrue(uniprotToFlyBaseMap.size() > 0);
		//ReferenceObjectCache.setAdapter(adapter);
		// 2 == UniProt
		List<GKInstance> uniprotReferences = objectCache.getByRefDb("2", ReactomeJavaConstants.ReferenceGeneProduct);
		FlyBaseReferenceCreator.createIdentifiers(123456, uniprotToFlyBaseMap, uniprotReferences);
		//TODO: Assert the creation worked. Maybe do this by intercepting the actual call with a mock class...
	}
	
	@Test
	public void testOrphanetReferenceCreator() throws Exception
	{
		OrphanetToUniprotReferenceDNASequence.fetchData();
		
		Map<String, String> uniprotToOrphanetMap = orphanetFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(uniprotToOrphanetMap);
		assertTrue(uniprotToOrphanetMap.size() > 0);
		//ReferenceObjectCache.setAdapter(adapter);
		// 2 == UniProt
		List<GKInstance> uniProtReferences = objectCache.getByRefDb("2", ReactomeJavaConstants.ReferenceGeneProduct);
		OrphanetReferenceCreator.createIdentifiers(123456, uniprotToOrphanetMap, uniProtReferences);
		//TODO: Assert the creation worked. Maybe do this by intercepting the actual call with a mock class...
	}

	@Test
	public void testPROReferenceCreator() throws Exception
	{
		PROToReferencePeptideSequence.fetchData();
		
		Map<String, String> uniprotToProMap = proFileProcessor.getIdMappingsFromFile();
		
		assertNotNull(uniprotToProMap);
		assertTrue(uniprotToProMap.size() > 0);
		//ReferenceObjectCache.setAdapter(adapter);
		// 2 == UniProt
		List<GKInstance> uniprotReferences = objectCache.getByRefDb("2", ReactomeJavaConstants.ReferenceGeneProduct);
		proRefCreator.createIdentifiers(123456, uniprotToProMap, uniprotReferences);
		//TODO: Assert the creation worked. Maybe do this by intercepting the actual call with a mock class...
	}
	
	@Test
	public void testGeneCardsReferenceCreator() throws Exception
	{
		List<GKInstance> uniprotReferences = objectCache.getByRefDbAndSpecies("2", "48887", ReactomeJavaConstants.ReferenceGeneProduct);
		assertTrue(uniprotReferences.size() > 0);
		geneCardsRefCreator.createIdentifiers(123456, uniprotReferences);
	}
	
	@Test
	public void testZincMoleculeReferenceCreator() throws Exception
	{
		String className = "ReferenceMolecule";
		String refDb = objectCache.getRefDbNamesToIds().get("ChEBI").get(0);
		System.out.println("ChEBI ID: "+refDb);
		List<GKInstance> chebiReferences = objectCache.getByRefDb(refDb, className);
		ZincFileRetriever.fetchData();
		Map<String,String> mappings = zincMolFileProcessor.getIdMappingsFromFile();
		zincToChEBIReferenceCreator.createIdentifiers(123456, mappings, chebiReferences);
	}
}
