package org.reactome.addlinks.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.reactome.addlinks.db.DuplicateIdentifierReporter;
import org.reactome.addlinks.db.DuplicateIdentifierReporter.REPORT_KEYS;

@PowerMockIgnore({"javax.management.*","javax.net.ssl.*"})
@RunWith( PowerMockRunner.class )
@PrepareForTest({MySQLAdaptor.class, DuplicateIdentifierReporter.class, ResultSet.class} )
public class TestDuplicateIdentifierReport
{

	@Mock
	private MySQLAdaptor adaptor;
	
	@Mock
	private ResultSet mockResultSet;
	
	@Mock
	private GKInstance mockInstance;

	@Mock
	private GKInstance mockReferrer;
	
	@Mock
	private SchemaClass mockSchemaClass;
	
	@Mock
	private SchemaAttribute mockAttribute;
	
	@Before
	public void setup() throws InvalidAttributeException, Exception
	{
		MockitoAnnotations.initMocks(this);
		
		// Two rows in the report.
		PowerMockito.when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
		
		PowerMockito.when(mockResultSet.getString("duplicate_count")).thenReturn("2").thenReturn("3");
		PowerMockito.when(mockResultSet.getString("identifier")).thenReturn("ENSG0000000012345").thenReturn("CHEBI:993845");
		PowerMockito.when(mockResultSet.getString("ReferenceEntity_DB_IDs")).thenReturn("1112334451,1112334452").thenReturn("944321,944322,944323");
		PowerMockito.when(mockResultSet.getString("object_type")).thenReturn("ReferenceDNASequence").thenReturn("ReferenceMolecule");
		PowerMockito.when(mockResultSet.getString("display_name")).thenReturn("ENSG0000000012345:blah blah blah").thenReturn("[insert chemical name here]");
		PowerMockito.when(mockResultSet.getString("combined_identifier")).thenReturn("ENSG0000000012345;ENSG0000000012345:blah blah blah").thenReturn("CHEBI:993845;[insert chemical name here]");
		PowerMockito.when(mockResultSet.getString("ref_db_name")).thenReturn("ENSEMBL").thenReturn("CHEBI");
		PowerMockito.when(mockResultSet.getString("species_db_id")).thenReturn("48887").thenReturn(null);
		PowerMockito.when(mockResultSet.getString("species_name")).thenReturn("Homo sapiens").thenReturn(null);
		
		PowerMockito.when(this.mockReferrer.getDBID()).thenReturn(1234L);
		
		PowerMockito.when(this.mockInstance.getReferers(this.mockAttribute)).thenReturn(Arrays.asList(this.mockReferrer));
		
		PowerMockito.when(this.mockSchemaClass.getReferers()).thenReturn(Arrays.asList(this.mockAttribute));
		
		PowerMockito.when(this.mockInstance.getSchemClass()).thenReturn(this.mockSchemaClass);
		PowerMockito.when(adaptor.fetchInstance(any(Long.class))).thenReturn(this.mockInstance);
		
		PowerMockito.when(adaptor.executeQuery(anyString(), any())).thenReturn(mockResultSet);
		PowerMockito.when(adaptor.getDBHost()).thenReturn("host");
		PowerMockito.when(adaptor.getDBName()).thenReturn("name");
		PowerMockito.when(adaptor.getDBUser()).thenReturn("user");
		PowerMockito.when(adaptor.getDBPwd()).thenReturn("password");
		PowerMockito.when(adaptor.getDBPort()).thenReturn(3306);
		PowerMockito.whenNew(MySQLAdaptor.class).withAnyArguments().thenReturn(adaptor);
		
	}
	
	@Test
	public void testCreateReportMap() throws SQLException, NumberFormatException, Exception
	{
		DuplicateIdentifierReporter reporter = new DuplicateIdentifierReporter(adaptor);
	
		List<Map<REPORT_KEYS, String>> rows = reporter.createReport();
		
		assertNotNull(rows);
		// Should be two data rows.
		System.out.println(rows.size());
		System.out.println(rows.toString());
		assertTrue(rows.size() == 1);
	}
	
	@Test
	public void testPrintReport() throws SQLException, NumberFormatException, Exception
	{
		DuplicateIdentifierReporter reporter = new DuplicateIdentifierReporter(adaptor);
		
		List<Map<REPORT_KEYS, String>> rows = reporter.createReport();
		StringBuilder sb = reporter.generatePrintableReport(rows);
		
		assertNotNull(sb);
		assertTrue(sb.toString().length() > 0);
		System.out.println(sb.toString());
		// should be 4 lines (header line, separator line, 2 data lines)
		assertTrue(sb.toString().split("\n").length == 3);
		
	}
}

