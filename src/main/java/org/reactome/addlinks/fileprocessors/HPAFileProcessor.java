package org.reactome.addlinks.fileprocessors;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class HPAFileProcessor extends FileProcessor<List<String>>
{

    public HPAFileProcessor(String processorName)
    {
        super(processorName);
    }

    public HPAFileProcessor()
    {
        super(null);
    }

    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        try {
            String dirToHPAFiles = this.unzipFile(this.pathToFile);
            String inputFilepath = dirToHPAFiles + "/" + this.pathToFile.getFileName().toString().replace(".zip", "");

            int count = 0;
            for (String line : Files.readAllLines(Paths.get(inputFilepath)).stream().skip(1).collect((Collectors.toList()))) {
                count++;
                List<String> tabSplit = Arrays.asList(line.split("\t"));

                String geneName = tabSplit.get(0);
                String ensemblId = tabSplit.get(2);
                String uniprotId = tabSplit.get(4);
                String hpaUrlId = String.join("-", ensemblId, geneName);

                if (!geneName.isEmpty() && !ensemblId.isEmpty() && !uniprotId.isEmpty()) {
                    if (mappings.get(uniprotId) != null) {
                        mappings.get(uniprotId).add(hpaUrlId);
                    } else {
                        mappings.put(uniprotId, new ArrayList<>(Arrays.asList(hpaUrlId)));

                    }
                }
            }
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        return mappings;
    }
}