package org.reactome.addlinks.referencecreators;

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.addlinks.db.ReferenceObjectCache;

/**
 * This class will create a single reference based on an EntrezGene ID for another EntrezGeneID-based database, such as BioGPS.
 * @author sshorser
 *
 */
public class EntrezGeneBasedReferenceCreator extends SimpleReferenceCreator<String>
{
	protected Logger logger;

	public EntrezGeneBasedReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
		this.logger = this.createLogger(refCreatorName, "RollingRandomAccessFile", this.getClass().getName(), true, Level.TRACE, this.logger, "Reference Creator");
	}

	
	public void createEntrezGeneReference(String identifierValue, String referencedObject, String speciesID, Long personID) throws Exception
	{
		this.logger.trace("Possible new identifier {} for {}", identifierValue, referencedObject);
		GKInstance inst = this.adapter.fetchInstance(new Long(referencedObject));
		@SuppressWarnings("unchecked")
		List<GKInstance> xrefs = (List<GKInstance>) inst.getAttributeValuesList(referringAttributeName);
		boolean xrefAlreadyExists = false;
		String xrefString = "";
		for (GKInstance xref : xrefs)
		{
			String xrefIdentifier = xref.getAttributeValue(ReactomeJavaConstants.identifier) != null
									? xref.getAttributeValue(ReactomeJavaConstants.identifier).toString()
									: null;
			String xrefRefDB = xref.getAttributeValue(ReactomeJavaConstants.referenceDatabase).toString();
			xrefString += "cross-reference: "+xrefIdentifier+"@"+xrefRefDB+" ; ";
			
			// We won't add a cross-reference if it already exists, and for the same Ref database (it's possible that the identifier could be
			// used in other reference databases).
			if (identifierValue.equals(xrefIdentifier) && xrefRefDB.contains(this.targetRefDB))
			{
				xrefAlreadyExists = true;
				// Break out of the xrefs loop - we found an existing cross-reference that matches so there's no point 
				// in letting the loop run longer.
				// TODO: rewrite into a while-loop condition (I don't like breaks that much).
				break;
			}
		}
		if (!xrefString.equals(""))
		{
			this.logger.trace("\t {}",xrefString);
		}
		if (!xrefAlreadyExists)
		{
			this.logger.trace("\tCreate identifier {} for {}@{}", identifierValue, referencedObject, this.targetRefDB);
			this.refCreator.createIdentifier(identifierValue, referencedObject, this.targetRefDB, personID, this.getClass().getName() + "( for " + this.targetRefDB + " )", Long.valueOf(speciesID));
		}
	}
}
