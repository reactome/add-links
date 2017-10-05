package org.reactome.addlinks.referencecreators;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

public class IntActReferenceCreator extends SimpleReferenceCreator<List<String>>
{
	public IntActReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring,
			String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}
	
	@Override
	public void createIdentifiers(long personID, Map<String,List<String>> mappings, List<GKInstance> sourceReferences) throws IOException
	{
		AtomicInteger createdCounter = new AtomicInteger(0);
		AtomicInteger notCreatedCounter = new AtomicInteger(0);
		AtomicInteger xrefAlreadyExistsCounter = new AtomicInteger(0);

		for (String reactomeStableID : mappings.keySet())
		{
			try
			{
				// I don't expect more than one instance, but the Adaptor function will return a collection, so let's use the whole thing, just in case.
				@SuppressWarnings("unchecked")
				Collection<GKInstance> stableIdentifierObjects = ((Collection<GKInstance>)this.adapter.fetchInstanceByAttribute(ReactomeJavaConstants.StableIdentifier, ReactomeJavaConstants.identifier, " = ", reactomeStableID));
				for (GKInstance stId : stableIdentifierObjects)
				{
					@SuppressWarnings("unchecked")
					Collection<GKInstance> reactomeInstances = (Collection<GKInstance>) this.adapter.fetchInstanceByAttribute(this.classReferringToRefName, ReactomeJavaConstants.stableIdentifier, " = ", stId);
					// I don't expect more than one instance, but the Adaptor function will return a collection, so let's use the whole thing, just in case.
					for (GKInstance reactomeInstance : reactomeInstances)
					{
						if (reactomeInstance != null)
						{
							for (String complexPortalIdentifier : mappings.get(reactomeStableID))
							{
								if (!this.checkXRefExists(reactomeInstance, complexPortalIdentifier))
								{
									Long speciesID = ((GKInstance)reactomeInstance.getAttributeValue(ReactomeJavaConstants.species)).getDBID();
									if (!this.testMode)
									{
										this.refCreator.createIdentifier(complexPortalIdentifier, String.valueOf(reactomeInstance.getDBID()), targetRefDB, personID, this.getClass().getName(), speciesID);
									}
									createdCounter.incrementAndGet();
								}
								else
								{
									xrefAlreadyExistsCounter.incrementAndGet();
								}
							}
						}
						else
						{
							this.logger.warn("Reactome Stable Identifier {} was in the IntAct Complex Portal mapping file but it could not be found in Reactome!", reactomeStableID);
							notCreatedCounter.incrementAndGet();
						}
					}
				}
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		logger.info("{} Reference creation summary:\n"
				+ "\t# Identifiers created: {}\n"
				+ "\t# Identifiers which already existed: {} \n"
				+ "\t# Identifiers that were not created: {}",
				this.targetRefDB, 
				createdCounter.get(), xrefAlreadyExistsCounter.get(), notCreatedCounter.get());

	}
}