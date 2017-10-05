package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	public void testCreateNewRefMolecule() throws SQLException, Exception
	{
		MySQLAdaptor adapter = null;
		try
		{
			String identifier = "NEWMOLECULE";
			adapter = new MySQLAdaptor("localhost", "test_reactome_58","root","", 3306);
			//adapter.debug = true;
			if (adapter.supportsTransactions())
			{
				adapter.startTransaction();
			}
			SchemaClass dbIdentifierClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.DatabaseIdentifier);
			SchemaClass refMoleculeClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceMolecule);
			GKSchemaAttribute refAttrib = (GKSchemaAttribute) refMoleculeClass.getAttribute(ReactomeJavaConstants.crossReference);
			
			ReferenceCreator creator = new ReferenceCreator(dbIdentifierClass, refMoleculeClass,  refAttrib, adapter);
			int personID = 8863762;
			
			String referenceMoleculeID = "5252032";
			String refDBName = "ChEBI";
			creator.createIdentifier(identifier, referenceMoleculeID,  refDBName, personID, this.getClass().getName());
			
			if (adapter.supportsTransactions())
			{
				adapter.commit();
			}
			
			//Ok now we have to verify that the thing we pulled out of the database is what we wanted.
			@SuppressWarnings("unchecked")
			Collection<GKInstance> instances = (Collection<GKInstance>)adapter.fetchInstanceByAttribute(ReactomeJavaConstants.DatabaseIdentifier, ReactomeJavaConstants.identifier, "=", identifier);
			System.out.println(instances.size());
			assertTrue(instances.size() == 1);
			GKInstance createdInstance = instances.iterator().next(); 
			System.out.println(createdInstance.getAttributeValue(ReactomeJavaConstants.identifier));
			
			String createdInstanceIdentifier = (String) createdInstance.getAttributeValue(ReactomeJavaConstants.identifier); 
			
			assertTrue(createdInstanceIdentifier.equals( identifier ));
			
			Object createdRefDB = createdInstance.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
			System.out.println(createdRefDB);
			
			String createdRefDBName = (((GKInstance)createdRefDB).getAttributeValue(ReactomeJavaConstants.name)).toString();
			
			assertTrue( createdRefDBName.toLowerCase().equals(refDBName.toLowerCase()) );
			
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
			
			GKInstance referenceMolecule = adapter.fetchInstance(Long.valueOf(referenceMoleculeID));
			
			System.out.println("ReferenceGene on the RefGeneProd: ");
			assertNotNull(referenceMolecule.getAttributeValue(ReactomeJavaConstants.crossReference));
			System.out.println( referenceMolecule.getAttributeValue(ReactomeJavaConstants.crossReference) );
		}
		catch (Exception e)
		{
			if (adapter !=null && adapter.supportsTransactions())
			{
				adapter.rollback();
			}
			e.printStackTrace();
			fail();
		}
		finally
		{
			if (adapter !=null)
			{
				adapter.cleanUp();
			}
		}
	}
	
	
	@Test
	public void testCreateNewIdentifier() throws SQLException, Exception
	{
		MySQLAdaptor adapter = null;
		try
		{
			String identifier = "NEWIDENTIFIER";
			adapter = new MySQLAdaptor("localhost", "test_reactome_58","root","", 3306);
			
			if (adapter.supportsTransactions())
			{
				adapter.startTransaction();
			}
			SchemaClass refDNASeqClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDNASequence);
			SchemaClass refGeneProdClass = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceGeneProduct);
			GKSchemaAttribute refAttrib = (GKSchemaAttribute) refGeneProdClass.getAttribute(ReactomeJavaConstants.referenceGene);
			ReferenceCreator creator = new ReferenceCreator(refDNASeqClass, refGeneProdClass, refAttrib, adapter);
			
			List<String> extraNames = new ArrayList<String>(3);
			extraNames.add("extra name 1");
			extraNames.add("extra name 2");
			extraNames.add("extra name 3");
			
			Map<String,List<String>> extraAttributes = new HashMap<String,List<String>>(1);
			
			extraAttributes.put(ReactomeJavaConstants.name, extraNames);
			
			int personID = 8863762;
			String referenceGeneProductID = "9604116";
			creator.createIdentifier(identifier, referenceGeneProductID, "FlyBase", personID, this.getClass().getName(),new Long(48887),extraAttributes);
			if (adapter.supportsTransactions())
			{
				adapter.commit();
			}
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
					System.out.println( ((GKSchemaAttribute)x).getName() +": "+ createdInstance.getAttributeValuesList((SchemaAttribute)x));
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
			if (adapter != null && adapter.supportsTransactions())
			{
				adapter.rollback();
			}
			fail();
		}
		finally
		{
			if (adapter != null)
			{
				adapter.cleanUp();
			}
		}
			
	}
}
