<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="text" indent="no" encoding="utf-8" media-type="text/plain" />
	<xsl:strip-space elements="*" />
	<xsl:template match="/opt/data/species">
		<xsl:value-of select="@name"/>
		<xsl:text> : </xsl:text>
		<!-- Everything after the @name attribute will be considered an "alias", even the common_name -->
		<xsl:value-of select="./@common_name"/>
		<xsl:text> , </xsl:text>
		<xsl:for-each select="./aliases">
			<xsl:value-of select="./text()"/>
			<xsl:text> , </xsl:text>
		</xsl:for-each>
		<xsl:text>&#10;</xsl:text>
	</xsl:template>
</xsl:stylesheet>