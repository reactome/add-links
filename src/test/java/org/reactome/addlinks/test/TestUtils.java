package org.reactome.addlinks.test;

import java.util.ArrayList;
import java.util.List;

import org.gk.model.ReactomeJavaConstants;
import org.reactome.addlinks.db.ReferenceObjectCache;

public final class TestUtils
{
	public static List<String> getIdentifiersList(String refDbId, String species, String className, ReferenceObjectCache objectCache)
	{
		// Need a list of identifiers.
		//String refDBID = objectCache.getRefDbNamesToIds().get(refDbId).get(0);
		String speciesDBID = objectCache.getSpeciesNamesToIds().get(species).get(0);
		System.out.println(refDbId + " " + refDbId + " ; " + species + " " + speciesDBID);
		List<String> identifiersList = new ArrayList<String>();
		objectCache.getByRefDbAndSpecies(refDbId, speciesDBID, className).stream().forEach(instance -> {
			try
			{
				identifiersList.add( (String)instance.getAttributeValue(ReactomeJavaConstants.identifier)  );
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		//String identifiers = identifiersList.toString();
		return identifiersList;
	}
}
