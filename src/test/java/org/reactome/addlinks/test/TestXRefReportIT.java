package org.reactome.addlinks.test;

import static org.junit.Assert.*;

import java.sql.SQLException;

import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactome.addlinks.db.CrossReferenceReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration("/test-application-context.xml")
@RunWith(org.springframework.test.context.junit4.SpringJUnit4ClassRunner.class)
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
}
