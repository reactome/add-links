package org.reactome.addlinks.test.referencecreators;

import org.reactome.addlinks.dataretrieval.FileRetriever;
import org.reactome.addlinks.fileprocessors.DOCKBlasterFileProcessor;
import org.reactome.addlinks.referencecreators.DOCKBlasterReferenceCreator;
import org.springframework.beans.factory.annotation.Autowired;

public class TestDOCKBlasterReferenceCreator
{
	// DOCKBlaster returns a Uniprot-to-PDB mapping.
	
	@Autowired
	DOCKBlasterReferenceCreator pdbRefCreator;
	
	@Autowired
	FileRetriever UniProtToPDBRetriever;
	
	@Autowired
	DOCKBlasterFileProcessor DOCKBlasterFileProcessor;
}
