package org.reactome.addlinks.referencecreators;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.reactome.addlinks.http.AddLinksHttpResponse;
import org.reactome.addlinks.http.client.AddLinksHttpClient;

public class ZincOrthologsReferenceCreator extends SimpleReferenceCreator< String >
{
	public ZincOrthologsReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB);
	}
	
	public ZincOrthologsReferenceCreator(MySQLAdaptor adapter, String classToCreate, String classReferring, String referringAttribute, String sourceDB, String targetDB, String refCreatorName)
	{
		super(adapter, classToCreate, classReferring, referringAttribute, sourceDB, targetDB, refCreatorName);
	}
	
	@Override
	public void createIdentifiers(long personID, Map<String, String> mapping, List<GKInstance> sourceReferences) throws IOException, Exception
	{
		AtomicInteger sourceIdentifiersWithNoMapping = new AtomicInteger(0);
		AtomicInteger sourceIdentifiersWithNewIdentifier = new AtomicInteger(0);
		AtomicInteger sourceIdentifiersWithExistingIdentifier = new AtomicInteger(0);
		
		@SuppressWarnings("unchecked")
		GKInstance refDB = (new ArrayList<GKInstance>(((Collection<GKInstance>) this.adapter.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", this.targetRefDB)))).get(0);
		List<String> thingsToCreate = new ArrayList<String>(mapping.keySet().size());
		//TODO: Maybe consider a slightly greater degree of parallelism here (~10-12 threads? instead of default 7 on my machine), since it's so damn slow?
		sourceReferences.parallelStream().forEach( sourceReference ->
		{
			try
			{
				String sourceReferenceIdentifier = (String) sourceReference.getAttributeValue(ReactomeJavaConstants.identifier);
				
				Long speciesID = null;
				@SuppressWarnings("unchecked")
				Collection<GKSchemaAttribute> attributes = (Collection<GKSchemaAttribute>) sourceReference.getSchemClass().getAttributes();
				if ( attributes.stream().filter(attr -> attr.getName().equals(ReactomeJavaConstants.species)).findFirst().isPresent())
				{
					GKInstance speciesInst = (GKInstance) sourceReference.getAttributeValue(ReactomeJavaConstants.species);
					if (speciesInst != null)
					{
						speciesID = new Long(speciesInst.getDBID());
					}
				}
				 
				if (mapping.containsKey(sourceReferenceIdentifier))
				{
					String targetRefDBIdentifier = (String)mapping.get(sourceReferenceIdentifier);
					logger.trace("{} ID: {}; {} ID: {}", this.sourceRefDB, sourceReferenceIdentifier, this.targetRefDB, targetRefDBIdentifier);
					// Look for cross-references.
					boolean xrefAlreadyExists = checkXRefExists(sourceReference, targetRefDBIdentifier);
					if (!xrefAlreadyExists)
					{
						// NOTE: Should also check http://zinc15.docking.org/orthologs/SRC_CHICK/predictions/subsets/purchasable.csv (example link) and if line count > 1 (meaning: more than 1 header line) the link is valid!
						AddLinksHttpClient client = new AddLinksHttpClient();
						// Seems that it usually takes > 30 seconds, so let's just give it a longer initial timeout.
						client.setTimeout(Duration.ofSeconds(50));
						URI uri = new URI(refDB.getAttributeValue("accessUrl").toString().replaceAll("###ID###", targetRefDBIdentifier).replaceAll("/$", ".csv"));
						client.setUri(uri);
						AddLinksHttpResponse response = client.executeRequest();
						String responseBody = response.getResponseBody();
						
						if (responseBody != null && !responseBody.trim().equals(""))
						{
							int numLines = responseBody.split("\n").length;
							if (numLines > 1)
							{
								logger.trace("\tCross-reference {} does not yet exist, need to create a new identifier!", targetRefDBIdentifier);
								sourceIdentifiersWithNewIdentifier.incrementAndGet();
								if (!this.testMode)
								{
									//this.refCreator.createIdentifier(targetRefDBIdentifier, String.valueOf(sourceReference.getDBID()), this.targetRefDB, personID, this.getClass().getName(), speciesID);
									thingsToCreate.add(targetRefDBIdentifier + ";" + String.valueOf(sourceReference.getDBID()) + ";" + ( speciesID == null ? "" : speciesID.toString() ));
								}
							}
							else
							{
								sourceIdentifiersWithNoMapping.incrementAndGet();
								logger.debug("Reference will NOT be created for {} because it had no content at {}", targetRefDBIdentifier, uri.toString());
							}
						}
					}
					else
					{
						sourceIdentifiersWithExistingIdentifier.incrementAndGet();
					}
				}
				else
				{
					sourceIdentifiersWithNoMapping.incrementAndGet();
					//logger.debug("UniProt ID {} is NOT in the database.", uniprotID);
				}
			}
			catch (Exception e)
			{
				// Only re-throw if the message is not a timeout. If the message is a timeout, keep trying other identifiers, but don't kill the whole process.
				if (!e.getMessage().matches("Connection timed out. Number of retries (\\d+) exceeded. No further attempts will be made."))
				{
					throw new Error(e);
				}
			}
		});
		
		for (String thingToCreate : thingsToCreate)
		{
			String[] parts = thingToCreate.split(";");
			Long speciesID = parts.length > 2 && parts[2] != null && !parts[2].trim().equals("")
								? Long.valueOf(parts[2])
								: null;
			this.refCreator.createIdentifier(parts[0], String.valueOf(parts[1]), this.targetRefDB, personID, this.getClass().getName(), speciesID);
		}
		
		logger.info("{} reference creation summary: \n"
				+ "\t# {} IDs with a new {} identifier (a new {} reference was created): {};\n"
				+ "\t# {} identifiers which already had the same {} reference (nothing new was created): {};\n"
				+ "\t# {} identifiers not in the {} mapping file (no new {} reference was created for them): {} ",
				this.targetRefDB,
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNewIdentifier.get(),
				this.sourceRefDB, this.targetRefDB, sourceIdentifiersWithExistingIdentifier.get(),
				this.sourceRefDB, this.targetRefDB, this.targetRefDB, sourceIdentifiersWithNoMapping.get());
	}
}
