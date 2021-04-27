package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.release.common.dataretrieval.cosmic.COSMICFileRetriever;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.COSMICFileProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.reactome.addlinks.referencecreators.COSMICReferenceCreator;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestCOSMIC
{

	@Autowired
	MySQLAdaptor dbAdapter;

	@Autowired
	ReferenceObjectCache objectCache;

	//@Autowired
	//FileRetriever COSMICRetriever;

	@Autowired
	COSMICFileProcessor COSMICProcessor;

	@Autowired
	COSMICReferenceCreator COSMICReferenceCreator;

	@Autowired
	COSMICFileRetriever COSMICFileRetriever;

	@Test
	public void testCOSMICDataRetrievalIT()
	{
		try
		{
			COSMICFileRetriever.fetchData();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testCOSMICFileProcessorIT()
	{
		Map<String,String> mapping = this.COSMICProcessor.getIdMappingsFromFile();
		assertTrue(mapping.keySet().size() > 0);
		System.out.println(mapping);
	}

	@Test
	public void testCOSMICReferenceCreationIT()
	{
		Map<String,String> mapping = this.COSMICProcessor.getIdMappingsFromFile();
		assertTrue(mapping.keySet().size() > 0);

		List<GKInstance> sourceReferences = this.objectCache.getBySpecies("48887", "ReferenceGeneProduct");

		try
		{
			this.COSMICReferenceCreator.createIdentifiers(1234, mapping, sourceReferences);
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail();
		}
	}
}
