# AddLinks
This is the AddLinks component of the Release system

## TOC

 - [Overview](./README.md#overview)
   - [High-level](./README.md#high-level)
   - [Detailed](./README.md#a-little-more-detail)
 - [Configuration](./README.md#configuration)
 - [Building & Running](./README.md#building--running)


## Overview

AddLinks is a program whose purpose is to create links to external resources from Reactome.

### High-level

At a high-level, AddLinks runs in four phases:

 1. [Data file retrieval](./README.md#data-file-retrieval)
 2. [Data file processing](./README.md#data-file-processing)
 3. [Reference creation](./README.md#reference-creation)
 4. [Link-checking](./README.md#link-checking)
 
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


 6. Execute Reference creators. Again, like for file retrievers and file processors, a list can be specified in the application-context.xml to define which reference creators will execute.
    It should be noted that the Data Retrieval phase and the Data Processing phase can be executed completely independently. You could run AddLinks and *only* run the file retrievers (empty lists for everything else) and then run only the file processors (empty lists for everything else). You cannot run the Reference Creators indepently - they operate on the in-memory output of the file processors. 

 7. After references are created, a report is run to show the current counts of external resources in the different databases. Additionally, a difference report will also be generated and saved to a file (named with a datestring, like this: "diffReport<DATETIME>.txt").
 
 8. Purging unused ReferenceDatabases
 
    ReferenceDatabase objects are created at the very begining of AddLinks. At that point in time, it is not yet known if all of those ReferenceDatabase objects will have references that make use of them. After all of the references have been created, any ReferenceDatabase objects that are unused (not external resources reference them) are removed from the database.

 9. Link-checking
 
    AddLinks will attempt to check the links from the references it created to ensure that they are all OK. The list of ReferenceDatabases to perform link-checking on can be configured in the application-context.xml file. Some databases should not have link-checking performed as they will not provide the correct response. Some websites seem to return a 403 error code if they are not accessed with a web browser. Some load their content via JavaScript so it is impossible to verify the links without actually executing the JavaScript.

## Configuration

AddLinks gets configuration values from Spring XML files. There are also some properties files that contain configuration settings.

### XML configuration
By default, the XML files that contain configuration are:

 - [application-context.xml](src/main/resources/application-context.xml) - This file contains the main configuration. It contains
   - A list of data retrievers. These must be in a list bean named "fileRetrieverFilter".
   - A list of data processors. These must be in a list bean named "fileProcessorFilter".
   - A list of reference creators. These must be in a list bean named "referenceCreatorFilter".
   - A list of reference database names that will be link-checked. These must be in a bean named "referenceDatabasesToLinkCheck".
   This file also contains database connector configuration, in a bean named "dbAdapter". This bean will get its values from a properties file.
  
 - [addlinks.properties](src/main/resources/addlinks.properties) - This file contains some property values, some of which will be used by the XML configuration.
   - filterFileRetrievers - This setting is used to determine if file retrievers will be filtered based on the fileRetrieverFilter bean in the XML file. Consider this deprecated!
   - executeAsPersonID - This is the ID of the Person entity that new data will be associated with.
   - numberOfUniprotDownloadThreads - Number of threads that will be created to download Uniprot data.
   - lazyLoadCache - determines if all of the object caches will be fully-loaded the first time *any* cache is referenced, or if caches will only be loaded the first time they are references (not all caches will be loaded - only the referenced cache will be loaded!)
   - proportionToLinkCheck - the proportion of new links to perform link-checking on, on a per-ReferenceDatabase basis. 0 means no new links will be checked. 1.0 means all new links will be checked. 0.5 means that half of the new links will be checked.
   - maxNumberLinksToCheck - the maximum number of new links to check. This overrides proportionToLinkCheck. For example, if 10 000 new links are created and proportionToLinkCheck == 0.5, that means that link-checking should check 5 000 links. If this is too many links to check in a reasonable amount of time (or if you don't want to send too many requests to someone's server) you can set maxNumberLinksToCheck to a more reasonable number (50, or 200, or something like that) and maxNumberLinksToCheck will act as as cap on the number of links that can be checked, on a per-ReferenceDatabase basis.
   
 - [db.properties](src/main/resources/db.properties) - This file contains database connection configuration information.
   - database.host - the name of the host machine which is hosting the database.
   - database.name - the name of the database to connect to.
   - database.user - the username to connect to the database.
   - database.password - the password for `database.user`.
   - database.port - the port to connect to.
   
 - [auth.properties](src/main/resources/auth.properties) - This file will contain usernames and passwords for other sites that you will connect to.
 
 - [logging.properties](src/main/resources/logging.properties) - This file contains basic settings to configure logging.
   - baseDir - This is the base directory where log files will be created. Log files will be grouped into subdirectories based on what type of logs they are. The subdirectories for logging will be `retrievers` for data retrievers, `file-processors` for data processors, and `refCreators` for reference creators. More generic log messages may go to `addlinks.log`. Logs will also be archived in subdirectries named with the date.
   
 - [log4j2.xml](src/main/resources/log4j2.xml) - This is the log4j2 configuration file. It is strongly recommended to not touch this file, unless you have a good understanding of log4j2 configuration.
 

## Building & Running

### Building AddLinks

Building AddLinks should be very simple. You should be able to perform these steps:

```bash
$ git clone https://github.com/reactome/AddLinks.git
$ cd AddLinks
$ mvn clean package -DskipTest=true
```
If you want to build and execute tests, make sure you have a Reactome database that AddLinks can connect to, and configure [src/test/resources/db.properties](src/test/resources/db.properties) with the correct values.

### Running AddLinks

Running AddLinks can be done like this:

```bash
java -cp "$(pwd)/resources" \
	-Dconfig.location=$(pwd)/resources/addlinks.properties \
	-Dlog4j.configurationFile=$(pwd)/resources/log4j2.xml \
	-jar AddLinks.jar file://$(pwd)/resources/application-context.xml
```

You will need to execute this command from a directory which has a subdirectory named "resources", such that "resources" contains all of the necessary configuration files described above, as well as the application properties file (defined with the `-Dconfig.location` VM setting) and the logging configuration files (defined with the `-Dlog4j.configuration` VM setting). The AddLinks application itself takes one argument: the path to the Spring context file (application-context.xml).


## Other notes

Some notes on other parts of the AddLinks system. Most of these notes describe exceptions to how the rest of the system is built.

### Different data retrieval
Most of the data retrievers are designed to download a single file, or submit queries to a webservice to get data. Some of them work a little bit differently:

#### ENSEMBL
Getting cross-references from ENSEMBL requires first getting doing a batch mapping from ENSP to ENST, then batch mapping ENST to ENSG.
Then, individual cross-reference lookups on ENSG to get other databases. The batch lookups require a specific species, as an input. So, getting data for ENSEMBL takes a few steps.

#### KEGG
To get data from KEGG, AddLinks must first get the UniProt-to-KEGG mappings. KEGG is then queried using these mapped values to get detailes for each of the KEGG identifiers. KEGG queries are species-specific.

#### UniProt
This is a pretty simple web-service call. The difference here with UniProt is that there will be a .tab file with the mappings from UniProt to some other database, and a .not file containing all of the identifiers that the UniProt web service could not map.

#### File processing
 - Most file processors operate on text files, usually tab or comma delimited. Some file processors operate on XML. In these cases, there is usually one or more XSL files that is used to transform the XML into a much simpler structure (usually a CSV or TSV). This is done for ENSEMBL, Orphanet, and HMDB.

 - Some file processors operate on file globs (file name patterns). These include file processors for ENSEMBL, KEGG, UniProt, and OMIM. Usually, this is done becuase there are multiple input files, often distinguished by the target database of the mapping, a species ID, or both.
 
#### Reference creation

Some of the code that creates references behaves differently than most of the rest.

#### Zinc Orthologs
The code that creates ZINC orthologs first performs a query to the ZINC website to see if the identifier has an content for a specific type (biogenic, fda approved, etc...).

#### OMIM
The OMIM Reference Creator is a UniProt-mapped reference creator, but it uses its own OMIM file processor to filter the UniProt list with a list from OMIM before creating references.

#### CTD, Monarch, BioGPS, dbSNP
The Reference Creators for these databases are all NCBI-based. When the ENSEMBL or UniProt reference creators create references with NCBI identifiers, these other reference creators are also automatically executed. Additionally, for CTD there is a separate file processor and reference creator which filter based on a CTD file.
