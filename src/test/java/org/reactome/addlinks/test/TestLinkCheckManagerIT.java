package org.reactome.addlinks.test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.linkchecking.LinkCheckInfo;
import org.reactome.addlinks.linkchecking.LinkCheckManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestLinkCheckManagerIT
{

	@Autowired
	LinkCheckManager linkCheckManager;
	
	@Autowired
	MySQLAdaptor dbAdapter;
	
	@Autowired
	ReferenceObjectCache objectCache;
	
	@Test
	/**
	 * This test method can be used to test link-checking for a *specific* ReferenceDatabase and a *specfic* entity. 
	 * NOTE: You must be certain that the ReferenceDatabase and ReferenceEntity exist before running
	 * this test method or an exception WILL be thrown. If you plan to run unit tests in batch mode
	 * for all of AddLinks, you should probably disable this specific test.
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	public void testLinkCheckManager() throws InvalidAttributeException, Exception
	{
		final String refDBName = "NCBI dbSNP";
		final String entityDBID = "100525522"; //"10787100"; // "100525522" - a different entity to test.
		final float proportionToCheck = 1.0f;
		final int maxToCheck = 20;
		// Get the Attribute
		SchemaAttribute att = this.dbAdapter.fetchSchema().getClassByName(ReactomeJavaConstants.ReferenceEntity).getAttribute(ReactomeJavaConstants.referenceDatabase);
		if (!this.objectCache.getRefDbNamesToIds().containsKey(refDBName))
		{
			throw new Exception("You tried to run the test with the ReferenceDatabase \""+refDBName+"\" but that does not exist in the database. Please create this reference database before attempting to link-check it.");
		}
		String refDbId = this.objectCache.getRefDbNamesToIds().get(refDBName).get(0);
		

		// Get a single instance based on the RefDBID
		GKInstance refDBInst = this.dbAdapter.fetchInstance(Long.parseLong(refDbId));
		final int size = 1;

		@SuppressWarnings("unchecked")
		List<GKInstance> instList = (List<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDNASequence, ReactomeJavaConstants.DB_ID, " = ",entityDBID); 
		
		if (instList.size() == 0)
		{
			throw new Exception("You tried to run the test with a ReferenceEntity whose DB ID ("+entityDBID+") was not in the database. Please ensure that the DB ID exists before running this test.");
		}
		
		Map<String, LinkCheckInfo> results = this.linkCheckManager.checkLinks(refDBInst, instList , proportionToCheck, maxToCheck);
		assertTrue(results.keySet().size() >= size);
		
		for(String k : results.keySet())
		{
			assertTrue(results.get(k).isKeywordFound());
			System.out.println(results.get(k));
		}
	}
	
	/**
	 * Test ZINC links. Specifically will test with "ZINC - Substances". Please ensure that 
	 * this reference database exists BEFORE running this test.
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	@Test
	public void testLinkCheckManagerZinc() throws InvalidAttributeException, Exception
	{
		final String refDBName = "ZINC - Substances";
		if (this.objectCache.getRefDbNamesToIds().get(refDBName) == null)
		{
			throw new Exception("Please ensure that the reference databse \""+refDBName+"\" exists before running this test.");
		}

		String dbID = this.objectCache.getRefDbNamesToIds().get(refDBName).get(0);
		GKInstance db = this.dbAdapter.fetchInstance(Long.valueOf(dbID));
		
		@SuppressWarnings("unchecked")
		Collection<GKInstance> instances = (Collection<GKInstance>) db.getReferers(ReactomeJavaConstants.referenceDatabase);
		assertTrue(instances.size() > 0);
		List<GKInstance> instList = (List<GKInstance>) instances;
		Map<String, LinkCheckInfo> results = this.linkCheckManager.checkLinks( instList.subList(0, 3) );
		assertTrue(results.keySet().size() == 3);
		
		for(String k : results.keySet())
		{
			assertTrue(results.get(k).isKeywordFound());
			System.out.println(results.get(k));
		}
	}
	
	/**
	 * Tests links for 10 randomly selected ReferenceEntities that have UniProt identifiers.
	 * Should always be safe to run this test.
	 * @throws Exception
	 */
	@Test
	public void testLinkCheckManager2() throws Exception
	{
		GKInstance refDBInst = this.dbAdapter.fetchInstance( Long.valueOf(this.objectCache.getRefDbNamesToIds().get("UniProt").get(0)) );
		SchemaAttribute att1 = this.dbAdapter.fetchSchema().getClassByName("ReferenceEntity").getAttribute("referenceDatabase");
		List<GKInstance> instances = new ArrayList<GKInstance> (this.dbAdapter.fetchInstanceByAttribute(att1 , " = ", refDBInst.getDBID()));
		float proportionToCheck = 0.25f;
		int maxToCheck = 10;
		
		Map<String, LinkCheckInfo> results = this.linkCheckManager.checkLinks(refDBInst, instances, proportionToCheck, maxToCheck);
		assertTrue(results.keySet().size() == 10);
		for(String k : results.keySet())
		{
			assertTrue(results.get(k).isKeywordFound());
			System.out.println(results.get(k));
		}
	}
	
	/**
	 * Test KEGG links. Please ensure that "KEGG Gene (Homo sapiens)" and "KEGG Gene (Danio rerio)"
	 * exist as reference databases before running this test.
	 * @throws Exception
	 */
	@Test
	public void testLinkCheckManagerKEGG() throws Exception
	{
		final String humanSpeciesID = "48887";
		final String otherSpeciedID = "68323";
		
		String refDBName = "KEGG Gene (Homo sapiens)";
		if (this.objectCache.getRefDbNamesToIds().get(refDBName) == null)
		{
			throw new Exception("Please ensure that \""+refDBName+"\" exists before running this test.");
		}
		GKInstance refDBInst = this.dbAdapter.fetchInstance( Long.valueOf(this.objectCache.getRefDbNamesToIds().get(refDBName).get(0)) );
		SchemaAttribute att1 = this.dbAdapter.fetchSchema().getClassByName(ReactomeJavaConstants.ReferenceDNASequence).getAttribute(ReactomeJavaConstants.referenceDatabase);
		SchemaAttribute att2 = this.dbAdapter.fetchSchema().getClassByName(ReactomeJavaConstants.ReferenceDNASequence).getAttribute(ReactomeJavaConstants.species);
		AttributeQueryRequest aqr1 = this.dbAdapter.new AttributeQueryRequest(att1, "=", refDBInst.getDBID());
		AttributeQueryRequest aqr2 = this.dbAdapter.new AttributeQueryRequest(att2, "=", humanSpeciesID);
		List<GKInstance> instances = new ArrayList<GKInstance> (this.dbAdapter._fetchInstance(Arrays.asList(aqr1, aqr2)));
		System.out.println("Number of instances found: " + instances.size());
		float proportionToCheck = 0.25f;
		int maxToCheck = 20;
		
		Map<String, LinkCheckInfo> results = this.linkCheckManager.checkLinks(refDBInst, instances, proportionToCheck, maxToCheck);
		System.out.println("Number of results: "+results.size());
		assertTrue(results.keySet().size() == 20);
		for(String k : results.keySet())
		{
			assertTrue(results.get(k).isKeywordFound());
			System.out.println(results.get(k));
		}
		
		refDBName = "KEGG Gene (Danio rerio)";
		if (this.objectCache.getRefDbNamesToIds().get(refDBName) == null)
		{
			throw new Exception("Please ensure that \""+refDBName+"\" exists before running this test.");
		}
		refDBInst = this.dbAdapter.fetchInstance( Long.valueOf(this.objectCache.getRefDbNamesToIds().get(refDBName).get(0)) );
		aqr1 = this.dbAdapter.new AttributeQueryRequest(att1, "=", refDBInst.getDBID());
		aqr2 = this.dbAdapter.new AttributeQueryRequest(att2, "=", otherSpeciedID);
		instances.clear();
		instances = new ArrayList<GKInstance> (this.dbAdapter._fetchInstance(Arrays.asList(aqr1, aqr2)));
		System.out.println("Number of instances found: " + instances.size());
		proportionToCheck = 0.25f;
		maxToCheck = 20;
		results.clear();
		results = this.linkCheckManager.checkLinks(refDBInst, instances, proportionToCheck, maxToCheck);
		System.out.println("Number of results: "+results.size());
		assertTrue(results.keySet().size() == 20);
		for(String k : results.keySet())
		{
			assertTrue(results.get(k).isKeywordFound());
			System.out.println(results.get(k));
		}
		
		// Check weird identfiers, such ones that contain a non-species prefix, such as "si:" 
		instances.clear();
		SchemaAttribute att3 = this.dbAdapter.fetchSchema().getClassByName(ReactomeJavaConstants.ReferenceDNASequence).getAttribute(ReactomeJavaConstants.identifier);
		AttributeQueryRequest aqr3 = this.dbAdapter.new AttributeQueryRequest(att3, "LIKE", "si:%");
		instances = new ArrayList<GKInstance> (this.dbAdapter._fetchInstance(Arrays.asList(aqr1, aqr2, aqr3)));
		System.out.println("Number of instances found: " + instances.size());
		proportionToCheck = 0.5f;
		maxToCheck = 20;
		results.clear();
		results = this.linkCheckManager.checkLinks(refDBInst, instances, proportionToCheck, maxToCheck);
		System.out.println("Number of results: "+results.size());
		assertTrue(results.keySet().size() > 0);
		for(String k : results.keySet())
		{
			assertTrue(results.get(k).isKeywordFound());
			System.out.println(results.get(k));
		}
	}
	
	/**
	 * Tests all links for all ReferenceDatabases that exist. Should always be safe to run.
	 * @throws InvalidAttributeException
	 * @throws Exception
	 */
	@Test
	public void testLinkCheckManagerAllDBs() throws InvalidAttributeException, Exception
	{
		SchemaAttribute att1 = this.dbAdapter.fetchSchema().getClassByName(ReactomeJavaConstants.ReferenceEntity).getAttribute(ReactomeJavaConstants.referenceDatabase);
		SchemaAttribute att2 = this.dbAdapter.fetchSchema().getClassByName(ReactomeJavaConstants.DatabaseIdentifier).getAttribute(ReactomeJavaConstants.referenceDatabase);
		
		List<SchemaAttribute> attributes = new ArrayList<>(2);
		attributes.add(att2);
		attributes.add(att1);
		
		for (SchemaAttribute att : attributes)
		{
			System.out.println("Trying attribute " + att.getName() + " from class " +att.getSchemaClass());
			Set<String> dbsToExclude = new HashSet<>();
			// A list of ReferenceDatabases that are difficult to link-check. The reasons vary:
			// Some load their pages with JavaScript so you can't actually check for the identifier. Some don't actually display the identifier (they display their own internal identifier, or the Name of the entity).
			// Some don't respond well to programmatic access (it seems they can detect non-browser access and reject it). See notes in src/main/resources/application-context.xml for details.
			dbsToExclude.addAll(Arrays.asList("OpenTargets", "HGNC", "DOCK Blaster", "UCSC", "BioGPS", "GeneCards", "OMIM", "ComplexPortal", "PRO"));
			for (String refDBID : this.objectCache.getRefDBMappings().keySet().stream().filter(refDBName -> { return !dbsToExclude.contains(refDBName); }).collect(Collectors.toList()) )
			{
				System.out.println(">> Now checking links for " + this.objectCache.getRefDBMappings().get(refDBID));
				
				@SuppressWarnings("unchecked")
				Set<GKInstance> instances = (HashSet<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(att , " = ", refDBID);
				
				List<GKInstance> instList = new ArrayList<>(instances);
				if (instList.size() > 0)
				{
					Map<String, LinkCheckInfo> results = this.linkCheckManager.checkLinks( instList.subList(0, (instList.size() >= 3 ? 3 : instList.size()) ) );
					//assertTrue(results.keySet().size() == 3);
					
					for(String k : results.keySet())
					{
						//assertTrue(results.get(k).isKeywordFound());
						System.out.println(results.get(k));
					}
				}
				else
				{
					System.out.println("No instances for " + refDBID + " / " + this.objectCache.getRefDBMappings().get(refDBID) );
				}
			}
		}
	}
}
