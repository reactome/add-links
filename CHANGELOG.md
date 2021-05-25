# CHANGELOG

## 1.6.0
 - Add links for:
   - GlyGen
   - Guide to Pharmacology
   - Pharos
 - Switch to new version of release-common-lib (1.2.2)
 - Minor fixes for KEGG

## 1.5.1
 - Add SARS files for ComplexPortal
 - Remove unused DOCKBlaster code
 - Add reference creation process for PharmacoDB

## 1.5.0
 - TargetPathogen file processor updated to match new format of input file
 - Updated FileRetriever to handle very large files (such as from COSMIC)
 - Updated SimpleReferenceCreator to handle more complex mappings
 - Updated URLs for files: Flybase, COSMIC, OpenTargets
 - Updated version of JUnit

## 1.4.0
 - New: Moved OtherIdentifiers into AddLinks
 - Fixes:
  - improved link-checking
  - changes for Jenkins
  - Fixed: https://github.com/reactome/add-links/issues/130
  - NCBI identifiers added to OtherIdentifiers slots
  - Updated Ensembl Canis Familiaris reference database name to Ensembl Canis lupus familiaris
  - Remove BRENDA-related code
  - Fixed memory issues

## 1.3.0
 - New: Changes for Jenkins

## 1.2.0
 - New: Create references for TargetPathogen
 - Major Fixes:
  - Fixed https://github.com/reactome/add-links/issues/118 - Avoid extra dummy ReferenceDatabase objects
  - Fixed https://github.com/reactome/add-links/issues/119 - Link checker failing to report "404" responses
  - Fixed https://github.com/reactome/add-links/issues/120 - dbSNP only supports Human identifiers
  - Fixed https://github.com/reactome/add-links/issues/117 - log "403" responses

## 1.1.4
 - Use a new OpenTargets file with a new format.
 - Fixed some ENSEMBL endpoints.
 - Some fixes for better MIR lookups for identifiers.org

## 1.1.3
Highlights:
 - Use identifiers.org to verify/update some of the URLs - look for "resourceIdentifier" in reference-databases.xml
 - Retry download of Uniprot mapping files on failure.
 - Improve performance for Zinc link-checking.
 - Associate new ReferenceDatabase objects with an InstanceEdit.
 - Include IntEnz as a resource for identifiers.
 - Make use of release-common-lib (see: https://github.com/reactome/data-release-pipeline/releases/tag/release-common-lib-1.0.2) and remove redundant code.
 - Link-checking summay report.
 - Fix the Duplicate Identifier query.
 - Refactoring and cleanup.
 - Stop creating references for BRENDA and DOCKBlaster.
 

## 1.1.2
 - New downloader for COSMIC (for new download process, introduced by COSMIC)
 - Fixed: issues with duplicated ENSEMBL ReferenceDatabase objects
 - Improved CTD file parsing
 - Some code cleanup
 - exclude HGNC and OpenTargets from link validation (they either load content via JavaScript, or don't refer to the identifier in the body of the page)

## 1.1.1
Hotfix: Fixes for ENSEMBL species list.

## 1.1.0
Highlights:
 - New resource: OpenTargets
 - ComplexPortal: 
   - Renamed IntActComplexPortal to be simply "ComplexPortal"
   - Use new input file
 - Fix some issues with KEGG identifiers being associated with the wrong species code, or including a species code in the identifier string
 - Fix issues related to duplicated identifiers
 - New report for duplicated identifiers
 - Reports now go in their own directory

## 1.0.4
 - BRENDA Reference Database object names will simply be "BRENDA", but the _displayName will include the species name for the species-specific ReferenceDatabase instance.

## 1.0.3
 - Integrated a fix for BRENDA species-specific databases.

## 1.0.2
 - Changes for species-specific BRENDA databases.
 - Fixed bugs for species-specific ENSEMBL databases.
 - Link checker should now log to its own files.
 - ...and updated Flybase file URL, of course ;)

## 1.0.1b
 - Exactly the same as 1.0.1, but the Flybase filename has been updated.

## 1.0.1
 - Include UCSC
 - Tweaks for BRENDA - it looks like we were sending too many requests at once before. Send fewer requests.
 - Include ability to set Passive mode for FTP
 - other smaller fixes.

## 1.0.0
Initial release.
