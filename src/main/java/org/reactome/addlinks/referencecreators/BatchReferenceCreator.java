/**
 * 
 */
package org.reactome.addlinks.referencecreators;

import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;

/**
 * Basic interface for classes for objects that will create many references in a batch. 
 * 
 * @author sshorser
 *
 */
public interface BatchReferenceCreator<T>
{
	public void createIdentifiers(long personID, Map<String, T> mapping, List<GKInstance> sourceReferences) throws Exception;

	public String getSourceRefDB();

	public String getClassReferringToRefName();
}
