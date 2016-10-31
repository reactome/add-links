package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.db.ReferenceCreator;

@PowerMockIgnore({"javax.management.*","javax.net.ssl.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({ java.net.URI.class,
				org.apache.commons.net.ftp.FTPClient.class,
				org.reactome.addlinks.db.ReferenceCreator.class,
				org.apache.http.impl.client.HttpClients.class })
public class TestReferenceCreator
{

	
	@Test
	public void testCreateNewIdentifier() throws SQLException
	{
		try
		{
			String identifier = "NEWIDENTIFIER";
			MySQLAdaptor adapter = new MySQLAdaptor("localhost", "test_reactome_58","root","", 3306);
			ReferenceCreator creator = new ReferenceCreator(identifier, adapter);
			creator.createIdentifier("attribute", identifier, "", "FlyBase", 12345, this.getClass().getName());
			
			
			// Now assert that the object was created properly.
			Collection<GKInstance> instances = adapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceGeneProduct, ReactomeJavaConstants.identifier, "=", identifier);
			System.out.println(instances.size());
			assertTrue(instances.size() == 1);
			System.out.println(instances.iterator().next());
		}
		catch (Exception e)
		{
			e.printStackTrace();
			assert(false);	
		}
			
		assert(true);
	}
}
