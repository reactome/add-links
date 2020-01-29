package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBiomartUtil;

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
        super();
    }

    private static final int zfinIdentifierIndex = 0;
    private static final int uniprotIdentifierIndex = 2;

    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        Path inputFilePath = Paths.get(this.pathToFile.toAbsolutePath().toString().replace(".gz", ""));
        List<String> lines = new ArrayList<>();
        try {
            lines = EnsemblBiomartUtil.getLinesFromFile(inputFilePath, true);
        } catch (IOException e) {
            logger.error("Error reading file ({}): {}", inputFilePath.toString(), e.getMessage());
            e.printStackTrace();
        }

        for (String line :lines) {
            List<String> tabSplit = Arrays.asList(line.split("\t"));
            String zfinId = tabSplit.get(zfinIdentifierIndex);
            String uniprotId = tabSplit.get(uniprotIdentifierIndex);
            mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(zfinId);
        }
        return mappings;
    }
}
