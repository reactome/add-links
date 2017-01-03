package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.dataretrieval.RHEAFileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestRHEA
{
	@Autowired
	ReferenceObjectCache objectCache;
	
	@Autowired
	RHEAFileRetriever rheaRetriever;
	
	@Test
	public void testGetRheaFiles()
	{
		List<GKInstance> chebis = objectCache.getByRefDb( objectCache.getRefDbNamesToIds().get("ChEBI").get(0) , ReactomeJavaConstants.ReferenceMolecule);
		List<String> chebiIdentifiers = chebis.stream().map(chebi -> {
			try
			{
				return (String)chebi.getAttributeValue(ReactomeJavaConstants.identifier);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new Error(e);
			}
		}).collect(Collectors.toList());
		assertTrue(chebiIdentifiers.size() > 0);
		rheaRetriever.setChEBIList(chebiIdentifiers);
		try
		{
			rheaRetriever.fetchData();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
