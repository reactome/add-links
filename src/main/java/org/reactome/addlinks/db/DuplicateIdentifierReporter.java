package org.reactome.addlinks.db;

import org.gk.persistence.MySQLAdaptor;

/**
 * Reports on duplicated identifiers.
 * @author sshorser
 *
 */
public class DuplicateIdentifierReporter
{
	private MySQLAdaptor dbAdapter;
	
	private static String query = "SELECT distinct subq1.*, ReferenceDatabase_2_name.name, Species.db_id as specied_db_id, Taxon_2_name.name as species_name\n" + 
			"from (\n" + 
			"	select count(ReferenceEntity.DB_ID) as duplicate_count, ReferenceEntity.identifier, group_concat(ReferenceEntity.db_id) as ReferenceEntity_DB_IDs, ReferenceEntity.referenceDatabase, DatabaseObject._class, subq.combined_identifier\n" + 
			"	from ReferenceEntity \n" + 
			"	inner join DatabaseObject on DatabaseObject.db_id = ReferenceEntity.db_id\n" + 
			"    -- subquery to produce a combined identifier that contains the identifier string and the object's displayName\n" + 
			"	inner join (select ReferenceEntity.*, concat(coalesce(ReferenceEntity.identifier,'NULL'),';', coalesce(DatabaseObject._displayName,'NULL')) as combined_identifier\n" + 
			"		from ReferenceEntity\n" + 
			"		inner join DatabaseObject on ReferenceEntity.db_id = DatabaseObject.db_id) as subq on subq.db_id = ReferenceEntity.db_id\n" + 
			"	where ReferenceEntity.identifier is not null\n" + 
			"	-- First, group on the combined identifer - the identifier concatenated with _displayName.\n" + 
			"    -- This is because an identifier could be associated with many DatabaseObjects that have different _displayNames, sometimes the\n" + 
			"    -- _displayName indiciates as version-number change in an object, but the reference identifier remains the same.\n" + 
			"	group by subq.combined_identifier, ReferenceEntity.identifier, referenceDatabase, _class\n" + 
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
	
}
