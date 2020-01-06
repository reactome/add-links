package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class RGDFileProcessor extends FileProcessor{

    public RGDFileProcessor(String processorName)
    {
        super(processorName);
    }

    public RGDFileProcessor()
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
                if (Character.isDigit(line.charAt(0))) {
                    List<String> tabSplit = Arrays.asList(line.split("\t"));
                    String rgdId = tabSplit.get(0);
                    List<String> uniprotIds = Arrays.asList(tabSplit.get(21).split(";"));
                    if (!uniprotIds.isEmpty()) {
                        for (String uniprotId : uniprotIds) {
                            if (mappings.get(uniprotId) != null) {
                                mappings.get(uniprotId).add(rgdId);
                            } else {
                                mappings.put(uniprotId, new ArrayList<>(Arrays.asList(rgdId)));
                            }
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
