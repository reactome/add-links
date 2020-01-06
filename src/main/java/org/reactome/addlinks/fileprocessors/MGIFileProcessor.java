package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MGIFileProcessor extends FileProcessor{

    public MGIFileProcessor(String processorName)
    {
        super(processorName);
    }

    public MGIFileProcessor()
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
                if (tabSplit.size() > 6) {
                  String mgiId = tabSplit.get(0).split(":")[1];
                  List<String> uniprotIds = Arrays.asList(tabSplit.get(6).split(" "));
                  for (String uniprotId : uniprotIds) {
                    if (mappings.get(uniprotId) != null) {
                        mappings.get(uniprotId).add(mgiId);
                    } else {
                        mappings.put(uniprotId, new ArrayList<>(Arrays.asList(mgiId)));
                    }
                  }

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
