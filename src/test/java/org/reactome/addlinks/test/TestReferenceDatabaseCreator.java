package org.reactome.addlinks.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.FileInputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.junit.Before;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.reactome.addlinks.db.ReferenceDatabaseCreator;
import org.reactome.addlinks.db.ReferenceObjectCache;
import org.reactome.addlinks.ensembl.EnsemblReferenceDatabaseGenerator;
import org.reactome.addlinks.kegg.KEGGReferenceDatabaseGenerator;
import org.springframework.test.context.ContextConfiguration;


@PowerMockIgnore({"javax.management.*","javax.net.ssl.*","javax.security.*"})
@PrepareForTest({ org.reactome.addlinks.db.ReferenceDatabaseCreator.class,
				org.reactome.addlinks.ensembl.EnsemblReferenceDatabaseGenerator.class })
public class TestReferenceDatabaseCreator
{
	
	MySQLAdaptor dbAdapter;

	private long personID;
	
	@Before
	public void setup() throws Exception
	{
		if (dbAdapter==null)
		{
			Properties props = new Properties();
			props.load(new FileInputStream("src/test/resources/db.properties"));
			dbAdapter=new MySQLAdaptor(props.getProperty("database.host"), props.getProperty("database.name"),
									props.getProperty("database.user", "root"),props.getProperty("database.password", "root"),
									Integer.parseInt(props.getProperty("database.port","3306")));
		}
		// Get a person. Doesn't *really* matter which person.
		ResultSet rs = this.dbAdapter.executeQuery("SELECT min(DB_ID) FROM Person;", null);
		rs.next();
		this.personID = rs.getLong(1);
	}
	
	@Test
	public void testCreateRefDBWithAliases() throws SQLException, InvalidAttributeException, Exception
	{
		String refDBName = "TestReferenceDatabase";
		String refDBAlias1 = "TestRefDB_alias_1";
		String refDBAlias2 = "TestRefDB_alias_2";
		String refDBUrl = "http://test.reference.database/";
		String refDBAccessUrl = "http://test.reference.database/?##ID##";
		
		SchemaAttribute refDBNameAttrib = dbAdapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase).getAttribute(ReactomeJavaConstants.name);
		@SuppressWarnings("unchecked")
		Collection<GKInstance> instances = (Collection<GKInstance>)dbAdapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		
		//This test should clean itself up, but in case it doesn't work, delete the instances if they DO exist.
		if (instances != null)
		{
			for (GKInstance instance : instances)
			{
				System.out.println("Deleting pre-existing instance: "+instance.getDBID()+" "+instance.getDisplayName());
				dbAdapter.deleteInstance(instance);
			}
		}
		
		ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(dbAdapter, this.personID);
		creator.createReferenceDatabaseWithAliases(refDBUrl, refDBAccessUrl, refDBName, refDBAlias1, refDBAlias2);
		// Now that we've created the instance, let's verify that it is there.
		instances = (Collection<GKInstance>)dbAdapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		System.out.println(instances);
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
					assertTrue(name.equals(refDBName) || name.equals(refDBAlias1) || name.equals(refDBAlias2));
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
			// Now check InstanceEdits
			try
			{
				GKInstance created = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
				assertNotNull(created);
				System.out.println(created.toString());
				@SuppressWarnings("unchecked")
				Collection<GKInstance> modifieds = (Collection<GKInstance>) instance.getAttributeValuesList(ReactomeJavaConstants.modified);
				assertNotNull(modifieds);
				// Since we gave the aliases at the same time we created the ReferenceDatabase, there should not be any "modified" instances.
				assertTrue(modifieds.size() == 0);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});
		for (GKInstance instance : instances)
		{
			// If everything was OK, let's delete the instance (don't want to clutter up the database.
			dbAdapter.deleteInstance(instance);
		}
	}

	@Test
	public void testCreateReferenceDatabaseToUrl() throws Exception
	{
		ReferenceDatabaseCreator refDBCreator = new ReferenceDatabaseCreator(this.dbAdapter, this.personID);
		String refDBName = "TestRefDB";
		// there might be residuals from failed tests, so delete them.
		Collection<GKInstance> preexistingRefDBs = this.dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", refDBName);
		
		for(GKInstance inst : preexistingRefDBs)
		{
			this.dbAdapter.deleteByDBID(inst.getDBID());
		}
		
		long newRefDBID = refDBCreator.createReferenceDatabaseToURL("http://www.test.com", "http://www.test.com/id=###ID###", refDBName, "test_alias_1", "test_alias_2");
		long newRefDBDID2 = refDBCreator.createReferenceDatabaseToURL("http://www.test.com", "http://www.test.com/id=###ID###", refDBName, "test_alias_3", "test_alias_4");
		
		System.out.println(newRefDBID + " " + newRefDBDID2 +";");
		assertEquals(newRefDBID, newRefDBDID2);
		
		GKInstance refDB = this.dbAdapter.fetchInstance(newRefDBID);
		assertNotNull(refDB);
		List<GKInstance> modifieds = (ArrayList<GKInstance>) refDB.getAttributeValuesList(ReactomeJavaConstants.modified);
		
		assertTrue(modifieds.size() > 0);
		
		for (GKInstance m : modifieds)
		{
			System.out.println(m.toStanza());
		}
		
		// delete everyting after the test.
		Collection<GKInstance> postexistingRefDBs = this.dbAdapter.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", refDBName);
		for(GKInstance inst : postexistingRefDBs)
		{
			this.dbAdapter.deleteByDBID(inst.getDBID());
		}
	}
	
	@Test
	public void testCreatePreexistingRefDBWithAliases() throws SQLException, InvalidAttributeException, Exception
	{
		
		String refDBName = "TestReferenceDatabase";
		String refDBAlias1 = "TestRefDB_alias_1";
		String refDBAlias2 = "TestRefDB_alias_2";
		String refDBAlias3 = "TestRefDB_alias_3";
		String refDBAlias4 = "TestRefDB_alias_4";
		String refDBUrl = "http://test.reference.database/";
		String refDBAccessUrl = "http://test.reference.database/?##ID##";
		
		SchemaAttribute refDBNameAttrib = dbAdapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase).getAttribute(ReactomeJavaConstants.name);
		@SuppressWarnings("unchecked")
		Collection<GKInstance> instances = (Collection<GKInstance>)dbAdapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		
		//This test should clean itself up, but in case it doesn't work, delete the instances if they DO exist.
		if (instances != null)
		{
			for (GKInstance instance : instances)
			{
				System.out.println("Deleting pre-existing instance: "+instance.getDBID()+" "+instance.getDisplayName());
				dbAdapter.deleteInstance(instance);
			}
		}
		
		ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(dbAdapter, this.personID);
		creator.createReferenceDatabaseWithAliases(refDBUrl, refDBAccessUrl, refDBName, refDBAlias1, refDBAlias2);
		creator.createReferenceDatabaseWithAliases(refDBUrl, refDBAccessUrl, refDBName, refDBAlias3, refDBAlias4);
		// Now that we've created the instance, let's verify that it is there.
		instances = (Collection<GKInstance>)dbAdapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		System.out.println(instances);
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
					assertTrue(name.equals(refDBName) || name.equals(refDBAlias1) || name.equals(refDBAlias2));
				}
				// Now check InstanceEdits
				GKInstance created = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
				assertNotNull(created);
				System.out.println(created.toString());
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
			dbAdapter.deleteInstance(instance);
		}
	}
	
	@Test
	public void testCreateSimpleReferenceDatabase() throws SQLException, InvalidAttributeException, Exception
	{
		
		String refDBName = "TestReferenceDatabase";
		String refDBAlias = "TestRefDB";
		String refDBUrl = "http://test.reference.database/";
		String refDBAccessUrl = "http://test.reference.database/?##ID##";
		
		SchemaAttribute refDBNameAttrib = dbAdapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase).getAttribute(ReactomeJavaConstants.name);
		@SuppressWarnings("unchecked")
		Collection<GKInstance> instances = (Collection<GKInstance>)dbAdapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		
		//This test should clean itself up, but in case it doesn't work, delete the instances if they DO exist.
		if (instances != null)
		{
			for (GKInstance instance : instances)
			{
				System.out.println("Deleting pre-existing instance: "+instance.getDBID()+" "+instance.getDisplayName());
				dbAdapter.deleteInstance(instance);
			}
		}
		
		ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(dbAdapter, this.personID);
		creator.createReferenceDatabaseToURL(refDBUrl, refDBAccessUrl, refDBName, refDBAlias);
		// Now that we've created the instance, let's verify that it is there.
		instances = (Collection<GKInstance>)dbAdapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
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
			dbAdapter.deleteInstance(instance);
		}
		
	}
	
	@Test
	public void testPreexistingReferenceDatabaseCreation() throws SQLException, InvalidAttributeException, Exception
	{
		
		String refDBName = "TestReferenceDatabase";
		String refDBAlias = "TestRefDB";
		String refDBUrl = "http://test.reference.database/";
		String refDBAccessUrl = "http://test.reference.database/?##ID##";
		SchemaAttribute refDBNameAttrib = dbAdapter.getSchema().getClassByName(ReactomeJavaConstants.ReferenceDatabase).getAttribute(ReactomeJavaConstants.name);
		@SuppressWarnings("unchecked")
		Collection<GKInstance> instances = (Collection<GKInstance>)dbAdapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
		
		//This test should clean itself up, but in case it doesn't work, delete the instances if they DO exist.
		if (instances != null)
		{
			for (GKInstance instance : instances)
			{
				System.out.println("Deleting pre-existing instance: "+instance.getDBID()+" "+instance.getDisplayName());
				dbAdapter.deleteInstance(instance);
			}
		}
		
		ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(dbAdapter, this.personID);
		creator.createReferenceDatabaseToURL(refDBUrl, refDBAccessUrl, refDBName, refDBAlias);
		
		//Now, let's try to create another ReferenceDatabase, with one name that matches the first one.
		String refDBAlias2 = "Test_Ref_DB_2";
		creator.createReferenceDatabaseToURL(refDBUrl, refDBAccessUrl, refDBName, refDBAlias2);
		
		instances = (Collection<GKInstance>) dbAdapter.fetchInstanceByAttribute(refDBNameAttrib, "=", refDBName);
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
			dbAdapter.deleteInstance(instance);
		}
	}
	
	@Test
	public void testEnsemblRefDBCreator() throws SQLException, InvalidAttributeException, Exception
	{
		try
		{
			ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(dbAdapter, this.personID);
			ReferenceObjectCache objectCache = new ReferenceObjectCache(dbAdapter);
			EnsemblReferenceDatabaseGenerator.setDbCreator(creator);
			EnsemblReferenceDatabaseGenerator.generateSpeciesSpecificReferenceDatabases(objectCache );
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void testKEGGRefDBCreator()
	{
		try
		{
			ReferenceObjectCache objectCache = new ReferenceObjectCache(dbAdapter);
			ReferenceDatabaseCreator creator = new ReferenceDatabaseCreator(dbAdapter, this.personID);
			KEGGReferenceDatabaseGenerator.setDBCreator(creator);
			KEGGReferenceDatabaseGenerator.setDBAdaptor(dbAdapter);
			KEGGReferenceDatabaseGenerator.generateSpeciesSpecificReferenceDatabases(objectCache);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
}
