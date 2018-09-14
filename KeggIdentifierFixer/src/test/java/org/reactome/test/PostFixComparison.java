package org.reactome.test;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.util.compare.DBObjectComparer;

/*
 * Compares the results of KeggIdentifierFixer to a copy of the same database that is unmodified.
 * Assumptions: You are running both databases on the same host in docker containers with the same name
 * inside the containers. Only the port numbers differ. The first port number should be for the modified database,
 * the second port number should be the baseline database.
 */
public class PostFixComparison
{
	
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
	protected static final Logger logger = LogManager.getLogger();
	
	public static void main(String[] args) throws SQLException, Exception
	{
		if (args.length != 6)
		{
			logger.error("Args should be: '${host} ${database} ${user} ${password} ${port 1} ${port 2}'");
			System.exit(1);
		}
		
		String host = args[0];
		String database = args[1];
		String user = args[2];
		String password = args[3];
		int port1 = Integer.valueOf(args[4]);
		int port2 = Integer.valueOf(args[5]);
 
		MySQLAdaptor alteredDB = new MySQLAdaptor(host, database, user, password, port1);
		MySQLAdaptor baselineDB = new MySQLAdaptor(host, database, user, password, port2);
		
		ResultSet rs = baselineDB.executeQuery(PostFixComparison.query, null);
		int numInstsWithDiffs = 0;
		int numInstsWithNoDiffs = 0;
		while (rs.next())
		{
			GKInstance baselineInst = baselineDB.fetchInstance(rs.getLong(1));
			GKInstance alteredInst = alteredDB.fetchInstance(rs.getLong(1));
			StringBuilder sb = new StringBuilder();
			int numDiffs = DBObjectComparer.compareInstances(baselineInst, alteredInst, sb);
			if (numDiffs > 0)
			{
				// anything more than 1 difference and that's a problem!!
				if (numDiffs > 1)
				{
					logger.error("Only 1 difference (\"identifier\") was expected, but got {} differences!", numDiffs);
				}
				numInstsWithDiffs++;
				logger.debug("Number of differences: {}",numDiffs);
				logger.debug(sb.toString());
			}
			else
			{
				numInstsWithNoDiffs++;
			}
		}
		logger.info("Number of instances with differences: {}", numInstsWithDiffs);
		logger.info("Number of instances with NO differences: {}", numInstsWithNoDiffs);
		rs.close();
	}

}
