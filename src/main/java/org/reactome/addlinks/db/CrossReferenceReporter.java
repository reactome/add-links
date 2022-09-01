package org.reactome.addlinks.db;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gk.persistence.MySQLAdaptor;

public class CrossReferenceReporter
{
	private MySQLAdaptor dbAdapter;

	/**
	 * This comparator is used for comparing two REPORT_KEYS. It just compares them based on sortOrder.
	 */
	private Comparator<REPORT_KEYS> reportKeysComparator = new Comparator<REPORT_KEYS>()
		{
			@Override
			public int compare(REPORT_KEYS o1, REPORT_KEYS o2)
			{
				return Integer.compare(o1.getSortOrder(), o2.getSortOrder());
			}
		};
	
	/**
	 * This comparator is used to compare rows in a report. It ensures that "Subtotal" and "Grand Total" lines appear "lower down" in the report.
	 */
	protected static Comparator<String> reportRowComparator = new Comparator<String>()
		{
			@Override
			public int compare(String o1, String o2)
			{
				if (o1 == null)
					return -1;
				if (o2 == null)
					return 1;
				
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
	
	/**
	 * Keys for the columns of the report.
	 * @author sshorser
	 *
	 */
	public enum REPORT_KEYS
	{
		NEW_REF_DB(0), OLD_REF_DB(1), NEW_OBJECT_TYPE(2), OLD_OBJECT_TYPE(3), DIFFERENCE(4), NEW_QUANTITY(5), OLD_QUANTITY(6), ALT_SORT_VALUE(8);
		
		private int sortOrder = 0;
		
		REPORT_KEYS(int i)
		{
			this.sortOrder = i;
		}
		
		/**
		 * Sort order can be used to determine what order to process the keys in.
		 * @return
		 */
		public int getSortOrder()
		{
			return this.sortOrder;
		}
	}
	
	/**
	 * A specialized Map that is keyed by REPORT_KEYS and is comparable for the purpose of generating reports.
	 * @author sshorser
	 *
	 * @param <K>
	 * @param <V>
	 */
	public class ReportMap<K extends REPORT_KEYS, V extends String> extends HashMap<K, V> implements Comparable<ReportMap<K, V>>
	{
		/**
		 * generated serialVersionUID
		 */
		private static final long serialVersionUID = -4567642471909039840L;

		
		@Override
		public int compareTo(ReportMap<K, V> other)
		{
			// we will need to check key-by-key.
			for (K key : this.keySet().stream().sorted((Comparator<? super K>) reportKeysComparator).collect(Collectors.toList()))
			{
				// if we found the Grand Total, it is ALWAYS greater (should appear near the end of the report).
				if (this.get(key) != null && this.get(key).trim().contains("Grand Total"))
					return 1;
				
				if (other.get(key) != null && other.get(key).trim().contains("Grand Total"))
					return -1;
				
				// if we found the Subtotal, it is ALWAYS greater (should appear near the end the section).
				if (this.get(key) != null && this.get(key).trim().contains("Subtotal"))
					return 1;
				
				if (other.get(key) != null && other.get(key).trim().contains("Subtotal"))
					return -1;
				
				// if this key IS null and the other has a subtotal, then the *other* should appear lower down. 
				if (this.get(key) == null && other.get(REPORT_KEYS.NEW_OBJECT_TYPE).trim().contains("Subtotal"))
					return -1;
				// Same as above condition, but "this" and "other" are reversed.
				if (other.get(key) == null && this.get(REPORT_KEYS.NEW_OBJECT_TYPE).trim().contains("Subtotal"))
					return 1;
				
				
				// Ok let's compare non subtotal/grand total keys. Use the ALT sort value in cases where *key* maps to NULL (that's what it's there for!)
				String leftValue = this.get(key) != null ? this.get(key) : this.get(REPORT_KEYS.ALT_SORT_VALUE) ;
				String rightValue = other.get(key) != null ? other.get(key) : other.get(REPORT_KEYS.ALT_SORT_VALUE) ;
				
				int rowCompareResult = reportRowComparator.compare(leftValue, rightValue);
				if (rowCompareResult!=0)
				{
					return rowCompareResult;
				}
			}
			// If the function STILL hasn't reutnred by this point, it means that THIS had no keys, so it is "less than" the other map.
			return -1;
		}
	}

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
			"	union\n" +
			"	select count(*) as count,  ExternalOntology.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q5'\n" +
			"	from ExternalOntology\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = ExternalOntology.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = ExternalOntology.db_id\n" +
			"	group by ExternalOntology.referenceDatabase,  ref_db_subq.name, DatabaseObject._class\n" +
			"	union\n" +
			"	select count(*) as count,  GO_CellularComponent.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q6'\n" +
			"	from GO_CellularComponent\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = GO_CellularComponent.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = GO_CellularComponent.db_id\n" +
			"	group by GO_CellularComponent.referenceDatabase,  ref_db_subq.name, DatabaseObject._class\n" +
			"	union\n" +
			"	select count(*) as count,  GO_MolecularFunction.referenceDatabase as ref_db_id, ref_db_subq.name as ref_db_names_and_aliases, DatabaseObject._class as object_type, 'q7'\n" +
			"	from GO_MolecularFunction\n" +
			"	inner join ReferenceDatabase on ReferenceDatabase.DB_ID = GO_MolecularFunction.referenceDatabase\n" +
			"	inner join (select group_concat(ReferenceDatabase_2_name.name order by ReferenceDatabase_2_name.name_rank separator ', ') as name, ReferenceDatabase_2_name.db_id\n" +
			"				from ReferenceDatabase_2_name\n" +
			"				inner join ReferenceDatabase on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" +
			"				group by ReferenceDatabase.db_id) as ref_db_subq on ReferenceDatabase.db_id = ref_db_subq.db_id\n" +
			"	inner join DatabaseObject on DatabaseObject.db_id = GO_MolecularFunction.db_id\n" +
			"	group by GO_MolecularFunction.referenceDatabase,  ref_db_subq.name, DatabaseObject._class\n" +
			"	union\n" +
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
			" GROUP BY ref_db_names_and_aliases, object_type WITH ROLLUP\n" +
      " ORDER BY ref_db_names_and_aliases, object_type;\n";



	public CrossReferenceReporter(MySQLAdaptor adaptor)
	{
		this.dbAdapter = adaptor;
	}
	
	/**
	 * Return the empty string if the input is null. I think it's cleaner to call function than use "?:" SO MANY times.
	 * @param s
	 * @return
	 */
	private String emptyStringIfNull(String s)
	{
		return s != null ? s : "";
	}
	
	/**
	 * Generates a report that shows differences between two cross-reference reports.
	 * @param oldReport - An older report.
	 * @param newReport - A report generated after oldReport.
	 * @return The diff report, as a string.
	 * @throws SQLException
	 */
	public String printReportWithDiffs(Map<String,Map<String,Integer>> oldReport, Map<String,Map<String,Integer>> newReport) throws SQLException
	{
		//printing the diff in-place is probably too hard, so maybe create an intermediate data structure and then sort/print THAT. 
		//Intermediate structure: a list of Maps with the following keys: newRefDB, oldRefDB, newObjectType, oldObjectType, newQuantity, oldQuantity, diff.
		//first, we need to build this thing.
		List<ReportMap<REPORT_KEYS,String>> reportRows = new ArrayList<ReportMap<REPORT_KEYS,String>>();
		for (String oldRefDBName : oldReport.keySet().stream().sorted(CrossReferenceReporter.reportRowComparator).collect(Collectors.toList()))
		{
			for (String oldObjectType : oldReport.get(oldRefDBName).keySet().stream().sorted(CrossReferenceReporter.reportRowComparator).collect(Collectors.toList()))
			{
				ReportMap<REPORT_KEYS, String> map = new ReportMap<REPORT_KEYS, String>();
				map.put(REPORT_KEYS.OLD_REF_DB, oldRefDBName);
				map.put(REPORT_KEYS.OLD_OBJECT_TYPE, oldObjectType);
				map.put(REPORT_KEYS.OLD_QUANTITY, String.valueOf(oldReport.get(oldRefDBName).get(oldObjectType)));
				// append with 1 if this is a subtotal, to force the alt sort value to be larger, and thus appear further down the list.
				map.put(REPORT_KEYS.ALT_SORT_VALUE, oldRefDBName+(oldObjectType.contains("Subtotal") ? "1":"0"));
				// need to make sure it's actually in the new report.
				if (newReport.containsKey(oldRefDBName))
				{
					map.put(REPORT_KEYS.NEW_REF_DB, oldRefDBName);
					// need to make sure it's actually in the new report.
					if (newReport.get(oldRefDBName).containsKey(oldObjectType))
					{
						map.put(REPORT_KEYS.NEW_OBJECT_TYPE, oldObjectType);
						map.put(REPORT_KEYS.NEW_QUANTITY, String.valueOf(newReport.get(oldRefDBName).get(oldObjectType)));
						map.put(REPORT_KEYS.DIFFERENCE, String.valueOf( newReport.get(oldRefDBName).get(oldObjectType) - oldReport.get(oldRefDBName).get(oldObjectType) ));
					}
					else
					{
						// Set null - at least the keys are present so when it comes time to print, we don't need to constantly check for key existence.
						map.put(REPORT_KEYS.NEW_OBJECT_TYPE, null);
						map.put(REPORT_KEYS.NEW_QUANTITY, null);
						map.put(REPORT_KEYS.DIFFERENCE, String.valueOf(-oldReport.get(oldRefDBName).get(oldObjectType)));
					}
				}
				else
				{
					// Set null - at least the keys are present so when it comes time to print, we don't need to constantly check for key existence.
					map.put(REPORT_KEYS.NEW_REF_DB, null);
					map.put(REPORT_KEYS.NEW_OBJECT_TYPE, null);
					map.put(REPORT_KEYS.NEW_QUANTITY, null);
					map.put(REPORT_KEYS.DIFFERENCE, String.valueOf(-oldReport.get(oldRefDBName).get(oldObjectType)));
				}
				reportRows.add(map);
			}
		}
		// Once that's done, we should go through the NEW report and add in any refdbs/objecttypes that weren't in oldReport
		// for all ref db names in the new report that are not in the old report. EDIT: This was wrong: If new object types were added for existing databases, their records were omitted in the final report though the subtotal still showed the correct number.
		for (String newRefDBName : newReport.keySet().stream().sorted(CrossReferenceReporter.reportRowComparator).collect(Collectors.toList()))
		{
			// Filter for new Ref DBs *OR* for pre-existing Ref DBs with new object types.
			for (String newObjectType : newReport.get(newRefDBName).keySet().stream()
										.filter( objectType -> !oldReport.containsKey(newRefDBName) || !oldReport.get(newRefDBName).containsKey(objectType) )
										.sorted(CrossReferenceReporter.reportRowComparator).collect(Collectors.toList()))
			{
				ReportMap<REPORT_KEYS, String> map = new ReportMap<REPORT_KEYS, String>();
				map.put(REPORT_KEYS.NEW_OBJECT_TYPE, newObjectType);
				map.put(REPORT_KEYS.ALT_SORT_VALUE, newRefDBName+(newObjectType.contains("Subtotal") ? "1" : "0"));
				map.put(REPORT_KEYS.NEW_REF_DB, newRefDBName);
				map.put(REPORT_KEYS.NEW_QUANTITY, String.valueOf(newReport.get(newRefDBName).get(newObjectType)));
				map.put(REPORT_KEYS.DIFFERENCE, String.valueOf(newReport.get(newRefDBName).get(newObjectType)));
				
				// Set null - at least the keys are present so when it comes time to print, we don't need to constantly check for key existence.
				map.put(REPORT_KEYS.OLD_REF_DB, null);
				map.put(REPORT_KEYS.OLD_OBJECT_TYPE, null);
				map.put(REPORT_KEYS.OLD_QUANTITY, null);
				reportRows.add(map);
			}
		}
		
		//reportRows.sort(reportMapSorter);
		
		int oldRefDBNameMaxWidth = 0;
		int oldObjectTypeMaxWidth = 0;
		int oldQuantityMaxWidth = 0;
		int newRefDBNameMaxWidth = 0;
		int newObjectTypeMaxWidth = 0;
		int newQuantityMaxWidth = 0;
		int diffMaxWidth = 0;
		
		List<List<String>> rows = new ArrayList<List<String>>(reportRows.size()+1);
		//rows.add(Arrays.asList("Pre-AddLinks DB Name", "Object Type", "Quantity", "Difference", "Post-AddLinks DB Name", "Object Type", "Quantity"));
		for (Map<REPORT_KEYS, String> reportRow : reportRows.stream().sequential().sorted().collect(Collectors.toList()))
		{
			rows.add(Arrays.asList( reportRow.get(REPORT_KEYS.OLD_REF_DB), reportRow.get(REPORT_KEYS.OLD_OBJECT_TYPE), reportRow.get(REPORT_KEYS.OLD_QUANTITY),
									reportRow.get(REPORT_KEYS.DIFFERENCE),
									reportRow.get(REPORT_KEYS.NEW_REF_DB), reportRow.get(REPORT_KEYS.NEW_OBJECT_TYPE), reportRow.get(REPORT_KEYS.NEW_QUANTITY),
									reportRow.get(REPORT_KEYS.ALT_SORT_VALUE)));
			
			//Function<String, String> emptyStringIfNull = (s) -> s != null ? s : ""; // had to make this into a function because PowerMock kept choking on it.
			oldRefDBNameMaxWidth = Math.max(oldRefDBNameMaxWidth, emptyStringIfNull(reportRow.get(REPORT_KEYS.OLD_REF_DB)).length());
			oldObjectTypeMaxWidth = Math.max(oldObjectTypeMaxWidth, emptyStringIfNull(reportRow.get(REPORT_KEYS.OLD_OBJECT_TYPE)).length());
			oldQuantityMaxWidth = Math.max(oldQuantityMaxWidth, emptyStringIfNull(reportRow.get(REPORT_KEYS.OLD_QUANTITY)).length());
			newRefDBNameMaxWidth = Math.max(newRefDBNameMaxWidth, emptyStringIfNull(reportRow.get(REPORT_KEYS.NEW_REF_DB)).length());
			newObjectTypeMaxWidth = Math.max(newObjectTypeMaxWidth, emptyStringIfNull(reportRow.get(REPORT_KEYS.NEW_OBJECT_TYPE)).length());
			newQuantityMaxWidth = Math.max(newQuantityMaxWidth, emptyStringIfNull(reportRow.get(REPORT_KEYS.NEW_QUANTITY)).length());
			diffMaxWidth = Math.max(diffMaxWidth, emptyStringIfNull(reportRow.get(REPORT_KEYS.DIFFERENCE)).length());
		}
		
		oldRefDBNameMaxWidth = Math.max("Pre-AddLinks DB Name".length(), oldRefDBNameMaxWidth);
		oldObjectTypeMaxWidth = Math.max("Object Type".length(), oldObjectTypeMaxWidth);
		oldQuantityMaxWidth = Math.max("Quantity".length(), oldQuantityMaxWidth);
		newRefDBNameMaxWidth = Math.max("Post-AddLinks DB Name".length(), newRefDBNameMaxWidth);
		newObjectTypeMaxWidth = Math.max("Object Type".length(), newObjectTypeMaxWidth);
		newQuantityMaxWidth = Math.max("Quantity".length(), newQuantityMaxWidth);
		diffMaxWidth = Math.max("Difference".length(), diffMaxWidth);
		int lineWidth = oldRefDBNameMaxWidth + oldObjectTypeMaxWidth + oldQuantityMaxWidth + newRefDBNameMaxWidth + newObjectTypeMaxWidth + newQuantityMaxWidth + diffMaxWidth;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream pstream = new PrintStream(baos);
		
		pstream.printf(" | %1$-"+oldRefDBNameMaxWidth+"s | %2$-"+oldObjectTypeMaxWidth+"s | %3$"+oldQuantityMaxWidth+"s | %4$"+diffMaxWidth+"s | %5$-"+newRefDBNameMaxWidth+"s | %6$-"+newObjectTypeMaxWidth+"s | %7$"+newQuantityMaxWidth+"s |\n",
						"Pre-AddLinks DB Name", "Object Type", "Quantity", "Difference", "Post-AddLinks DB Name", "Object Type", "Quantity");
		// the "3*7" is because there are three extra characters (padding: " | ") for 7 columns, the +2 is for leading/trailing chars of the header line.
		pstream.print( new String(new char[lineWidth + (3*7) + 2]).replace("\0", "-") + "\n" );
		for (List<String> row : rows)
		{
			pstream.printf(" | %1$-"+oldRefDBNameMaxWidth+"s | %2$-"+oldObjectTypeMaxWidth+"s | %3$"+oldQuantityMaxWidth+"s | %4$"+diffMaxWidth+"s | %5$-"+newRefDBNameMaxWidth+"s | %6$-"+newObjectTypeMaxWidth+"s | %7$"+newQuantityMaxWidth+"s | \n"
							, emptyStringIfNull(row.get(0)), emptyStringIfNull(row.get(1)), emptyStringIfNull(row.get(2)),
							emptyStringIfNull(row.get(3)), emptyStringIfNull(row.get(4)), emptyStringIfNull(row.get(5)), emptyStringIfNull(row.get(6)));
		}
		
		return baos.toString();
	}
	
	/**
	 * Generates a report that shows differences between two cross-reference reports. The diff will be between a new report that is generated when this function is called,
	 * and oldReport.
	 * @param oldReport - An older report to compare to the *current* state of the database.
	 * @return The diff report, as a string.
	 * @throws SQLException
	 */
	public String printReportWithDiffs(Map<String,Map<String,Integer>> oldReport) throws SQLException
	{
		Map<String,Map<String,Integer>> newReport = this.createReportMap();
		return this.printReportWithDiffs(oldReport, newReport);
	}
	
	/**
	 * Queries the database a produces a Map containing information about the current state of cross-references.
	 * @return Returns the data as a Map. You can use this to give to printReportWithDiffs later.
	 * @throws SQLException
	 */
	public Map<String,Map<String,Integer>> createReportMap() throws SQLException
	{
		Map<String,Map<String,Integer>> report = new HashMap<String, Map<String,Integer>>();
		ResultSet rs = this.dbAdapter.executeQuery(this.reportSQL, null);
		while (rs.next())
		{
			String refDBName = rs.getString(1) != null ? rs.getString(1) : "Grand Total:";
			String objectType = rs.getString(2) != null ? rs.getString(2) : "    Subtotal:";
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
	
	/**
	 * Formats and prints an existing report map.
	 * @param reportMap - The report, as a map, to print.
	 * @return The report, as a formatted string.
	 */
	public String getReportContent(Map<String,Map<String,Integer>> reportMap)
	{
		int refDBNameMaxWidth = 0;
		int objectTypeMaxWidth = 0;
		int quantityMaxWidth = 0;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream pstream = new PrintStream(baos);
		
		for (String dbName : reportMap.keySet())
		{
			for (String objectType : reportMap.get(dbName).keySet())
			{
				refDBNameMaxWidth = Math.max(dbName.length(), refDBNameMaxWidth);
				objectTypeMaxWidth = Math.max(objectType.length(), objectTypeMaxWidth);
				quantityMaxWidth = Math.max(String.valueOf(reportMap.get(dbName).get(objectType)).length(), quantityMaxWidth);
			}
		}
		
		quantityMaxWidth = Math.max(quantityMaxWidth, "Quantity".length());
		// I really wish there was some modern, well-supported library that could do table formatting. It's not hard to write, but I don't really 
		// want to write one myself.
		// Guide for Formatter syntax in Java: http://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#syntax
		pstream.printf(" | %1$-"+refDBNameMaxWidth+"s | %2$-"+objectTypeMaxWidth+"s | %3$"+quantityMaxWidth+"s | \n", "Database Name", "Object Type", "Quantity");
		pstream.print("--------------------------------------------------------------------------------------------------------\n");

		for (String refDBName : reportMap.keySet().stream().sorted(CrossReferenceReporter.reportRowComparator).collect(Collectors.toList()))
		{
			for (String objectType : reportMap.get(refDBName).keySet().stream().sorted(CrossReferenceReporter.reportRowComparator).collect(Collectors.toList()))
			{
				String quantity = String.valueOf(reportMap.get(refDBName).get(objectType));
				pstream.printf(" | %1$-"+refDBNameMaxWidth+"s | %2$-"+objectTypeMaxWidth+"s | %3$"+quantityMaxWidth+"s | \n", refDBName , !refDBName.contains("Grand Total") ? objectType : "", quantity);		
			}
		}
		
		return baos.toString();
	}
	
	/**
	 * Queries the database and produces a report about the current state of cross-references.
	 * @return The report, as a string.
	 * @throws SQLException
	 */
	public String printReport() throws SQLException
	{
		Map<String,Map<String,Integer>> reportMap = new HashMap<String, Map<String,Integer>>();
		ResultSet rs = this.dbAdapter.executeQuery(this.reportSQL, null);
		while (rs.next())
		{
			// If there is no object type, it means this is the Grand Total line (because the SQL query uses ROLL UP and so there will be a NULL value in this field for the roll-up summary)
			String refDBName = rs.getString(1) != null ? rs.getString(1) : "Grand Total:";
			// If there is no ref db name, it means this is a subtotal line (again - roll-up will put NULL in the field when it is being summarized)
			String objectType = rs.getString(2) != null ? rs.getString(2) : "    Subtotal:" ;
			String quantity = rs.getString(3);
			
			if (reportMap.containsKey(refDBName))
			{
				reportMap.get(refDBName).put(objectType, Integer.valueOf(quantity));
			}
			else
			{
				Map<String, Integer> map = new HashMap<String,Integer>();
				map.put(objectType, Integer.valueOf(quantity));
				reportMap.put(refDBName, map);
			}
		}
		
		return this.getReportContent(reportMap);
	}
}
