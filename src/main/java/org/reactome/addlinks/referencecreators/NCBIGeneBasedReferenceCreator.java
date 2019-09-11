package org.reactome.addlinks.referencecreators;

import java.util.List;
import java.util.Map;

import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.db.ReferenceObjectCache;

public abstract class NCBIGeneBasedReferenceCreator extends SimpleReferenceCreator< Map<String,List<String>> >
{
	protected List<EntrezGeneBasedReferenceCreator> entrezGeneReferenceCreators;
	
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

	protected void runNCBIGeneRefCreators(long personID, String[] parts, ReferenceObjectCache objectCache) throws Exception
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
			
			entrezGeneCreator.createEntrezGeneReference(parts[0], parts[1], parts[2], personID);
		}
	}
}
