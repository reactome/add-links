package org.reactome.addlinks.test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
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
		
		String value = objectCache.getRefDbNamesToIds().get("UniProt").get(0);
		
		@SuppressWarnings("unchecked")
		Set<GKInstance> instances = (HashSet<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(att , " = ", value);
		List<GKInstance> instList = new ArrayList<GKInstance>(instances);
		Map<String, LinkCheckInfo> results = this.linkCheckManager.checkLinks( instList.subList(0, 3) );
		assertTrue(results.keySet().size() == 3);
		
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
		
		List<SchemaAttribute> attributes = new ArrayList<SchemaAttribute>(2);
		attributes.add(att2);
		attributes.add(att1);
		//String value = objectCache.getRefDbNamesToIds().get("UniProt").get(0);
		
		for (SchemaAttribute att : attributes)
		{
			System.out.println("Trying attribute " + att.getName() + " from class " +att.getSchemaClass());
			for (String refDBID : objectCache.getRefDBMappings().keySet())
			{
				System.out.println(">> Now checking links for " + objectCache.getRefDBMappings().get(refDBID));
				
				@SuppressWarnings("unchecked")
				Set<GKInstance> instances = (HashSet<GKInstance>) this.dbAdapter.fetchInstanceByAttribute(att , " = ", refDBID);
				
				List<GKInstance> instList = new ArrayList<GKInstance>(instances);
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
					System.out.println("No instances for " + refDBID + " / " + objectCache.getRefDBMappings().get(refDBID) );
				}
			}
		}
	}
}
