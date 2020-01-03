package org.reactome.addlinks.fileprocessors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class VGNCFileProcessor extends FileProcessor{

    public VGNCFileProcessor(String processorName)
    {
        super(processorName);
    }

    public VGNCFileProcessor()
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
            this.unzipFile(this.pathToFile, true);

            for (String line : Files.readAllLines(inputFile).stream().skip(1).collect(Collectors.toList())) {
                List<String> tabSplit = Arrays.asList(line.split("\t"));
                if (tabSplit.size() > 21) {
                    String vgncId = tabSplit.get(1).split(":")[1];
                    String ensemblId = tabSplit.get(20);
                    List<String> uniprotIds = Arrays.asList(tabSplit.get(21).split("\\|"));

                    for (String uniprotId : uniprotIds) {
                        if (mappings.get(uniprotId) != null) {
                            mappings.get(uniprotId).add(vgncId);
                        } else {
                            mappings.put(uniprotId, new ArrayList<>(Arrays.asList(vgncId)));
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
        catch (Exception e) // potentially thrown by this.unzipFile
        {
            logger.error("Error accessing/unzipping file({}): {}", this.pathToFile, e.getMessage());
            e.printStackTrace();
        }

        return mappings;
    }
}
