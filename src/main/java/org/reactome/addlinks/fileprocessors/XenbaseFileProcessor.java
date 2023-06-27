package org.reactome.addlinks.fileprocessors;

import org.reactome.addlinks.EnsemblBioMartUtil;
import java.util.*;

public class XenbaseFileProcessor extends FileProcessor<List<String>>{

    public XenbaseFileProcessor(String processorName)
    {
        super(processorName);
    }

    public XenbaseFileProcessor()
    {
        super();
    }

    private static final int UNIPROT_IDENTIFIER_INDEX = 0;
    private static final int XENBASE_IDENTIFIER_INDEX = 3;

    /**
     * Build map of UniProt identifiers to Xenbase identifiers that is used to create Xenbase cross-references in database.
     * @return - Map<String, List<String>>
     */
    @Override
    public Map<String, List<String>> getIdMappingsFromFile()
    {
        Map<String, List<String>> mappings = new HashMap<>();

        for (String line : EnsemblBioMartUtil.getLinesFromFile(this.pathToFile, false)) {
            List<String> tabSplit = Arrays.asList(line.split("\t"));
            String xenbaseId = tabSplit.get(XENBASE_IDENTIFIER_INDEX);
            String uniprotId = tabSplit.get(UNIPROT_IDENTIFIER_INDEX);
            mappings.computeIfAbsent(uniprotId, k -> new ArrayList<>()).add(xenbaseId);
        }
        logger.info("{} keys in mapping.", mappings.size());
        return mappings;
    }
}