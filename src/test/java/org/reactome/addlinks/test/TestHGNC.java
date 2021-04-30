package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.release.common.dataretrieval.FileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.HGNCFileProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.reactome.addlinks.referencecreators.HGNCReferenceCreator;

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

	@Autowired
	HGNCReferenceCreator HGNCReferenceCreator;

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

	private List<GKInstance> getIdentifiersList(String refDb, String species, String className)
	{
		// Need a list of identifiers.
		String refDBID = objectCache.getRefDbNamesToIds().get(refDb).get(0);
		List<GKInstance> identifiers;
		if (species!=null)
		{
			String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
			identifiers = objectCache.getByRefDbAndSpecies(refDBID, speciesDBID, className);
			System.out.println(refDb + " " + refDBID + " ; " + species + " " + speciesDBID);
		}
		else
		{
			identifiers = objectCache.getByRefDb(refDBID, className);
			System.out.println(refDb + " " + refDBID + " ; " );
		}

		return identifiers;
	}

	@Test
	public void testHGNCReferenceCreation() throws Exception
	{
		ReferenceObjectCache.rebuildAllCachesWithoutClearing();
		this.HGNCRetriever.fetchData();
		Map<String, List<String>> mappings = this.HGNCProcessor.getIdMappingsFromFile();
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);

		List<GKInstance> sourceIdentifiers = getIdentifiersList("UniProt", "Homo sapiens", "ReferenceGeneProduct");
		System.out.println("db id: "+ objectCache.getRefDbNamesToIds().get("UniProt").get(0));
		assertTrue(sourceIdentifiers.size() > 0);
		HGNCReferenceCreator.createIdentifiers(1234, mappings, sourceIdentifiers );
	}
}
