package org.reactome.addlinks.linkchecking;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.release.common.CustomLoggable;


public class LinkCheckManager implements CustomLoggable
{

	private static final String IDENTIFIER_TOKEN = "###ID###";
	private static Logger logger ;
	private MySQLAdaptor dbAdaptor;

	public LinkCheckManager()
	{
		if (LinkCheckManager.logger  == null)
		{
			LinkCheckManager.logger = this.createLogger("LinkCheckManager", "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG);
		}
	}

	public void setDbAdaptor(MySQLAdaptor adaptor)
	{
		this.dbAdaptor = adaptor;
	}

	/**
	 * Takes in a list of instances that have Identifier attributes and checks them as links.
	 * @param refDBInst - This accessURL of refDBInst will be used to generate the links that will be checked.
	 * @param instances - The instances.
	 * @param proportionToCheck - The proportion to check. 1.0 means ALL links could be checked.
	 * @param maxToCheck - The maximum actual number to check. If the list has 100000 elements, and proportionToCheck is 0.75, that means 75000 could be checked.
	 * If you set maxToCheck to 100, that overrides the number calculated by proportionToCheck and only 100 will be checked.
	 * @return A map. The key is the identifier, the value is a LinkCheckInfo object, {@link org.reactome.addlinks.linkchecking.LinkCheckInfo}
	 * @throws IllegalArgumentException This exception is thrown if proportionToCheck or maxToCheck are less than zero, or proportionToCheck is greater than 1.0.
	 */
	public Map<String, LinkCheckInfo> checkLinks(GKInstance refDBInst, List<GKInstance> instances, float proportionToCheck, int maxToCheck) throws IllegalArgumentException
	{
		if (proportionToCheck < 0.0)
		{
			throw new IllegalArgumentException("\"proportionToCheck\" cannot be negative.");
		}
		if (proportionToCheck > 1.0)
		{
			throw new IllegalArgumentException("\"proportionToCheck\" cannot be greater than 1.");
		}
		if (maxToCheck < 0)
		{
			throw new IllegalArgumentException("\"maxToCheck\" cannot be negative.");
		}

		Map<String, LinkCheckInfo> linkCheckResults = Collections.synchronizedMap( new HashMap<String, LinkCheckInfo>(instances.size()) );

		int numberOfInstancesToCheck ;
		if (instances.size() < maxToCheck)
		{
			numberOfInstancesToCheck = instances.size();
		}
		else
		{
			numberOfInstancesToCheck = Math.min( Math.min( (int)(instances.size() * proportionToCheck) , instances.size()), maxToCheck);
		}

		Collections.shuffle(instances);
		List<GKInstance> instancesToCheck = instances.subList(0, numberOfInstancesToCheck);

		logger.info("Checking links for {}; requested proportion of links to check: {}; max allows links to check: {}; Total # of possible links to check: {}; {}*{}: {}; *actual* number of links to check: {}",
				refDBInst, proportionToCheck, maxToCheck, instances.size(), proportionToCheck , instances.size(), (int)(instances.size() * proportionToCheck), instancesToCheck.size());

		String refDBID = refDBInst.getDBID().toString();
		instancesToCheck.parallelStream().forEach( inst -> {
			try
			{
				String identifierString = (String) inst.getAttributeValue(ReactomeJavaConstants.identifier);
				//get the reference DB from the database (if it's not in local cache)
				String accessURL = ((String)refDBInst.getAttributeValue(ReactomeJavaConstants.accessUrl));
				accessURL = LinkCheckManager.tweakIfZinc(refDBInst);
				String referenceDatabaseName = refDBInst.getDisplayName();

				LinkCheckManager.checkTheLink(linkCheckResults, refDBID, inst, identifierString, accessURL, referenceDatabaseName);
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
			}
			catch (InterruptedException r)
			{
				r.printStackTrace();
			}
			catch (ConnectException e)
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			catch (InvalidAttributeException e)
			{
				e.printStackTrace();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});
		return linkCheckResults;
	}

	/**
	 * Tweaks the Zinc URL to perform better. This is done by appending "?count=1&sort=no&distinct=no" to the URL string.
	 * In theory, this is supposed to reduce the amount of time it takes for Zinc to respond.
	 * @param refDBInst - The ReferenceDatabase to tweak.
	 * @return The updated URL IF it is for a Zinc URL. If not Zinc, the original accessURL will be returned.
	 * @throws Exception
	 * @throws InvalidAttributeException
	 */
	private static String tweakIfZinc(GKInstance refDBInst) throws InvalidAttributeException, Exception
	{
		String refDBName = refDBInst.getDisplayName();

		String accessURL = (String) refDBInst.getAttributeValue(ReactomeJavaConstants.accessUrl);
		String newURL = accessURL;
		// Zinc databases all start with "Zinc" such as "Zinc - Substances". But we'll normalize to lowercase.
		if (refDBName.toLowerCase().startsWith("zinc"))
		{
			newURL += "?count=1&sort=no&distinct=no";
		}
		return newURL;
	}

	/**
	 * Takes in a (possibly) heterogeneous list of instances with Identifier attributes and then gets links out of them and then checks them.
	 * The instances do NOT need to be associated with the same Reference Database.
	 * @param instances - A list of instances. ALL of them will be checked.
	 * @return A map. The key is the identifier, the value is a LinkCheckInfo object, {@link org.reactome.addlinks.linkchecking.LinkCheckInfo}
	 */
	public Map<String, LinkCheckInfo> checkLinks(List<GKInstance> instances)
	{
		Map<String, GKInstance> refDBCache = new HashMap<>();

		// This map stores results, keyed by DB_ID of the objects.
		Map<String, LinkCheckInfo> linkCheckResults = Collections.synchronizedMap( new HashMap<String, LinkCheckInfo>(instances.size()) );

		//for (GKInstance inst : instances)
		instances.parallelStream().forEach( inst ->
		{
			// checking a link is more than just a true/false - the status code needs
			// to be taken into account too. And we could track other things such as number of
			// retries, and time to get the response.
			// Maybe we should pass a call-back function to the link-checker
			// to be called when the link-checker has gotten a response.

			try
			{
				String identifierString = (String) inst.getAttributeValue("identifier");

				String refDBID =  ((GKInstance) inst.getAttributeValue("referenceDatabase")).getDBID().toString();
				GKInstance refDBInstance;
				if (!refDBCache.containsKey(refDBID))
				{
					refDBInstance = this.dbAdaptor.fetchInstance(Long.valueOf(refDBID));
					refDBCache.put(refDBID, refDBInstance);
				}
				else
				{
					refDBInstance = refDBCache.get(refDBID);
				}
				logger.debug(refDBCache.get(refDBID));
				//get the reference DB from the database (if it's not in local cache)
				String accessURL = ((String)refDBCache.get(refDBID).getAttributeValue("accessUrl"));
				accessURL = LinkCheckManager.tweakIfZinc(refDBInstance);
				String referenceDatabaseName = ((String)refDBCache.get(refDBID).getDisplayName());

				LinkCheckManager.checkTheLink(linkCheckResults, refDBID, inst, identifierString, accessURL, referenceDatabaseName);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new Error(e);
			}

		});
		return linkCheckResults;
	}

	private static void checkTheLink(Map<String, LinkCheckInfo> linkCheckResults, String refDBID, GKInstance inst, String identifierString, String accessURL, String referenceDatabaseName)
			throws URISyntaxException, IOException, Exception, InterruptedException
	{
		// Some ReferenceDatabases that are added by Curators do not have an Identifier Token (the string: "###ID###".)
		// Normally, we don't create references for these databases so we normally do not check links for these databases. BUT...
		// just in cas someone tries to run link-checking on such a database OR if somehow, a ReferenceDatabase accessURL loses it's token,
		// we will check for the token before proceeding, and issue a warning if no token was found.
		//
		// This should not be an exception because there are legitimate ReferenceDatabases that do not have this value, but still, it's
		// a good idea to warn the user about it. Most likely, this will happen if they try to link-check on something that they should not
		// be checking.
		if (!accessURL.contains(IDENTIFIER_TOKEN))
		{
			logger.warn("Access URL ({}) for ReferenceDatabase \"{}\" does not contain an ID token that can be replaced! Link checking cannot proceed!", accessURL, referenceDatabaseName);
		}
		else
		{
			URI uri = new URI(  accessURL.replace(IDENTIFIER_TOKEN, identifierString) );
			LinkChecker checker = new LinkChecker(uri, identifierString);
			LinkCheckInfo info = checker.checkLink();
			if (!(info.isKeywordFound() && info.getStatusCode() == 200))
			{
				LinkCheckManager.logger.warn("Link {} produced status code: {} ; keyword {} was not found.",uri.toString(), info.getStatusCode(), identifierString );
			}
			else
			{
				LinkCheckManager.logger.debug("Link {} produced status code: {} ; keyword {} was found.",uri.toString(), info.getStatusCode(), identifierString );
			}
			info.setIdentifier(identifierString);
			info.setReferenceDatabaseDBID(refDBID);
			info.setIdentifierDBID(inst.getDBID().toString());
			info.setReferenceDatabaseName(referenceDatabaseName);
			linkCheckResults.put(inst.getDBID().toString(), info);
			// Sleep for 2 seconds between requests so that the server we're talking to doesn't think we're trying to DOS them.
			Thread.sleep(Duration.ofSeconds(2).toMillis());
		}
	}
}
