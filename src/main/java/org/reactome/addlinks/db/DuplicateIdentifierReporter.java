package org.reactome.addlinks.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.gk.persistence.MySQLAdaptor;

/**
 * Reports on duplicated identifiers.
 * @author sshorser
 *
 */
public class DuplicateIdentifierReporter
{
	private static final String FIELD_SEPARATOR = " | ";

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
	
	private static String query = "SELECT distinct subq1.duplicate_count, subq1.identifier, subq1.ReferenceEntity_DB_IDs, subq1._class AS object_type, subq1._displayName as display_name, subq1.combined_identifier, ReferenceDatabase_2_name.name as ref_db_name, Species.db_id as specied_db_id, Taxon_2_name.name as species_name\n" + 
			"from (\n" + 
			"	select count(ReferenceEntity.DB_ID) as duplicate_count, ReferenceEntity.identifier, group_concat(ReferenceEntity.db_id) as ReferenceEntity_DB_IDs, ReferenceEntity.referenceDatabase, DatabaseObject._class, DatabaseObject._displayName, subq.combined_identifier\n" + 
			"	from ReferenceEntity \n" + 
			"	inner join DatabaseObject on DatabaseObject.db_id = ReferenceEntity.db_id\n" + 
			"    -- subquery to produce a combined identifier that contains the identifier string and the object's displayName\n" + 
			"	inner join (select ReferenceEntity.*, concat(coalesce(ReferenceEntity.identifier,'NULL'),';', coalesce(DatabaseObject._displayName,'NULL')) as combined_identifier\n" + 
			"		from ReferenceEntity\n" + 
			"		inner join DatabaseObject on ReferenceEntity.db_id = DatabaseObject.db_id) as subq on subq.db_id = ReferenceEntity.db_id\n" + 
			"	where ReferenceEntity.identifier is not null\n" + 
			"    -- First, group on the combined identifer - the identifier concatenated with _displayName.\n" + 
			"    -- This is because an identifier could be associated with many DatabaseObjects that have different _displayNames, sometimes the\n" + 
			"    -- _displayName indiciates as version-number change in an object, but the reference identifier remains the same.\n" + 
			"	group by subq.combined_identifier, ReferenceEntity.identifier, referenceDatabase, _class, _displayName\n" + 
			"	having count(ReferenceEntity.db_id) > 1) as subq1\n" + 
			"inner join ReferenceDatabase_2_name on ReferenceDatabase_2_name.DB_ID = subq1.referenceDatabase\n" + 
			"INNER JOIN ReferenceEntity ON ReferenceEntity.identifier = subq1.identifier\n" + 
			"-- start OUTER joining from ReferenceEntity through ReferenceSequence to Taxon2Name to get the species name of\n" + 
			"-- the reference object. But not all References have a species (such as ReferenceMolecule),\n" + 
			"-- hence the OUTER joins.\n" + 
			"LEFT OUTER JOIN ReferenceSequence ON ReferenceEntity.DB_ID = ReferenceSequence.DB_ID\n" + 
			"LEFT OUTER JOIN Species ON ReferenceSequence.species = Species.DB_ID\n" + 
			"LEFT OUTER JOIN Taxon_2_name ON (Taxon_2_name.DB_ID = Species.DB_ID AND Taxon_2_name.name_rank = 0)\n" + 
			"where ReferenceDatabase_2_name.name_rank = 0\n" + 
			"order by duplicate_count desc, ReferenceDatabase_2_name.name asc;";
	
	// This is used to keep track of the maximum width of each column. This will be important when
	// the time comes to actual *print* the report.
	private Map<REPORT_KEYS, Integer> maxColWidths = new HashMap<>();
	
	public DuplicateIdentifierReporter(MySQLAdaptor adaptor)
	{
		this.dbAdapter = adaptor;
		for (REPORT_KEYS key : REPORT_KEYS.values())
		{
			// initializxe the map of column widths with the widths of header columns.
			if (this.maxColWidths.containsKey(key))
			{
				if (this.maxColWidths.get(key) < key.toString().length())
				{
					this.maxColWidths.put(key, key.toString().length());
				}
			}
			else
			{
				this.maxColWidths.put(key, key.toString().length());
			}
		}
	}
	
	public List<Map<REPORT_KEYS, String>> createReportMap() throws SQLException
	{
		List<Map<REPORT_KEYS, String>> rows = new ArrayList<>();
		
		ResultSet rs = this.dbAdapter.executeQuery(DuplicateIdentifierReporter.query, null);
		while (rs.next())
		{
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
				rowData.put(key, value);
				// Check to see if this key is already in the map of column widths
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
			rows.add(rowData);
		}
		
		return rows;
	}
	
	public StringBuilder generatePrintableReport(List<Map<REPORT_KEYS, String>> reportMap)
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
		for (Map<REPORT_KEYS, String> rowData : reportMap)
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
