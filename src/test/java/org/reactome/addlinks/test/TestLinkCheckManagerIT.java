package org.reactome.addlinks.test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	public void testLinkCheckManager() throws InvalidAttributeException, Exception
	{
		SchemaAttribute att = this.dbAdapter.fetchSchema().getClassByName("ReferenceEntity").getAttribute("referenceDatabase");
		
		String refDbId = this.objectCache.getRefDbNamesToIds().get("NCBI dbSNP").get(0);
		
		@SuppressWarnings("unchecked")
		Set<GKInstance> instances = (HashSet<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(att , " = ", refDbId);
		List<GKInstance> instList = new ArrayList<>(instances);
		
//		dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceEntity, ReactomeJavaConstants.identifier, " = ", "100525522"); // a different entity to test.
		GKInstance refDBInst = this.dbAdapter.fetchInstance(Long.parseLong(refDbId));
		refDBInst.setAttributeValue(ReactomeJavaConstants.accessUrl, "https://www.ncbi.nlm.nih.gov/snp/###ID###");
		final int size = 1;
		instList = instList.subList(0, size);
//		instList.addAll(dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceEntity, ReactomeJavaConstants.identifier, " = ", "100525522")); // a different entity to test.
		instList.addAll(this.dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDNASequence, ReactomeJavaConstants.DB_ID, " = ","10787100") );
		Map<String, LinkCheckInfo> results = this.linkCheckManager.checkLinks(refDBInst, instList , 1.0f, 20);
		assertTrue(results.keySet().size() >= size);
		
		for(String k : results.keySet())
		{
			assertTrue(results.get(k).isKeywordFound());
			System.out.println(results.get(k));
		}
	}
	
	@Test
	public void testLinkCheckManagerZinc() throws InvalidAttributeException, Exception
	{
		String dbID = this.objectCache.getRefDbNamesToIds().get("ZINC - Substances").get(0);
		
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
	
	@Test
	public void testLinkCheckManagerKEGG() throws Exception
	{
		GKInstance refDBInst = this.dbAdapter.fetchInstance( Long.valueOf(this.objectCache.getRefDbNamesToIds().get("KEGG Gene (Homo sapiens)").get(0)) );
		SchemaAttribute att1 = this.dbAdapter.fetchSchema().getClassByName("ReferenceDNASequence").getAttribute("referenceDatabase");
		SchemaAttribute att2 = this.dbAdapter.fetchSchema().getClassByName("ReferenceDNASequence").getAttribute("species");

		AttributeQueryRequest aqr1 = this.dbAdapter.new AttributeQueryRequest(att1, "=", refDBInst.getDBID());
		AttributeQueryRequest aqr2 = this.dbAdapter.new AttributeQueryRequest(att2, "=", "48887");
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
		
		refDBInst = this.dbAdapter.fetchInstance( Long.valueOf(this.objectCache.getRefDbNamesToIds().get("KEGG Gene (Danio rerio)").get(0)) );
		aqr1 = this.dbAdapter.new AttributeQueryRequest(att1, "=", refDBInst.getDBID());
		aqr2 = this.dbAdapter.new AttributeQueryRequest(att2, "=", "68323");
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
		SchemaAttribute att3 = this.dbAdapter.fetchSchema().getClassByName("ReferenceDNASequence").getAttribute("identifier");
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
	
	@Test
	public void testLinkCheckManagerAllDBs() throws InvalidAttributeException, Exception
	{
		SchemaAttribute att1 = this.dbAdapter.fetchSchema().getClassByName("ReferenceEntity").getAttribute("referenceDatabase");
		SchemaAttribute att2 = this.dbAdapter.fetchSchema().getClassByName("DatabaseIdentifier").getAttribute("referenceDatabase");
		
		List<SchemaAttribute> attributes = new ArrayList<>(2);
		attributes.add(att2);
		attributes.add(att1);
		//String value = objectCache.getRefDbNamesToIds().get("UniProt").get(0);
		
		for (SchemaAttribute att : attributes)
		{
			System.out.println("Trying attribute " + att.getName() + " from class " +att.getSchemaClass());
			for (String refDBID : this.objectCache.getRefDBMappings().keySet())
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
