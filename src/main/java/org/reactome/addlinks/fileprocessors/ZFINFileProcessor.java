package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ZFINFileProcessor extends FileProcessor{

    public ZFINFileProcessor(String processorName)
    {
        super(processorName);
    }

    public ZFINFileProcessor()
    {
        super(null);
    }

    //TODO: Looks like getting it to use BasicFileProcessor for all except Biomart is possible.

    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        Path inputFile = Paths.get(this.pathToFile.toAbsolutePath().toString().replace(".gz", ""));
        try
        {
            for (String line : Files.readAllLines(inputFile).stream().collect(Collectors.toList())) {
                List<String> tabSplit = Arrays.asList(line.split("\t"));
                String zfinId = tabSplit.get(0);
                String uniprotId = tabSplit.get(2);
                if (mappings.get(uniprotId) != null) {
                    mappings.get(uniprotId).add(zfinId);
                } else {
                    mappings.put(uniprotId, new ArrayList<>(Arrays.asList(zfinId)));
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
