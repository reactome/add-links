package org.reactome.addlinks.linkchecking;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;

/**
 * A cache of links that will be checked.
 * @author sshorser
 *
 */
public class LinksToCheckCache
{
	// cache will be a map: Key is reference DB, value is a list of GKInstances which are Identifiers.
	private static Map<GKInstance, Set<GKInstance>> linksCache = Collections.synchronizedMap(new HashMap<GKInstance, Set<GKInstance>>());
	
	public synchronized static void addLinkToCache(GKInstance refDB, GKInstance identifier)
	{
		if (LinksToCheckCache.linksCache.containsKey(refDB))
		{
			if (! LinksToCheckCache.linksCache.get(refDB).contains(identifier))
			{
				LinksToCheckCache.linksCache.get(refDB).add(identifier);
			}
		}
		else
		{
			LinksToCheckCache.linksCache.put(refDB, Collections.synchronizedSet(new HashSet<GKInstance>()));
			LinksToCheckCache.linksCache.get(refDB).add(identifier);
		}
	}
	
	public static Map<GKInstance, Set<GKInstance>> getCache()
	{
		return LinksToCheckCache.linksCache;
	}
}
