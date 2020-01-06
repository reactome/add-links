package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class XenbaseFileProcessor extends FileProcessor{

    public XenbaseFileProcessor(String processorName)
    {
        super(processorName);
    }

    public XenbaseFileProcessor()
    {
        super(null);
    }

    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        Path inputFile = Paths.get(this.pathToFile.toAbsolutePath().toString().replace(".gz", ""));
        try
        {
            for (String line : Files.readAllLines(inputFile).stream().collect(Collectors.toList())) {
                List<String> tabSplit = Arrays.asList(line.split("\t"));
                String xenbaseId = tabSplit.get(3);
                String uniprotId = tabSplit.get(0);
                if (mappings.get(uniprotId) != null) {
                    mappings.get(uniprotId).add(xenbaseId);
                } else {
                    mappings.put(uniprotId, new ArrayList<>(Arrays.asList(xenbaseId)));
                }
            }
        }
        catch (IOException e) // potentially thrown by Files.readAllLines
        {
            logger.error("Error reading file ({}): {}", inputFile.toString(), e.getMessage());
            e.printStackTrace();
        }
        return mappings;
    }
}
