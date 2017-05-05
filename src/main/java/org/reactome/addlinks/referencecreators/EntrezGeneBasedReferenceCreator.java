package org.reactome.addlinks.referencecreators;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

/**
 * This class will create a single reference based on an EntrezGene ID for another EntrezGeneID-based database, such as BioGPS.
 * @author sshorser
 *
 */
public class EntrezGeneBasedReferenceCreator extends SimpleReferenceCreator<String>
{
	private Logger logger;

	public EntrezGeneBasedReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
		this.logger = this.createLogger(refCreatorName, "RollingRandomAccessFile", this.getClass().getName(), true, Level.TRACE, this.logger, "Reference Creator");
	}

	
	public void createEntrezGeneReference(String identifierValue, String referencedObject, String speciesID, Long personID) throws Exception
	{
		this.refCreator.createIdentifier(identifierValue, referencedObject, this.targetRefDB, personID, this.getClass().getName());
	}
}
