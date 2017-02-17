package org.reactome.addlinks.test;

import static org.junit.Assert.*;


import java.sql.SQLException;
import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.ensembl.EnsemblReferenceDatabaseGenerator;


@PowerMockIgnore({"javax.management.*","javax.net.ssl.*","javax.security.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({ org.reactome.addlinks.db.ReferenceDatabaseCreator.class,
				org.reactome.addlinks.ensembl.EnsemblReferenceDatabaseGenerator.class })
public class TestReferenceDatabaseCreator
{

	
	@Test
	public void testCreateSimpleReferenceDatabase() throws SQLException, InvalidAttributeException, Exception
	{
		MySQLAdaptor adapter = null;
		adapter = new MySQLAdaptor("localhost", "test_reactome_58","root","", 3306);
		
		String refDBName = "TestReferenceDatabase";
		String refDBAlias = "TestRefDB";
		String refDBUrl = "http://test.reference.database/";
		String refDBAccessUrl = "http://test.reference.database/?##ID##";
		
		SchemaAttribute refDBNameAttrib = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase).getAttribute(ReactomeJavaConstants.name);
		@SuppressWarnings("unchecked")
		Collection<GKInstance> instances = (Collection<GKInstance>)adapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		
		//This test should clean itself up, but in case it doesn't work, delete the instances if they DO exist.
		if (instances != null)
		{
			for (GKInstance instance : instances)
			{
				System.out.println("Deleting pre-existing instance: "+instance.getDBID()+" "+instance.getDisplayName());
				adapter.deleteInstance(instance);
			}
		}
		
		ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(adapter);
		creator.createReferenceDatabase(refDBUrl, refDBAccessUrl, refDBName, refDBAlias);
		// Now that we've created the instance, let's verify that it is there.
		instances = (Collection<GKInstance>)adapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		assertTrue(instances.size() == 1);
		instances.forEach( instance -> {
			System.out.println("Checking instance: "+instance.getDBID()+" "+instance.getDisplayName());
			try
			{
				assertEquals(instance.getAttributeValue(ReactomeJavaConstants.url),refDBUrl);
				assertEquals(instance.getAttributeValue(ReactomeJavaConstants.accessUrl),refDBAccessUrl);
				@SuppressWarnings("unchecked")
				Collection<String> names = (Collection<String>)instance.getAttributeValuesList(ReactomeJavaConstants.name);
				assertTrue(names.size() == 2);
				for (String name : names)
				{
					System.out.println("checking name: "+name);
					assertTrue(name.equals(refDBName) || name.equals(refDBAlias));
				}
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
				fail();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				fail();
			}
		});
		for (GKInstance instance : instances)
		{
			// If everything was OK, let's delete the instance (don't want to clutter up the database.
			adapter.deleteInstance(instance);
		}
		
	}
	
	@Test
	public void testPreexistingReferenceDatabaseCreation() throws SQLException, InvalidAttributeException, Exception
	{
		MySQLAdaptor adapter = null;
		adapter = new MySQLAdaptor("localhost", "test_reactome_58","root","", 3306);
		
		String refDBName = "TestReferenceDatabase";
		String refDBAlias = "TestRefDB";
		String refDBUrl = "http://test.reference.database/";
		String refDBAccessUrl = "http://test.reference.database/?##ID##";
		SchemaAttribute refDBNameAttrib = adapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase).getAttribute(ReactomeJavaConstants.name);
		@SuppressWarnings("unchecked")
		Collection<GKInstance> instances = (Collection<GKInstance>)adapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		
		//This test should clean itself up, but in case it doesn't work, delete the instances if they DO exist.
		if (instances != null)
		{
			for (GKInstance instance : instances)
			{
				System.out.println("Deleting pre-existing instance: "+instance.getDBID()+" "+instance.getDisplayName());
				adapter.deleteInstance(instance);
			}
		}
		
		ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(adapter);
		creator.createReferenceDatabase(refDBUrl, refDBAccessUrl, refDBName, refDBAlias);
		
		//Now, let's try to create another ReferenceDatabase, with one name that matches the first one.
		String refDBAlias2 = "Test_Ref_DB_2";
		creator.createReferenceDatabase(refDBUrl, refDBAccessUrl, refDBName, refDBAlias2);
		
		instances = (Collection<GKInstance>) adapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		assertTrue(instances.size() == 1);
		instances.forEach( instance -> {
			System.out.println("Checking instance: "+instance.getDBID()+" "+instance.getDisplayName());
			try
			{
				assertEquals(instance.getAttributeValue(ReactomeJavaConstants.url),refDBUrl);
				assertEquals(instance.getAttributeValue(ReactomeJavaConstants.accessUrl),refDBAccessUrl);
				@SuppressWarnings("unchecked")
				Collection<String> names = (Collection<String>)instance.getAttributeValuesList(ReactomeJavaConstants.name);
				assertTrue(names.size() == 3);
				for (String name : names)
				{
					System.out.println("checking name: "+name);
					assertTrue(name.equals(refDBName) || name.equals(refDBAlias) || name.equals(refDBAlias2));
				}
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
				fail();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				fail();
			}
		});
		for (GKInstance instance : instances)
		{
			// If everything was OK, let's delete the instance (don't want to clutter up the database.
			adapter.deleteInstance(instance);
		}
	}
	
	@Test
	public void testEnsemblRefDBCreator() throws SQLException, InvalidAttributeException, Exception
	{
		MySQLAdaptor adapter = null;
		try
		{
			adapter = new MySQLAdaptor("localhost", "test_reactome_58","root","", 3306);
			ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(adapter);
			EnsemblReferenceDatabaseGenerator.setDbCreator(creator);
			EnsemblReferenceDatabaseGenerator.generateSpeciesSpecificReferenceDatabases();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
}
