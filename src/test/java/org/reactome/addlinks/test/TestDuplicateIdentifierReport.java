package org.reactome.addlinks.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.reactome.addlinks.db.DuplicateIdentifierReporter;
import org.reactome.addlinks.db.DuplicateIdentifierReporter.REPORT_KEYS;

public class TestDuplicateIdentifierReport
{

	@Mock
	MySQLAdaptor adaptor;
	
	@Mock
	ResultSet mockResultSet;
	
	@Before
	public void setup() throws InvalidAttributeException, Exception
	{
		MockitoAnnotations.initMocks(this);
		
		// Two rows in the report.
		Mockito.when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
		
		Mockito.when(mockResultSet.getString("duplicate_count")).thenReturn("2").thenReturn("3");
		Mockito.when(mockResultSet.getString("identifier")).thenReturn("ENSG0000000012345").thenReturn("CHEBI:993845");
		Mockito.when(mockResultSet.getString("ReferenceEntity_DB_IDs")).thenReturn("1112334451,1112334452").thenReturn("944321,944322,944323");
		Mockito.when(mockResultSet.getString("object_type")).thenReturn("ReferenceDNASequence").thenReturn("ReferenceMolecule");
		Mockito.when(mockResultSet.getString("display_name")).thenReturn("ENSG0000000012345:blah blah blah").thenReturn("[insert chemical name here]");
		Mockito.when(mockResultSet.getString("combined_identifier")).thenReturn("ENSG0000000012345;ENSG0000000012345:blah blah blah").thenReturn("CHEBI:993845;[insert chemical name here]");
		Mockito.when(mockResultSet.getString("ref_db_name")).thenReturn("ENSEMBL").thenReturn("CHEBI");
		Mockito.when(mockResultSet.getString("species_db_id")).thenReturn("48887").thenReturn(null);
		Mockito.when(mockResultSet.getString("species_name")).thenReturn("Homo sapiens").thenReturn(null);
		
		Mockito.when(adaptor.executeQuery(anyString(), any())).thenReturn(mockResultSet);

	}
	
	@Test
	public void testCreateReportMap() throws SQLException
	{
		DuplicateIdentifierReporter reporter = new DuplicateIdentifierReporter(adaptor);
	
		List<Map<REPORT_KEYS, String>> rows = reporter.createReport();
		
		assertNotNull(rows);
		// Should be two data rows.
		assertTrue(rows.size() == 2);
	}
	
	@Test
	public void testPrintReport() throws SQLException
	{
		DuplicateIdentifierReporter reporter = new DuplicateIdentifierReporter(adaptor);
		
		List<Map<REPORT_KEYS, String>> rows = reporter.createReport();
		StringBuilder sb = reporter.generatePrintableReport(rows);
		
		assertNotNull(sb);
		assertTrue(sb.toString().length() > 0);
		// should be 4 lines (header line, separator line, 2 data lines)
		assertTrue(sb.toString().split("\n").length == 4);
		System.out.println(sb.toString());
	}
}
