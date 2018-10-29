package org.reactome.addlinks.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.fileprocessors.OpenTargetsFileProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
public class TestOpenTargets
{
	@Autowired
	ReferenceObjectCache objectCache;

	@Autowired
	MySQLAdaptor dbAdapter;
	
	@Autowired
	OpenTargetsFileProcessor openTargetsFileProcessor;
	
	@Test
	public void testOpenTargetsFileProcessor()
	{
		Map<String, String> mappings = openTargetsFileProcessor.getIdMappingsFromFile();
		assertNotNull(mappings);
		assertTrue(mappings.keySet().size() > 0);
	}
}
