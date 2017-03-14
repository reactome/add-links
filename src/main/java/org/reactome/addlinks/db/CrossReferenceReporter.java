package org.reactome.addlinks.db;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public class CrossReferenceReporter
{
	private MySQLAdaptor dbAdapter;
	private Logger logger = LogManager.getLogger();
	
	private Map<String,Map<String,Integer>> reportMap = new HashMap<String, Map<String,Integer>>();
	private Comparator<String> comparator = new Comparator<String>()
											{
												@Override
												public int compare(String o1, String o2)
												{
													// if left Operand is Grand Total, then o1 > o2
													if (o1.trim().contains("Grand Total"))
														return 1;
													// if right Operand is Grand Total, then o2 > o1
													if (o2.trim().contains("Grand Total"))
														return -1;
													
													// At this point, we know we are not dealing with a Grand Total operand.
													
													// if left Operand is subtotal, then o1 > o2
													if (o1.trim().contains("Subtotal"))
														return 1;
													// if right Operand is subtotal, then o2 > o1
													if (o2.trim().contains("Subtotal"))
														return -1;
													
													// use normal string comparison for all other cases.
													return o1.toLowerCase().compareTo(o2.toLowerCase());
												}
											};
	
	// Giant SQL query converted to Java string via http://www.buildmystring.com/
	private String reportSQL = "select ref_db_names_and_aliases, /*ref_db_id,*/ object_type, sum(count)\n" +
			" from\n" +
			" (\n" +
			"	select count(*) as count, ReferenceEntity.referenceDatabase as ref_db_id,  ref_db_subq.name as ref_db_names_and_aliases,  DatabaseObject._class as object_type, 'q1'\n" +
			"	from ReferenceEntity\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = ReferenceEntity.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = ReferenceEntity.db_id\n" +
			"	group by ReferenceEntity.referenceDatabase,  ref_db_subq.name, DatabaseObject._class\n" +
			"	union\n" +
			"	select count(*) as count,  DatabaseIdentifier.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q2'\n" +
			"	from DatabaseIdentifier\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = DatabaseIdentifier.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = DatabaseIdentifier.db_id\n" +
			"	group by DatabaseIdentifier.referenceDatabase,  ref_db_subq.name, DatabaseObject._class\n" +
			"	union\n" +
			"	select count(*) as count, DatabaseIdentifier.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q3'\n" +
			"	from PhysicalEntity\n" +
			"	inner join PhysicalEntity_2_crossReference on PhysicalEntity.DB_ID = PhysicalEntity_2_crossReference.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = PhysicalEntity.DB_ID\n" +
			"	inner join DatabaseIdentifier on PhysicalEntity_2_crossReference.crossReference = DatabaseIdentifier.db_id\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = DatabaseIdentifier.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	group by DatabaseObject._class, DatabaseIdentifier.referenceDatabase, ref_db_subq.name\n" +
			"	union\n" +
			"	select count(*) as count, DatabaseIdentifier.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q4'\n" +
			"	from Event \n" +
			"	inner join Event_2_crossReference on Event.DB_ID = Event_2_crossReference.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = Event.DB_ID\n" +
			"	inner join DatabaseIdentifier on Event_2_crossReference.crossReference = DatabaseIdentifier.db_id\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = DatabaseIdentifier.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	group by DatabaseObject._class, DatabaseIdentifier.referenceDatabase, ref_db_subq.name\n" +
			"    union\n" +
			"	select count(*) as count,  ExternalOntology.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q5'\n" +
			"	from ExternalOntology\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = ExternalOntology.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = ExternalOntology.db_id\n" +
			"	group by ExternalOntology.referenceDatabase,  ref_db_subq.name, DatabaseObject._class\n" +
			"    union\n" +
			"	select count(*) as count,  GO_CellularComponent.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q6'\n" +
			"	from GO_CellularComponent\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = GO_CellularComponent.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = GO_CellularComponent.db_id\n" +
			"	group by GO_CellularComponent.referenceDatabase,  ref_db_subq.name, DatabaseObject._class\n" +
			"    union\n" +
			"	select count(*) as count,  GO_MolecularFunction.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q7'\n" +
			"	from GO_MolecularFunction\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = GO_MolecularFunction.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = GO_MolecularFunction.db_id\n" +
			"	group by GO_MolecularFunction.referenceDatabase,  ref_db_subq.name, DatabaseObject._class\n" +
			"    union\n" +
			"	select count(*) as count,  GO_BiologicalProcess.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q8'\n" +
			"	from GO_BiologicalProcess\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = GO_BiologicalProcess.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = GO_BiologicalProcess.db_id\n" +
			"	group by GO_BiologicalProcess.referenceDatabase,  ref_db_subq.name, DatabaseObject._class\n" +
			"	) as subq\n" +
			" group by ref_db_names_and_aliases asc, object_type asc with rollup;\n";
	

	public CrossReferenceReporter(MySQLAdaptor adaptor)
	{
		this.dbAdapter = adaptor;
	}
	
	public void printReportWithDiffs(Map<String,Map<String,Integer>> oldReport) throws SQLException
	{
		//ResultSet rs = this.dbAdapter.executeQuery(this.reportSQL, null);
		
		Map<String,Map<String,Integer>> newReport = this.createReportMap();
		
		
	}
	
	private Map<String,Map<String,Integer>> createReportMap() throws SQLException
	{
		Map<String,Map<String,Integer>> report = new HashMap<String, Map<String,Integer>>();
		ResultSet rs = this.dbAdapter.executeQuery(this.reportSQL, null);
		while (rs.next())
		{
			String refDBName = rs.getString(1) != null ? rs.getString(1) : "Grand Total:";//rs.getString(1);
			String objectType = rs.getString(2) != null ? rs.getString(2) : "    Subtotal:" ; //rs.getString(2);
			String quantity = rs.getString(3);
			
			if (report.containsKey(refDBName))
			{
				report.get(refDBName).put(objectType, Integer.valueOf(quantity));
			}
			else
			{
				Map<String, Integer> map = new HashMap<String,Integer>();
				map.put(objectType, Integer.valueOf(quantity));
				report.put(refDBName, map);
			}
		}
		return report;
	}
	
	
	public String printReport() throws SQLException
	{
		// Guide for Formatter syntax in Java: http://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#syntax
		//
		// I really wish there was some modern, well-supported library that could do table formatting. It's not hard to write, but I don't really 
		// want to write one myself.
		

		int refDBNameMaxWidth = 0;
		int objectTypeMaxWidth = 0;
		int quantityMaxWidth = 0;
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream pstream = new PrintStream(baos);
		
		
		ResultSet rs = this.dbAdapter.executeQuery(this.reportSQL, null);
		while (rs.next())
		{
			String refDBName = rs.getString(1) != null ? rs.getString(1) : "Grand Total:";//rs.getString(1);
			String objectType = rs.getString(2) != null ? rs.getString(2) : "    Subtotal:" ; //rs.getString(2);
			String quantity = rs.getString(3);
			
			refDBNameMaxWidth = Math.max(refDBNameMaxWidth, refDBName.length());
			objectTypeMaxWidth = Math.max(objectTypeMaxWidth, objectType.length());
			quantityMaxWidth = Math.max(quantityMaxWidth, quantity.length());
			
			if (this.reportMap.containsKey(refDBName))
			{
				this.reportMap.get(refDBName).put(objectType, Integer.valueOf(quantity));
			}
			else
			{
				Map<String, Integer> map = new HashMap<String,Integer>();
				map.put(objectType, Integer.valueOf(quantity));
				this.reportMap.put(refDBName, map);
			}
		}
		quantityMaxWidth = Math.max(quantityMaxWidth, "Quantity".length());
		pstream.printf(" | %1$-"+refDBNameMaxWidth+"s | %2$-"+objectTypeMaxWidth+"s | %3$"+quantityMaxWidth+"s | \n", "Database Name", "Object Type", "Quantity");
		pstream.print("--------------------------------------------------------------------------------------------------------\n");

		for (String refDBName : this.reportMap.keySet().stream().sorted(this.comparator).collect(Collectors.toList()))
		{
			for (String objectType : this.reportMap.get(refDBName).keySet().stream().sorted(this.comparator).collect(Collectors.toList()))
			{
				String quantity = String.valueOf(this.reportMap.get(refDBName).get(objectType));
				pstream.printf(" | %1$-"+refDBNameMaxWidth+"s | %2$-"+objectTypeMaxWidth+"s | %3$"+quantityMaxWidth+"s | \n", refDBName , !refDBName.contains("Grand Total") ? objectType : "", quantity);		
			}
		}
		
		return baos.toString();
	}

}
