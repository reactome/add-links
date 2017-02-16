package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.BRENDAFileRetriever;
import org.reactome.addlinks.dataretrieval.BRENDAFileRetriever.BRENDASoapClient;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.BRENDAFileProcessor;
import org.reactome.addlinks.referencecreators.BRENDAReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestBrendaIT
{

	@Autowired
	ReferenceObjectCache objectCache;
	
	@Autowired
	BRENDAFileRetriever brendaRetriever;
	
	@Autowired
	BRENDAFileProcessor brendaProcessor;
	
	@Autowired
	BRENDAReferenceCreator brendaRefCreator;
	
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
	
	@Test
	public void testBrendaReferenceCreator() throws Exception
	{
		
		Map<String, List<String>> identifiers = new HashMap<String, List<String>>();
		
		identifiers.put("Homo sapiens", Arrays.asList("Q8N5Z0", "O00116"));
		
		brendaRetriever.setIdentifiers(identifiers);
		
		brendaRetriever.fetchData();
		
		Map<String, List<String>> mappings = brendaProcessor.getIdMappingsFromFile();

		System.out.println(mappings);
		
		assertNotNull(mappings);
		
		assertTrue(mappings.keySet().size() > 0);
		
		List<GKInstance> sourceReferences = objectCache.getByRefDb( objectCache.getRefDbNamesToIds().get("UniProt").get(0) , ReactomeJavaConstants.ReferenceGeneProduct);
		
		brendaRefCreator.createIdentifiers(123465L, mappings, sourceReferences);
		
	}

	
	@Test
	public void testBrendaReferenceCreatorAllUniProt() throws Exception
	{
		BRENDASoapClient client = brendaRetriever.new BRENDASoapClient(brendaRetriever.getUserName(), brendaRetriever.getPassword());
		
		String speciesResult = client.callBrendaService(brendaRetriever.getDataURL().toString(), "getOrganismsFromOrganism", "");
		//Normalize the list.
		List<String> brendaSpecies = Arrays.asList(speciesResult.split("!")).stream().map(species -> species.replace("'", "").replaceAll("\"", "").trim().toUpperCase() ).collect(Collectors.toList());
		System.out.println(brendaSpecies.size() + " species known to BRENDA");

		Map<String, List<String>> identifiers = new HashMap<String, List<String>>();
		
		for (String speciesId : objectCache.getSpeciesNamesByID().keySet())
		{
			String speciesName = objectCache.getSpeciesNamesByID().get(speciesId).get(0);
			
			if (brendaSpecies.contains(speciesName.trim().toUpperCase()))
			{
				List<String> uniprotIdentifiers = objectCache.getByRefDbAndSpecies("2", speciesId, ReactomeJavaConstants.ReferenceGeneProduct).stream().map(instance -> {
					try
					{
						return (String)instance.getAttributeValue(ReactomeJavaConstants.identifier);
					} catch (InvalidAttributeException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return null;
				}).collect(Collectors.toList());
				
				System.out.println("Species: "+speciesId+"/"+speciesName);
				identifiers.put(speciesName,uniprotIdentifiers);
			}
			else
			{
				System.out.println("Species " + speciesName + "is not in the list of species known to BRENDA.");
			}
		}
		
		brendaRetriever.setIdentifiers(identifiers);
		
		brendaRetriever.fetchData();
		
		Map<String, List<String>> mappings = brendaProcessor.getIdMappingsFromFile();

		System.out.println(mappings);
		
		assertNotNull(mappings);
		
		assertTrue(mappings.keySet().size() > 0);
		
		List<GKInstance> sourceReferences = objectCache.getByRefDb( objectCache.getRefDbNamesToIds().get("UniProt").get(0) , ReactomeJavaConstants.ReferenceGeneProduct);
		
		brendaRefCreator.createIdentifiers(123465L, mappings, sourceReferences);
		
	}
}
