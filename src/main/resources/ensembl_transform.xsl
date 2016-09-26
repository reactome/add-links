<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="text" indent="no" encoding="utf-8"
		media-type="text/plain" />
	<xsl:strip-space elements="*" />

	<!-- parameter "db" determines what will be matched for @dbname in the xml file. -->
	<xsl:param name="db" />
	
	<!-- create a comma-separated line of the form: <Ensembl ID in Reactome>,<ID from other database> -->
	<xsl:template match="/ensemblResponses/ensemblResponse/opt/data[@dbname=$db]">
		<xsl:value-of select="../../@id" />
		<xsl:text>,</xsl:text>
		<xsl:value-of select="./@primary_id" />
		<xsl:text>&#10;</xsl:text>
	</xsl:template>
	
	<!-- Empty transform: linkage_types can be discarded. -->
	<xsl:template  match="linkage_types"/>
</xsl:stylesheet>