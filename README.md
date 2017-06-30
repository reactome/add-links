# AddLinks
This is the AddLinks component of the Release system


## Overview

AddLinks is a program whose purpose is to create links to external resources from Reactome.

### High-level

At a high-level, AddLinks runs in four phases:

 1. [Data file retrieval](./README.md#3)
 2. [Data file processing](./README.md#4)
 3. [Reference creation](./README.md#5)
 4. [Link-checking](./README.md#6)
 
#### Data file retrieval

The phase will retrieve data that will be used to create links to external resources.

Typically, the data files are mapping files that map an identifier from one external resource to a different identifier in a different external resource.

Some file retrievers download a text file from a URL. Some will download from an FTP server. Some will make calls to a webservice and then store the results in a file on disc.

You can see a list of file retrievers [here](src/main/resources/basic-file-retrievers.xml).

Some data retrievers have slightly different configurations and are grouped together in separate files. Ensembl data retrievers are [here](src/main/resources/ensembl-file-retrievers.xml) and uniprot file retrievers are configerd [here](src/main/resources/uniprot-file-retrievers.xml).

#### Data file processing

Many of the files that are downloaded need some processing to prepare the data in such a way that it can easily be used by AddLinks. File procssing may be simple, such as extracting two columns from a TSV file. Other file processors are more complex and may use XSL transformations to turn an XML document into a format that is more usable.

You can see a list of file processors [here](src/main/resources/file-processors.xml).

#### Reference Creation

The reference creators will use the data that has been prepared in [Data file processing](./README.md#4) to create new references in the Reactome database.

The list of reference creators can be seen [here](/src/main/reference-creators.xml).

#### Link-checking

After the references have been created, AddLinks will check links to see if they are OK.

The list of reference databases to check can be seen [here](src/main/resources/application-context.xml). Look for the Spring bean whose name is "referenceDatabasesToLinkCheck".

### A little more detail...

The more detailed process of how AddLinks runs looks like this:

 1. Populate caches.

    AddLinks will build a cache of data from the main database that it is connected to. This is done to make data lookups faster.

 2. Create reference databases.

    Some of the reference databases are only populated by the AddLinks process, so the relevant ReferenceDatabase objects need to be created before anything else can be done. A  list of the reference databases that AddLinks will create can be found [here](src/main/resources/reference-databases.xml). Please be aware that for some databases, species-specific ReferenceDatabase objects will be created. For example, this applies to KEGG and ENSEMBL. See: [src/main/java/org/reactome/addlinks/kegg/KEGGReferenceDatabaseGenerator.java](src/main/java/org/reactome/addlinks/kegg/KEGGReferenceDatabaseGenerator.java) and [src/main/java/org/reactome/addlinks/ensembl/EnsemblReferenceDatabaseGenerator.java](src/main/java/org/reactome/addlinks/ensembl/EnsemblReferenceDatabaseGenerator.java).

 3. Run "before" report

    A query will be executed to produce a report that shows how many external resources of which types exist for which databases.

 4. Execute data retrievers.

    The data retrievers will be executed. The list of data retrievers to execute comes from the filter list in the application-contect.xml file, and must be specified in the bean named "fileRetrieverFilter".

In this example below, only the file retrievers named "HGNC", "OrthologsFromZinc", "OrthologsCSVFromZinc" will be executed. No other retrievers will be executed.
    It is in this way that you can control AddLinks to run as many or as few of the defined file retrievers.

```xml
<util:list id="fileRetrieverFilter" value-type="java.lang.String">
		<value>HGNC</value>
		<value>OrthologsFromZinc</value>
		<value>OrthologsCSVFromZinc</value>
</util:list>
```

 5. Execute file processors
    This step can also be filtered. The filtering looks like this:
    
```xml
	<util:list id="fileProcessorFilter" value-type="java.lang.String">
		<value>HGNCProcessor</value>
		<value>zincOrthologFileProcessor</value>
		<value>FlyBaseFileProcessor</value>
	</util:list>
```

    Like the data retriever filter, this list will explicitly specify which file processors will be executed.
    If you are interested in 