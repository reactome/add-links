package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBiomartUtil;

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
        super();
    }

    private static final int vgncIdentifierIndex = 1;
    private static final int uniprotIdentifiersIndex = 21;

    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();
        try {
            this.unzipFile(this.pathToFile, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Path inputFilePath = Paths.get(this.pathToFile.toAbsolutePath().toString().replace(".gz", ""));

        List<String> lines = new ArrayList<>();
        try {
            lines = EnsemblBiomartUtil.getLinesFromFile(inputFilePath, true);
        } catch (IOException e) {
            logger.error("Error reading file ({}): {}", inputFilePath.toString(), e.getMessage());
            e.printStackTrace();
        }

        for (String line : lines) {
            List<String> tabSplit = Arrays.asList(line.split("\t"));
            if (EnsemblBiomartUtil.necessaryColumnPresent(tabSplit, uniprotIdentifiersIndex)) {

                String vgncId = tabSplit.get(vgncIdentifierIndex).split(":")[1];
                List<String> uniprotIds = Arrays.asList(tabSplit.get(uniprotIdentifiersIndex).replaceAll("\"", "").split("\\|"));
                for (String uniprotId : uniprotIds) {
                    mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(vgncId);
                }
            }
        }

        return mappings;
    }
}