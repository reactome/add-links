package org.reactome.addlinks.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblBatchLookup;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblFileRetriever.EnsemblDB;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.EnsemblFileProcessor;
import org.reactome.addlinks.fileprocessors.ensembl.EnsemblBatchLookupFileProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-ensembl-mapped-identifiers.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestEnsemblFileRetrievalIT
{
	@Autowired
	ReferenceObjectCache objectCache;

	
	@Test
	public void testEnsemblBatchLookup() throws Exception
	{
		String refDb = "ENSEMBL_Gallus gallus_PROTEIN";
		String species = "Gallus gallus";
		String className = "ReferenceGeneProduct";
		List<String> refDBIDs = objectCache.getRefDbNamesToIds().get(refDb);
		String refDBID = refDBIDs.get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);

		List<String> identifiers = TestUtils.getIdentifiersList(refDBID,species,className,objectCache);
		System.out.println("# identifiers: "+identifiers.size());
		EnsemblBatchLookup retriever = new EnsemblBatchLookup();		
		retriever.setSpecies(species.replace(" ", "_"));
		//retriever.setDataURL(new URI("http://rest.ensembl.org/lookup/id/"));
		retriever.setDataURL(new URI("http://localhost:9999/lookup/id/"));
		retriever.setFetchDestination("/tmp/addlinks-downloaded-files/ensembl/ENSP_batch_lookup."+speciesDBID+"."+refDBID+".xml");
		retriever.setMaxAge(Duration.ZERO);
		retriever.setIdentifiers(identifiers);
		retriever.fetchData();
	}
	
	@Test
	public void testEnsemblBatchProcessing() throws Exception
	{
		String refDb = "ENSEMBL_Gallus gallus_PROTEIN";
		String species = "Gallus gallus";
		List<String> refDBIDs = objectCache.getRefDbNamesToIds().get(refDb);
		String refDBID = refDBIDs.get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		
		testEnsemblBatchLookup();
		
		EnsemblBatchLookupFileProcessor processor = new EnsemblBatchLookupFileProcessor();
		
		processor.setPath(Paths.get("/tmp/addlinks-downloaded-files/ensembl/ENSP_batch_lookup."+speciesDBID+"."+refDBID+".xml"));
		Map<String,String> mappings = processor.getIdMappingsFromFile();
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
	}
	
	@Test
	public void testEnsemblProteinToGeneLookup() throws Exception
	{
		String refDb = "ENSEMBL_Gallus gallus_PROTEIN";
		String species = "Gallus gallus";
		String className = "ReferenceGeneProduct";
		List<String> refDBIDs = objectCache.getRefDbNamesToIds().get(refDb);
		String refDBID = refDBIDs.get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);

		List<String> identifiers = TestUtils.getIdentifiersList(refDBID,species,className,objectCache);
		System.out.println("# identifiers: "+identifiers.size());
		EnsemblBatchLookup protein2TranscriptRetriever = new EnsemblBatchLookup();		
		protein2TranscriptRetriever.setSpecies(species.replace(" ", "_"));
		//retriever.setDataURL(new URI("http://rest.ensembl.org/lookup/id/"));
		protein2TranscriptRetriever.setDataURL(new URI("http://localhost:9999/lookup/id/"));
		protein2TranscriptRetriever.setFetchDestination("/tmp/addlinks-downloaded-files/ensembl/ENSP_batch_lookup."+speciesDBID+"."+refDBID+".xml");
		protein2TranscriptRetriever.setMaxAge(Duration.ZERO);
		protein2TranscriptRetriever.setIdentifiers(identifiers);
		protein2TranscriptRetriever.fetchData();
		
		EnsemblBatchLookupFileProcessor processor = new EnsemblBatchLookupFileProcessor();
		
		processor.setPath(Paths.get("/tmp/addlinks-downloaded-files/ensembl/ENSP_batch_lookup."+speciesDBID+"."+refDBID+".xml"));
		Map<String,String> mappings = processor.getIdMappingsFromFile();
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
		
		List<String> transcripts = new ArrayList<String>(mappings.values());
		assertTrue(transcripts.size() > 0);
		EnsemblBatchLookup transacript2GeneRetriever = new EnsemblBatchLookup();		
		transacript2GeneRetriever.setSpecies(species.replace(" ", "_"));
		//retriever.setDataURL(new URI("http://rest.ensembl.org/lookup/id/"));
		transacript2GeneRetriever.setDataURL(new URI("http://localhost:9999/lookup/id/"));
		transacript2GeneRetriever.setFetchDestination("/tmp/addlinks-downloaded-files/ensembl/ENST_batch_lookup."+speciesDBID+"."+refDBID+".xml");
		transacript2GeneRetriever.setMaxAge(Duration.ZERO);
		transacript2GeneRetriever.setIdentifiers(transcripts);
		transacript2GeneRetriever.fetchData();
		
		processor.setPath(Paths.get("/tmp/addlinks-downloaded-files/ensembl/ENST_batch_lookup."+speciesDBID+"."+refDBID+".xml"));
		Map<String,String> mappings2 = processor.getIdMappingsFromFile();
		assertNotNull(mappings2);
		assertTrue(mappings2.keySet().size() > 0);
		
	}
	
	@Test public void testEnsemblXRefLookup() throws Exception
	{
		String refDb = "ENSEMBL_Gallus gallus_PROTEIN";
		String species = "Gallus gallus";
		String className = "ReferenceGeneProduct";
		List<String> refDBIDs = objectCache.getRefDbNamesToIds().get(refDb);
		String refDBID = refDBIDs.get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);

		List<String> identifiers = TestUtils.getIdentifiersList(refDBID,species,className,objectCache);
		System.out.println("# identifiers: "+identifiers.size());
		EnsemblBatchLookup protein2TranscriptRetriever = new EnsemblBatchLookup();		
		protein2TranscriptRetriever.setSpecies(species.replace(" ", "_"));
		//retriever.setDataURL(new URI("http://rest.ensembl.org/lookup/id/"));
		protein2TranscriptRetriever.setDataURL(new URI("http://localhost:9999/lookup/id/"));
		protein2TranscriptRetriever.setFetchDestination("/tmp/addlinks-downloaded-files/ensembl/ENSP_batch_lookup."+speciesDBID+"."+refDBID+".xml");
		protein2TranscriptRetriever.setMaxAge(Duration.ZERO);
		protein2TranscriptRetriever.setIdentifiers(identifiers);
		protein2TranscriptRetriever.fetchData();
		
		EnsemblBatchLookupFileProcessor processor = new EnsemblBatchLookupFileProcessor();
		
		processor.setPath(Paths.get("/tmp/addlinks-downloaded-files/ensembl/ENSP_batch_lookup."+speciesDBID+"."+refDBID+".xml"));
		Map<String,String> mappings = processor.getIdMappingsFromFile();
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
		
		List<String> transcripts = new ArrayList<String>(mappings.values());
		assertTrue(transcripts.size() > 0);
		EnsemblBatchLookup transacript2GeneRetriever = new EnsemblBatchLookup();		
		transacript2GeneRetriever.setSpecies(species.replace(" ", "_"));
		//retriever.setDataURL(new URI("http://rest.ensembl.org/lookup/id/"));
		transacript2GeneRetriever.setDataURL(new URI("http://localhost:9999/lookup/id/"));
		transacript2GeneRetriever.setFetchDestination("/tmp/addlinks-downloaded-files/ensembl/ENST_batch_lookup."+speciesDBID+"."+refDBID+".xml");
		transacript2GeneRetriever.setMaxAge(Duration.ZERO);
		transacript2GeneRetriever.setIdentifiers(transcripts);
		transacript2GeneRetriever.fetchData();
		
		processor.setPath(Paths.get("/tmp/addlinks-downloaded-files/ensembl/ENST_batch_lookup."+speciesDBID+"."+refDBID+".xml"));
		Map<String,String> mappings2 = processor.getIdMappingsFromFile();
		assertNotNull(mappings2);
		assertTrue(mappings2.keySet().size() > 0);
		
		EnsemblFileRetriever xrefRetriever = new EnsemblFileRetriever();
		xrefRetriever.setDataURL(new URI("http://rest.ensembl.org/xrefs/id/"));
		xrefRetriever.setFetchDestination("/tmp/addlinks-downloaded-files/ensembl-mappings/ensembl_to_EntrezGene.xml");
		xrefRetriever.setMapFromDbEnum(EnsemblDB.ENSEMBLProtein);
		xrefRetriever.setMapToDbEnum(EnsemblDB.EntrezGene);
		List<String> ensgList = new ArrayList<String>(mappings2.values());
		xrefRetriever.setIdentifiers(ensgList);
		xrefRetriever.setSpecies(species.replace(" ", "_"));
		xrefRetriever.setMaxAge(Duration.ZERO);
		
		
		xrefRetriever.fetchData();
		
		EnsemblFileProcessor xrefProcessor = new EnsemblFileProcessor();
		
		xrefProcessor.setPath(Paths.get("/tmp/addlinks-downloaded-files/ensembl-mappings/ensembl_to_EntrezGene.xml"));
		xrefProcessor.setDbs(Arrays.asList("EntrezGene"));
		
		Map<String, Map<String, List<String>>> xrefMappings = xrefProcessor.getIdMappingsFromFile();
		assertNotNull(xrefMappings);
		assertTrue(xrefMappings.keySet().size() > 0);
	}
}
