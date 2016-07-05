package org.reactome.addlinks.db;

import java.util.List;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.core.factory.DatabaseObjectFactory;
import org.reactome.core.model.DatabaseObject;

public class DatabaseRetriever {

	private String host;
	private String database;
	private String username;
	private String password;

	/*Big important query:
	 * 
select _displayName, _class, DatabaseObject.db_id as object_db_id, ReferenceEntity.identifier, ReferenceEntity.referenceDatabase as refent_refdb, ReferenceSequence.species -- , DatabaseIdentifier.identifier as ref_identifier -- , DatabaseIdentifier.referenceDatabase db_id_refdb
from DatabaseObject
inner join ReferenceGeneProduct on ReferenceGeneProduct.db_id = DatabaseObject.db_id 
inner join ReferenceEntity on ReferenceEntity.db_id = ReferenceGeneProduct.db_id
inner join ReferenceSequence on ReferenceSequence.db_id = ReferenceEntity.db_id
-- left join DatabaseIdentifier on DatabaseIdentifier.identifier = ReferenceEntity.identifier
where _class  = 'ReferenceGeneProduct'; 
	 */
	
	public List<Long> getIDs(String className) throws Exception
	{
		MySQLAdaptor adapter = new MySQLAdaptor(host, database, username, password);
//		
//		@SuppressWarnings("unchecked")
//		List<Instance> instances =  (List<Instance>) adapter.fetchInstancesByClass(className);
//		List<DatabaseObject> dbObjects = instances.stream().map(m -> ((DatabaseObject) DatabaseObjectFactory.getDatabaseObject((GKInstance)m))).collect(Collectors.toList());
//		return dbObjects.stream().map(m-> m.getDbId()).collect(Collectors.toList());
		return null;
	}
}
