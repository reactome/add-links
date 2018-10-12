package org.reactome.addlinks.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.brenda.BRENDAFileRetriever;
import org.reactome.addlinks.dataretrieval.brenda.BRENDASoapClient;
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
		brendaRetriever.setIdentifiers(Arrays.asList("Q8N5Z0", "O00116"));
		brendaRetriever.setSpeciesName("Homo sapiens");
		brendaRetriever.setFetchDestination(brendaRetriever.getFetchDestination().replace(".csv",".Homo_sapiens.csv"));
		brendaRetriever.fetchData();
		assertTrue( Files.size(Paths.get(new URI("file://"+brendaRetriever.getFetchDestination())) ) > 0);
	}
	
	@Test
	public void testBrendaRetrieverBigList() throws Exception
	{
		String speciesName = "Arabidopsis thaliana";
		String uniProtID = objectCache.getRefDbNamesToIds().get("UniProt").get(0);
		String speciesId = objectCache.getSpeciesNamesToIds().get(speciesName).get(0);
		brendaRetriever.setIdentifiers(
		objectCache.getByRefDbAndSpecies(uniProtID, speciesId, "ReferenceGeneProduct").stream().map( (inst) -> { try
			{
				return ((String)inst.getAttributeValue(ReactomeJavaConstants.identifier)) ;
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}} ).collect(Collectors.toList())
		);
		brendaRetriever.setSpeciesName(speciesName);
		brendaRetriever.setFetchDestination(brendaRetriever.getFetchDestination().replace(".csv","."+speciesName+".csv"));
		brendaRetriever.fetchData();
		assertTrue( Files.size(Paths.get(new URI("file://"+brendaRetriever.getFetchDestination())) ) > 0);
	}
	
	@Test
	public void testBrendaRetriever2() throws Exception
	{
		brendaRetriever.setIdentifiers(Arrays.asList("F4HU51", "O00116"));
		brendaRetriever.setSpeciesName("Arabidopsis thaliana");
		brendaRetriever.setFetchDestination(brendaRetriever.getFetchDestination().replace(".csv",".Arabidopsis_thaliana.csv"));
		brendaRetriever.fetchData();
		assertTrue( Files.size(Paths.get(new URI("file://"+brendaRetriever.getFetchDestination())) ) > 0);
	}
	
	@Test
	public void testBrendaProcessor() throws Exception
	{
		brendaRetriever.setIdentifiers(Arrays.asList("Q8N5Z0", "O00116"));
		brendaRetriever.setSpeciesName("Homo sapiens");
		brendaRetriever.setFetchDestination(brendaRetriever.getFetchDestination().replace(".csv",".Homo_sapiens.csv"));
		brendaRetriever.fetchData();
		
		Map<String, List<String>> mappings = brendaProcessor.getIdMappingsFromFile();

		System.out.println(mappings);
		
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
	}
	
	@Test
	public void testBrendaReferenceCreator() throws Exception
	{
		
		brendaRetriever.setIdentifiers(Arrays.asList("Q8N5Z0", "O00116"));
		brendaRetriever.setSpeciesName("Homo sapiens");
		brendaRetriever.setFetchDestination(brendaRetriever.getFetchDestination().replace(".csv",".Homo_sapiens.csv"));
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
		BRENDASoapClient client = new BRENDASoapClient(brendaRetriever.getUserName(), brendaRetriever.getPassword());
		
		// TODO: Maybe move this out to a BRENDASpeciesCache class. 
		String speciesResult = client.callBrendaService(brendaRetriever.getDataURL().toString(), "getOrganismsFromOrganism", "");
		//Normalize the list.
		List<String> brendaSpecies = Arrays.asList(speciesResult.split("!")).stream().map(species -> species.replace("'", "").replaceAll("\"", "").trim().toUpperCase() ).collect(Collectors.toList());
		System.out.println(brendaSpecies.size() + " species known to BRENDA");

		List<String> identifiers = new ArrayList<String>();
		String originalDestination = brendaRetriever.getFetchDestination();
		//for (String speciesId : objectCache.getSpeciesNamesByID().keySet())
		for (String speciesName : objectCache.getSetOfSpeciesNames().stream().sorted().collect(Collectors.toList() ) )
		{
			//String speciesName = objectCache.getSpeciesNamesByID().get(speciesId).get(0);
			String speciesId = objectCache.getSpeciesNamesToIds().get(speciesName).get(0);
			
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
				identifiers.addAll(uniprotIdentifiers);
				
				if (uniprotIdentifiers != null && uniprotIdentifiers.size() > 0)
				{
					brendaRetriever.setSpeciesName(speciesName);
					brendaRetriever.setIdentifiers(uniprotIdentifiers.subList(0, Math.min(100, uniprotIdentifiers.size())));
					brendaRetriever.setFetchDestination(originalDestination.replace(".csv","."+speciesName.replace(" ", "_")+".csv"));
					brendaRetriever.fetchData();
				}
				else
				{
					System.out.println("No uniprot identifiers for " + speciesName);
				}
			}
			else
			{
				System.out.println("Species " + speciesName + " is not in the list of species known to BRENDA.");
			}
		}
		
		
		Map<String, List<String>> mappings = brendaProcessor.getIdMappingsFromFile();

		//System.out.println(mappings);
		
		assertNotNull(mappings);
		
		assertTrue(mappings.keySet().size() > 0);
		
		List<GKInstance> sourceReferences = objectCache.getByRefDb( objectCache.getRefDbNamesToIds().get("UniProt").get(0) , ReactomeJavaConstants.ReferenceGeneProduct);
		
		brendaRefCreator.createIdentifiers(123465L, mappings, sourceReferences);
		
	}
}
