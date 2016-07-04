package org.reactome.addlinks.dataretrieval;

import java.util.List;
import java.util.stream.Collectors;

//import org.gk.model.GKInstance;
//import org.gk.model.Instance;
//import org.gk.persistence.MySQLAdaptor;
//import org.reactome.core.factory.DatabaseObjectFactory;
//import org.reactome.core.model.DatabaseObject;

public class DatabaseRetriever {

	private String host;
	private String database;
	private String username;
	private String password;

	public List<Long> getIDs(String className) throws Exception
	{
//		MySQLAdaptor adapter = new MySQLAdaptor(host, database, username, password);
//		
//		@SuppressWarnings("unchecked")
//		List<Instance> instances =  (List<Instance>) adapter.fetchInstancesByClass(className);
//		List<DatabaseObject> dbObjects = instances.stream().map(m -> ((DatabaseObject) DatabaseObjectFactory.getDatabaseObject((GKInstance)m))).collect(Collectors.toList());
//		return dbObjects.stream().map(m-> m.getDbId()).collect(Collectors.toList());
		return null;
	}
}
