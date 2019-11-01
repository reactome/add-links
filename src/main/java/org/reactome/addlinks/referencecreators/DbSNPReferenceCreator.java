package org.reactome.addlinks.referencecreators;

import org.apache.logging.log4j.Level;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.db.ReferenceObjectCache;

public class DbSNPReferenceCreator extends EntrezGeneBasedReferenceCreator
{
	private  ReferenceObjectCache objectCache;
	
	public void setObjectCache(ReferenceObjectCache objectCache)
	{
		this.objectCache = objectCache;
	}

	public DbSNPReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
		this.logger = this.createLogger(refCreatorName, "RollingRandomAccessFile", this.getClass().getName(), true, Level.TRACE, this.logger, "Reference Creator");
	}

	@Override
	public void createEntrezGeneReference(String identifierValue, String referencedObject, String speciesID, Long personID) throws Exception
	{
		boolean isHuman = this.objectCache.getSpeciesNamesToIds().get("Homo sapiens").contains(speciesID);
		if (isHuman)
		{
			super.createEntrezGeneReference(identifierValue, referencedObject, speciesID, personID);
		}
		else
		{
			// Message if we can't create a reference, probably because it's non-Human and the refDB is dbSNP.
			this.logger.debug("\tNOT creating identifier {} for {}@{}, because species is non-human ({})", identifierValue, referencedObject, this.targetRefDB, speciesID + " - " + objectCache.getSpeciesNamesByID().get(speciesID).get(0));
		}
	}
}
