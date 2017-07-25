package org.reactome.addlinks.referencecreators;

import java.util.List;
import java.util.Map;

import org.gk.persistence.MySQLAdaptor;

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
}
