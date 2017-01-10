package org.reactome.addlinks.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.ensembl.EnsemblBatchLookup;
import org.reactome.addlinks.db.ReferenceObjectCache;
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
}
