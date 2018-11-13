package org.reactome.addlinks.db;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;

/**
 * Reports on duplicated identifiers.
 * @author sshorser
 *
 */
public class DuplicateIdentifierReporter
{
	private static final String FIELD_SEPARATOR = " | ";
	private Logger logger = LogManager.getLogger();
	private MySQLAdaptor dbAdapter;
	
	/**
	 * Enum values map directly to column names in the query result set.
	 * @author sshorser
	 *
	 */
	public enum REPORT_KEYS
	{
		duplicate_count, identifier, ReferenceEntity_DB_IDs, object_type, display_name, combined_identifier, ref_db_name, species_db_id, species_name;
	}
	
	// Should I move the query out to a file? I like that having it in here as a local private member makes the implementation self-contained,
	// but on the other hand, *IFF* there are ever changes needed during a Release, that would require a recompiling of the code. Also, it's
	// a rather long and ugly string to keep contained in a class. But... I also don't want YET ANOTHER resource file to load... Hmmm... will resolve this later...
	private static String query = "SELECT distinct subq1.duplicate_count, subq1.identifier, subq1.ReferenceEntity_DB_IDs, subq1._class AS object_type, subq1._displayName as display_name, subq1.combined_identifier, ReferenceDatabase_2_name.name as ref_db_name, subq1.species_db_id, subq1.species_name \n" + 
			"from (\n" + 
			"    select count(ReferenceEntity.DB_ID) as duplicate_count, ReferenceEntity.identifier, group_concat(ReferenceEntity.db_id) as ReferenceEntity_DB_IDs, ReferenceEntity.referenceDatabase, DatabaseObject._class, DatabaseObject._displayName, subq.combined_identifier, subq.species_db_id, subq.species_name\n" + 
			"    from ReferenceEntity \n" + 
			"    inner join DatabaseObject on DatabaseObject.db_id = ReferenceEntity.db_id\n" + 
			"    -- subquery to produce a combined identifier that contains the identifier string and the object's displayName\n" + 
			"    inner join (select ReferenceEntity.*, concat(coalesce(ReferenceEntity.identifier,'NULL'),';', coalesce(DatabaseObject._displayName,'NULL')) as combined_identifier, species_subq.db_id AS species_db_id, species_subq.name AS species_name\n" + 
			"                from ReferenceEntity\n" + 
			"                inner join DatabaseObject on ReferenceEntity.db_id = DatabaseObject.db_id\n" + 
			"               -- start OUTER joining from ReferenceEntity through ReferenceSequence to Taxon2Name to get the species name of\n" + 
			"               -- the reference object. But not all References have a species (such as ReferenceMolecule),\n" + 
			"               -- hence the OUTER joins.\n" + 
			"               LEFT OUTER JOIN ReferenceSequence ON ReferenceEntity.DB_ID = ReferenceSequence.DB_ID\n" + 
			"               -- subquery to combine species table and taxon_2_name\n" + 
			"               LEFT OUTER JOIN (SELECT Species.DB_ID, Taxon_2_name.name\n" + 
			"                                   FROM Species \n" + 
			"                                   INNER JOIN Taxon_2_name ON (Taxon_2_name.DB_ID = Species.DB_ID AND Taxon_2_name.name_rank = 0)) AS species_subq\n" + 
			"               ON ReferenceSequence.species = species_subq.DB_ID\n" + 
			"                where ReferenceEntity.identifier is not null) as subq on subq.db_id = ReferenceEntity.db_id\n" + 
			"    -- First, group on the combined identifer - the identifier concatenated with _displayName.\n" + 
			"    -- This is because an identifier could be associated with many DatabaseObjects that have different _displayNames, sometimes the\n" + 
			"    -- _displayName indiciates as version-number change in an object, but the reference identifier remains the same.\n" + 
			"    group by subq.combined_identifier, ReferenceEntity.identifier, referenceDatabase, _class, _displayName, subq.species_db_id, subq.species_name\n" + 
			"    having count(ReferenceEntity.db_id) > 1) as subq1\n" + 
			"inner join ReferenceDatabase_2_name on ReferenceDatabase_2_name.DB_ID = subq1.referenceDatabase\n" + 
			"where ReferenceDatabase_2_name.name_rank = 0\n" + 
			"order by duplicate_count, ReferenceDatabase_2_name.name, identifier;";
	// This is used to keep track of the maximum width of each column. This will be important when
	// the time comes to actual *print* the report.
	private Map<REPORT_KEYS, Integer> maxColWidths = new HashMap<>();
	
	/**
	 * Creates a new DuplicateIdentifierReporter
	 * @param adaptor
	 */
	public DuplicateIdentifierReporter(MySQLAdaptor adaptor)
	{
		this.dbAdapter = adaptor;
		for (REPORT_KEYS key : REPORT_KEYS.values())
		{
			// initializxe the map of column widths with the widths of header columns.
			updateMaxColumnWidths(key,key.toString());
		}
	}
	
	/**
	 * Creates a report of duplicated identifiers. The report is represented as a list of maps - each map is a key-value mapping of column names to values.
	 * @return The report data.
	 * @throws Exception 
	 * @throws NumberFormatException 
	 */
	public List<Map<REPORT_KEYS, String>> createReport() throws NumberFormatException, Exception
	{
		Map<Long,MySQLAdaptor> adapterPool = Collections.synchronizedMap( new HashMap<Long,MySQLAdaptor>() );
		List<Map<REPORT_KEYS, String>> rows = new ArrayList<>();
		
		ResultSet rs = this.dbAdapter.executeQuery(DuplicateIdentifierReporter.query, null);
		while (rs.next())
		{
			boolean reportThisRow = true;
			Map<REPORT_KEYS, String> rowData = new HashMap<>();
			// For each REPORT_KEY (aka "column names"), add them to the a map.
			// Also: Update this instance's map of maximum column widths
			for (REPORT_KEYS key : REPORT_KEYS.values())
			{
				String value = rs.getString(key.toString());
				// Sometimes you might get a null, such as when there is no Species information for a ReferenceMolecule
				if (value == null)
				{
					// Use the empty string literal (instead of an actual NULL value) so we can get the length of it, if necessary.
					value = "";
				}
				else
				{
					if (key.equals(REPORT_KEYS.ReferenceEntity_DB_IDs))
					{
						Map<Long, Integer> referrerCounts = Collections.synchronizedMap(new HashMap<>());
						String[] dbIDs = value.split(",");
						// Now, we need to check the referrers of each entity and ensure that they are different. And Identifier is not *really* duplicated if 
						// the "duplicates" are referred to by different entities.
						if (dbIDs != null && dbIDs.length > 1)
						{
							//process multiple Reference Entities in parallel
							Arrays.asList(dbIDs).parallelStream().forEach(refEntID -> {
								try
								{
									// TODO: Create a common adapter pool for everywhere it is needed.
									MySQLAdaptor localAdapter ;
									long threadID = Thread.currentThread().getId();
									if (adapterPool.containsKey(threadID))
									{
										localAdapter = adapterPool.get(threadID);
									}
									else
									{
										logger.debug("Creating new SQL Adaptor for thread {}", Thread.currentThread().getId());
										localAdapter = new MySQLAdaptor(this.dbAdapter.getDBHost(), this.dbAdapter.getDBName(), this.dbAdapter.getDBUser(),this.dbAdapter.getDBPwd(), this.dbAdapter.getDBPort());
										adapterPool.put(threadID, localAdapter);
									}
									
									GKInstance refEntInst = this.dbAdapter.fetchInstance(Long.parseLong(refEntID));
									// Loop through the attributes of the instance's class that are referrer attributes.
									// This way, we don't need an explicit list of attributes for getReferrers, we just
									// use the metadata to drive this loop.
									for (SchemaAttribute attrib : ((Collection<SchemaAttribute>) refEntInst.getSchemClass().getReferers()) )
									{
										@SuppressWarnings("unchecked")
										Collection<GKInstance> referrers = refEntInst.getReferers(attrib);
										for (GKInstance referrer : referrers)
										{
											// Add the referrer's ID to a Map. If the same Referrer ID appears more than once, it means the identifier is referred to by the same thing more than once,
											// and that is what we REALLY need to report.
											Long referrerID = referrer.getDBID();
											if (referrerCounts.containsKey(referrerID))
											{
												referrerCounts.put(referrerID, referrerCounts.get(referrerID) + 1);
											}
											else
											{
												referrerCounts.put(referrerID, 1);
											}
										}
									}
								}
								catch (Exception e)
								{
									e.printStackTrace();
								}
							});
						}
						// Include this row in the report IF this identifier is referred to by the same referrer more than once.
						//synchronized(referrerCounts)
						{
							reportThisRow = referrerCounts.keySet().parallelStream().anyMatch(k -> referrerCounts.get(k) > 1);
						}
					}
				}
				if (reportThisRow)
				{
					rowData.put(key, value);
					// Check to see if this key is already in the map of column widths
					updateMaxColumnWidths(key, value);
				}
			}
			if (reportThisRow)
			{
				rows.add(rowData);
			}
		}
		
		return rows;
	}

	/**
	 * Updates the instance's maxColWidths map, for some key.
	 * @param key - The key in the map to update.
	 * @param value - The value whose length will be tested.
	 */
	private void updateMaxColumnWidths(REPORT_KEYS key, String value)
	{
		if (this.maxColWidths.containsKey(key))
		{
			// If the current width is smaller than the width of the current value, put the new value's width into the map.
			if (this.maxColWidths.get(key) < value.length())
			{
				this.maxColWidths.put(key, value.length());
			}
		}
		else
		{
			this.maxColWidths.put(key, value.length());
		}
	}
	
	/**
	 * Generates a printable version of a report.
	 * @param reportData - the report data.
	 * @return A StringBuilder is returned, containing the table-formatted String-representation of the report data. Columns are adjusted to be as
	 * wide as the widest value in that column - this report could be rather wide. 
	 */
	public StringBuilder generatePrintableReport(List<Map<REPORT_KEYS, String>> reportData)
	{
		StringBuilder sb = new StringBuilder();
		
		int totalReportWidth = 0;
		// First, create the header.
		for (REPORT_KEYS key : REPORT_KEYS.values())
		{
			int colWidth = Math.max(this.maxColWidths.get(key), key.toString().length());
			totalReportWidth += colWidth + FIELD_SEPARATOR.length();
			sb.append(StringUtils.rightPad(key.toString(), colWidth, " ")).append(FIELD_SEPARATOR);
		}
		sb.append("\n");
		sb.append(StringUtils.rightPad("-", totalReportWidth, "-"));
		sb.append("\n");
		// Now, append the rows.
		for (Map<REPORT_KEYS, String> rowData : reportData)
		{
			for(REPORT_KEYS key : REPORT_KEYS.values())
			{
				sb.append(StringUtils.rightPad(rowData.get(key), this.maxColWidths.get(key), " ")).append(FIELD_SEPARATOR);
			}
			sb.append("\n");
		}
		
		return sb;
	}
	
}
