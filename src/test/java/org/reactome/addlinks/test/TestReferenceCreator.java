package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
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
			SchemaClass refDNASeqClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDNASequence);
			SchemaClass refGeneProdClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceGeneProduct);
			GKSchemaAttribute refAttrib = (GKSchemaAttribute) refGeneProdClass.getAttribute(ReactomeJavaConstants.referenceGene);
			ReferenceCreator creator = new ReferenceCreator(refDNASeqClass, refGeneProdClass, refAttrib, adapter);
			int personID = 8863762;
			String referenceGeneProductID = "9604116";
			creator.createIdentifier(identifier, referenceGeneProductID, "FlyBase", personID, this.getClass().getName());
			
			
			// Now assert that the object was created properly.
			@SuppressWarnings("unchecked")
			Collection<GKInstance> instances = (Collection<GKInstance>)adapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDNASequence, ReactomeJavaConstants.identifier, "=", identifier);
			System.out.println(instances.size());
			assertTrue(instances.size() == 1);
			GKInstance createdInstance = instances.iterator().next(); 
			System.out.println(createdInstance.getAttributeValue(ReactomeJavaConstants.identifier));
			
			String createdInstanceIdentifier = (String) createdInstance.getAttributeValue(ReactomeJavaConstants.identifier); 
			
			assertTrue(createdInstanceIdentifier.equals( identifier ));
			
			Object createdRefDB = createdInstance.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
			System.out.println(createdRefDB);
			
			String createdRefDBName = (((GKInstance)createdRefDB).getAttributeValue(ReactomeJavaConstants.name)).toString();
			
			assertTrue( createdRefDBName.toLowerCase().equals("flybase") );
			
			//System.out.println(createdInstance.getAttributeValue(ReactomeJavaConstants.referenceGene));

			System.out.println("Attributes: ");
			
			((Collection<GKSchemaAttribute>)createdInstance.getSchemaAttributes()).stream().forEach( x -> {
				try
				{
					System.out.println( ((GKSchemaAttribute)x).getName() +": "+ createdInstance.getAttributeValue((SchemaAttribute)x));
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			} );
			
			System.out.println("Referrers:");
			
			createdInstance.getReferers().keySet().forEach( x -> {
				try
				{
					System.out.println( x );
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			} );
			
			System.out.println("Instance: " + createdInstance);
			
			//Ok, now that we've verified that the new data is ok, let's make sure the relationship between it and the RefGeneProd exists.
			
			GKInstance referenceGeneProduct = adapter.fetchInstance(Long.valueOf(referenceGeneProductID));
			
			System.out.println("ReferenceGene on the RefGeneProd: ");
			assertNotNull(referenceGeneProduct.getAttributeValue(ReactomeJavaConstants.referenceGene));
			System.out.println( referenceGeneProduct.getAttributeValue(ReactomeJavaConstants.referenceGene) );
		}
		catch (Exception e)
		{
			e.printStackTrace();
			assert(false);	
		}
			
		assert(true);
	}
}
