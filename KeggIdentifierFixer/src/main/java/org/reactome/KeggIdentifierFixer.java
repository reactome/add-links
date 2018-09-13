package org.reactome;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.release.common.database.InstanceEditUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 * This program will fix KEGG identifiers that have a species prefix, by REMOVING the species prefix.
 * But... not all prefixes are species codes. Generally, if the identifier is of the form &lt;prefix&gt;:&lt;identifier&gt; 
 * where &lt;identifier&gt; is a <em>numeric</em> string, then &lt;prefix&gt; is a species code. If the identifier portion
 * is alphanumeric or contains other characters (such as punctuation characters), then the prefix is not a species
 * code and the species code was already correctly stripped out by AddLinks (but the SQL query is a bit more relaxed, though it could be tightened up).
 * <br/><br/>
 * For more info on KEGG identifiers, see: https://www.genome.jp/kegg/kegg3.html
 * @author sshorser
 *
 */
public class KeggIdentifierFixer
{

	protected static final Logger logger = LogManager.getLogger();
	
	// Query the database for all ReferenceEntities which are associated with a 
	// ReferenceDatabase object that has "KEGG" in the name and whose identifier
	// matches %:%
	private static final String query ="select db_id\n" + 
			"from ReferenceEntity\n" + 
			"where ReferenceEntity.referenceDatabase in \n" + 
			"(\n" + 
			"	select db_id\n" + 
			"	from ReferenceDatabase_2_name\n" + 
			"	where ReferenceDatabase_2_name.name like '%KEGG%'\n" + 
			")\n" + 
			"and identifier like '%:%' ;";

	private static final String countQuery ="select COUNT(*)\n" + 
			"from ReferenceEntity\n" + 
			"where ReferenceEntity.referenceDatabase in \n" + 
			"(\n" + 
			"	select db_id\n" + 
			"	from ReferenceDatabase_2_name\n" + 
			"	where ReferenceDatabase_2_name.name like '%KEGG%'\n" + 
			")\n" + 
			"and identifier like '%:%' ;";
	
	private static MySQLAdaptor adaptor;
	
	public static void main(String args[])
	{
		try
		{
			if (args.length != 6)
			{
				logger.error("Args should be: '${host} ${database} ${user} ${password} ${port} ${Person DB_ID}");
				System.exit(1);
			}
			
			String host = args[0];
			String database = args[1];
			String user = args[2];
			String password = args[3];
			int port = Integer.valueOf(args[4]);
			Long personID = Long.valueOf(args[5]);
			KeggIdentifierFixer.adaptor = new MySQLAdaptor(host, database, user, password, port);
			GKInstance instanceEdit = null;
			logger.info("Querying the database...");
			ResultSet rsCount = adaptor.executeQuery(KeggIdentifierFixer.countQuery, null);
			int instancesCount = 0;
			while (rsCount.next())
			{
				instancesCount = rsCount.getInt(1);
			}
			rsCount.close();
			ResultSet rs = adaptor.executeQuery(KeggIdentifierFixer.query, null);
			int updatedCount = 0;
			int notUpdateCount = 0;
			int total = 0;
			logger.info("Preparing to update " + instancesCount + " instances...");
			while (rs.next())
			{
				total++;
				if (instanceEdit == null)
				{
					try
					{
						instanceEdit = InstanceEditUtils.createDefaultIE(adaptor, personID, true, "Data fix for KEGG identifier (prune species code).");
						instanceEdit.getDBID();
						adaptor.updateInstance(instanceEdit);
						logger.info("Using InstanceEdit: {}", instanceEdit.toStanza());
					}
					catch (Exception e)
					{
						e.printStackTrace();
						// if creating an InstanceEdit fails, abort execution!
						throw new RuntimeException(e);
					}
				}
				
				Long dbId = rs.getLong("db_id");
				GKInstance refEntInstance = KeggIdentifierFixer.adaptor.fetchInstance(dbId);
				
				//adaptor.loadInstanceAttributeValues(refEntInstance);
				
				String identifier = (String) refEntInstance.getAttributeValue(ReactomeJavaConstants.identifier);
				String message = "Instance " + refEntInstance.getDBID() + "; with identifier " + identifier;
				// only want to fix things that have numeric
				if (identifier.matches("^[^0-9]*:\\d+"))
				{
					// remove the species-code prefix.
					identifier = identifier.replaceAll("^.*:", "");
					message += " ; updated as: "+identifier;
					logger.debug(message);
					
					refEntInstance.setAttributeValue(ReactomeJavaConstants.identifier, identifier);
					refEntInstance.getAttributeValuesList(ReactomeJavaConstants.modified);
					refEntInstance.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
					// Update not yet tested...
					KeggIdentifierFixer.adaptor.updateInstanceAttribute(refEntInstance, ReactomeJavaConstants.identifier);
					KeggIdentifierFixer.adaptor.updateInstanceAttribute(refEntInstance, ReactomeJavaConstants.modified);
					updatedCount++;
				}
				else
				{
					logger.debug("Identifier {} does not match '^[^0-9]*:\\d+' so it will not be corrected (that prefix is probably a legitimate part of the KEGG identifier string).", identifier);
					notUpdateCount++;
				}
			}
			rs.close();
			logger.info(updatedCount + " instances were updated.");
			logger.info(notUpdateCount + " instances were not updated - prefix might be part of KEGG identifier.");
			logger.info(total + " records inspected, in total");
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
}
