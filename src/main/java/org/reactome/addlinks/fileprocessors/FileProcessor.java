package org.reactome.addlinks.fileprocessors;

import java.nio.file.Path;
import java.util.Map;

public interface FileProcessor {

	public void setPath(Path p);
	public Map<String,String> getIdMappingsFromFile();
	
}
