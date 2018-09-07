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
		ReferenceObjectCache cache = new ReferenceObjectCache(this.adapter);
		
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
				for (String uniprotOrReactomeIdentifier : mappings.get(complexPortalID))
				{
					// TODO: Refactor the code in the if-else blocks - it can probably be simplified to a single function.
					// If Reactome identifier...
					if (uniprotOrReactomeIdentifier.matches("R-[a-zA-Z]{3}.+"))
					{
						// Now, we need to find this instance identified by uniprotOrReactomeIdentifier
						@SuppressWarnings("unchecked")
						Collection<GKInstance> instances = (Collection<GKInstance>) this.adapter.fetchInstanceByAttribute(ReactomeJavaConstants.DatabaseObject, ReactomeJavaConstants.stableIdentifier, " LIKE ", uniprotOrReactomeIdentifier);
						for (GKInstance instance : instances)
						{
//							@SuppressWarnings("unchecked")
//							Collection<GKInstance> instances = (Collection<GKInstance>) identifierInstance.getReferers(ReactomeJavaConstants.crossReference);

							// should only be one, but the function returns a collection...
//							for (GKInstance instance : instances)
//							{
								// check that the reference is not already there.
								if (!this.checkXRefExists(instance, complexPortalID))
								{
									Long speciesID = ((GKInstance)instance.getAttributeValue(ReactomeJavaConstants.species)).getDBID();
									if (!this.testMode)
									{
										// Updating the classReferringToRefName is necessary because if the identifier is a Reactome identifier, we're dealing with a Complex,
										// but if it's a Uniprot identifier, then we're dealing with some other PhysicalEntity, probably a ReferenceGeneProduct or EntityWithAccessionedSequence
										this.setClassReferringToRefName(instance.getSchemClass().getName());
										this.setReferringAttributeName(ReactomeJavaConstants.crossReference);
										this.setClassToCreateName(ReactomeJavaConstants.DatabaseIdentifier);
										this.refCreator.createIdentifier(uniprotOrReactomeIdentifier, String.valueOf(instance.getDBID()), targetRefDB, personID, this.getClass().getName(), speciesID);
									}
									createdCounter.incrementAndGet();
									referencingReactome.incrementAndGet();
								}
								else
								{
									xrefAlreadyExistsCounter.incrementAndGet();
								}
//							}
						}
					}
					// else it's a Uniprot
					else
					{
						List<GKInstance> fromCache = cache.getByIdentifier(uniprotOrReactomeIdentifier, ReactomeJavaConstants.ReferenceGeneProduct);
						
						// Now, we need to find this instance identified by uniprotOrReactomeIdentifier
//						@SuppressWarnings("unchecked")
//						Collection<GKInstance> identifierInstances = (Collection<GKInstance>) this.adapter.fetchInstanceByAttribute(ReactomeJavaConstants.DatabaseIdentifier, ReactomeJavaConstants.identifier, "=", uniprotOrReactomeIdentifier);
						if (fromCache != null)
						{
							for (GKInstance instance : fromCache)
							{
	//							@SuppressWarnings("unchecked")
	//							Collection<GKInstance> instances = (Collection<GKInstance>) identifierInstance.getReferers(ReactomeJavaConstants.crossReference);
								// should only be one, but the function returns a collection...
	//							for (GKInstance instance : instances)
								{
									this.setClassReferringToRefName(instance.getSchemClass().getName());
									this.setClassToCreateName(ReactomeJavaConstants.ReferenceDNASequence);
									this.setReferringAttributeName(ReactomeJavaConstants.crossReference);
									this.refCreator.setReferringAttribute((GKSchemaAttribute)instance.getSchemClass().getAttribute(ReactomeJavaConstants.crossReference));
									// check that the reference is not already there.
									if (!this.checkXRefExists(instance, complexPortalID))
									{
										Long speciesID = ((GKInstance)instance.getAttributeValue(ReactomeJavaConstants.species)).getDBID();
										if (!this.testMode)
										{
											// Updating the classReferringToRefName is necessary because if the identifier is a Reactome identifier, we're dealing with a Complex,
											// but if it's a Uniprot identifier, then we're dealing with some other PhysicalEntity, probably a ReferenceGeneProduct or EntityWithAccessionedSequence
											this.refCreator.createIdentifier(uniprotOrReactomeIdentifier, String.valueOf(instance.getDBID()), targetRefDB, personID, this.getClass().getName(), speciesID);
										}
										createdCounter.incrementAndGet();
										referencingUniprot.incrementAndGet();
									}
									else
									{
										xrefAlreadyExistsCounter.incrementAndGet();
									}
								}
							}
						}
//						ReverseAttributeQueryRequest raqr = this.adapter.createReverseAttributeQueryRequest(ReactomeJavaConstants.DatabaseIdentifier, ReactomeJavaConstants.identifier, "=", uniprotOrReactomeIdentifier);
//						@SuppressWarnings("unchecked")
//						Set<GKInstance> instances = (Set<GKInstance>) this.adapter._fetchInstance(Arrays.asList(raqr));
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