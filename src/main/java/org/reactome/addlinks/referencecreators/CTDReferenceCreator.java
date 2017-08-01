package org.reactome.addlinks.referencecreators;

import java.util.Map;

import org.apache.logging.log4j.Level;
import org.gk.persistence.MySQLAdaptor;

public class CTDReferenceCreator extends EntrezGeneBasedReferenceCreator
{

	protected Map<String,String> ncbiGenesInCTD;
	
	public CTDReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
		this.logger = this.createLogger(refCreatorName, "RollingRandomAccessFile", this.getClass().getName(), true, Level.TRACE, this.logger, "Reference Creator");
	}

	@Override
	public void createEntrezGeneReference(String identifierValue, String referencedObject, String speciesID, Long personID) throws Exception
	{
		if (this.ncbiGenesInCTD.keySet().contains(identifierValue))
		{
			super.createEntrezGeneReference(identifierValue, referencedObject, speciesID, personID);
		}
	}

	public void setNcbiGenesInCTD(Map<String, String> ncbiGenesInCTD)
	{
		this.ncbiGenesInCTD = ncbiGenesInCTD;
	}
}
