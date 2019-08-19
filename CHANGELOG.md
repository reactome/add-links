# CHANGELOG

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
