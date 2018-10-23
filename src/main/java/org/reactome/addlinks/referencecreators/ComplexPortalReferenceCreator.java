package org.reactome.addlinks.referencecreators;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;
import org.gk.persistence.MySQLAdaptor.ReverseAttributeQueryRequest;
import org.gk.schema.GKSchemaAttribute;
import org.reactome.addlinks.db.ReferenceObjectCache;

public class ComplexPortalReferenceCreator extends SimpleReferenceCreator<List<String>>
{
	public ComplexPortalReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring,
			String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}
	
	@Override
	public void createIdentifiers(long personID, Map<String,List<String>> mappings, List<GKInstance> sourceReferences) throws IOException
	{
		ReferenceObjectCache cache = new ReferenceObjectCache(this.adapter, true);
		
		AtomicInteger createdCounter = new AtomicInteger(0);
		AtomicInteger notCreatedCounter = new AtomicInteger(0);
		AtomicInteger xrefAlreadyExistsCounter = new AtomicInteger(0);
		AtomicInteger referencingReactome = new AtomicInteger(0);
		AtomicInteger referencingUniprot = new AtomicInteger(0);
		
		for (String complexPortalID : mappings.keySet())
		{
			try
			{
				// A ComplexPortal ID could have mappings to Uniprot, Reactome, or both.
				// NOTE: In today's (2018-10-23) dev stand-up call, I confirmed with Robin that we'll just stick with mapping identifiers that already have a Reactome identifier.
				// Later, there may be a decision to expand this, but for now we'll keep it simple.
				for (String uniprotOrReactomeIdentifier : mappings.get(complexPortalID))
				{
					if (uniprotOrReactomeIdentifier.matches("R-[a-zA-Z]{3}.+"))
					{
						if (cache.getStableIdentifierCache().containsKey(uniprotOrReactomeIdentifier))
						{
							GKInstance instance = cache.getStableIdentifierCache().get(uniprotOrReactomeIdentifier);
							// Ensure that we only add identifiers to things of the correct class, which have the correct attribute. We could
							// avoid this if we modified that cache to cache by Stable Identifier AND SchemaClass.
							if (instance.getSchemClass().isa(this.classReferringToRefName) && instance.getSchemClass().isValidAttribute(this.referringAttributeName))
							{
								// check that the reference is not already there.
								if (!this.checkXRefExists(instance, complexPortalID))
								{
									Long speciesID = ((GKInstance)instance.getAttributeValue(ReactomeJavaConstants.species)).getDBID();
									if (!this.testMode)
									{
										this.refCreator.createIdentifier(complexPortalID, String.valueOf(instance.getDBID()), targetRefDB, personID, this.getClass().getName(), speciesID);
									}
									createdCounter.incrementAndGet();
									referencingReactome.incrementAndGet();
								}
								else
								{
									xrefAlreadyExistsCounter.incrementAndGet();
								}
							}
						}
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		logger.info("{} Reference creation summary:\n"
				+ "\t# Identifiers created: {}; {} for Reactome identifiers, {} for Uniprot identifiers\n"
				+ "\t# Identifiers which already existed: {} \n"
				+ "\t# Identifiers that were not created: {}",
				this.targetRefDB, 
				createdCounter.get(), referencingReactome.get(), referencingUniprot.get(), xrefAlreadyExistsCounter.get(), notCreatedCounter.get());

	}
}