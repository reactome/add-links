package org.reactome.addlinks.referencecreators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.db.ReferenceObjectCache;

public abstract class NCBIGeneBasedReferenceCreator extends SimpleReferenceCreator< Map<String,List<String>> >
{
	protected List<EntrezGeneBasedReferenceCreator> entrezGeneReferenceCreators;

	public List<EntrezGeneBasedReferenceCreator> getSubCreators()
	{
		return this.entrezGeneReferenceCreators != null
				? Collections.unmodifiableList(this.entrezGeneReferenceCreators)
				: Collections.unmodifiableList(new ArrayList<EntrezGeneBasedReferenceCreator>());
	}

	public NCBIGeneBasedReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}

	public NCBIGeneBasedReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}

	protected Map<String, String> ctdGenes;

	public void setCTDGenes(Map<String, String> mapping)
	{
		this.ctdGenes = mapping;
	}

	/**
	 * Executes reference creators. This will run reference creators for Entrezgene based references such as CTD, dbSNP, Monarch, BioGPS
	 * @param personID - the ID of the person creating the references.
	 * @param identifier - the new identifier.
	 * @param referencedObject - a string representation that this new identifier is an identifier for (can be as simple as the DB_ID of the object, or the toString representation - it's only used for logging).
	 * @param speciesID - the species ID of the referencedObject.
	 * @param objectCache - a ReferenceObject cache.
	 * @throws Exception
	 */
	protected void runNCBIGeneRefCreators(long personID, String identifier, String referencedObject, String speciesID, ReferenceObjectCache objectCache) throws Exception
	{
		for (EntrezGeneBasedReferenceCreator entrezGeneCreator : this.entrezGeneReferenceCreators)
		{
			// dbSNP ReferenceCreator needs access to a ReferenceObject cache.
			if (entrezGeneCreator instanceof DbSNPReferenceCreator)
			{
				((DbSNPReferenceCreator) entrezGeneCreator).setObjectCache(objectCache);
			}

			// CTD Reference Creator needs a list of NCBI genes that are in CTD.
			if (entrezGeneCreator instanceof CTDReferenceCreator )
			{
				((CTDReferenceCreator) entrezGeneCreator).setNcbiGenesInCTD(this.ctdGenes);
			}

			entrezGeneCreator.createEntrezGeneReference(identifier, referencedObject, speciesID, personID);
		}
	}
}
