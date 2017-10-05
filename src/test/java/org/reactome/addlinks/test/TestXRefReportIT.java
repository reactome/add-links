package org.reactome.addlinks.test;

import static org.junit.Assert.assertNotNull;

import java.sql.SQLException;
import java.util.Map;

import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
//import org.powermock.reflect.Whitebox;
import org.reactome.addlinks.db.CrossReferenceReporter;
import org.reactome.addlinks.db.CrossReferenceReporter.REPORT_KEYS;
import org.reactome.addlinks.db.CrossReferenceReporter.ReportMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(SpringJUnit4ClassRunner.class)
@PowerMockIgnore("javax.management.*")
@ContextConfiguration("/test-application-context.xml")
@PrepareForTest( { CrossReferenceReporter.class, REPORT_KEYS.class, ReportMap.class } )
public class TestXRefReportIT
{

	@Autowired
	MySQLAdaptor dbAdapter;

	@Test
	public void testPrintReport() throws SQLException
	{
		CrossReferenceReporter reporter = new CrossReferenceReporter(dbAdapter);
		
		String report = reporter.printReport();
		
		assertNotNull(report);
		System.out.println(report);
	}
	
	@Test
	public void testPrintDiffReport() throws Exception
	{
		CrossReferenceReporter reporter =  new CrossReferenceReporter(dbAdapter);
		
		//CrossReferenceReporter reporter = PowerMock.createPartialMock(CrossReferenceReporter.class, "createReportMap");
		//Map<String,Map<String,Integer>> reportMap = Whitebox.invokeMethod(reporter, "createReportMap");
		Map<String,Map<String,Integer>> reportMap = reporter.createReportMap();
		reportMap.get("OMIM").remove("ReferenceDNASequence");
		
		CrossReferenceReporter reporter2 =  new CrossReferenceReporter(new MySQLAdaptor(dbAdapter.getDBHost(), "test_reactome_61", dbAdapter.getDBUser(), dbAdapter.getDBPwd(), dbAdapter.getDBPort()));
		
		String report = reporter2.printReportWithDiffs(reportMap);
		
		assertNotNull(report);
		System.out.println(report);
	}
}
