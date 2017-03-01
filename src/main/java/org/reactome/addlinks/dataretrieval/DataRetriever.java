package org.reactome.addlinks.dataretrieval;

import java.io.Serializable;
import java.net.URI;
import java.time.Duration;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.Logger;


public interface DataRetriever {

	public void fetchData() throws Exception;
	public void setFetchDestination(String destination);
	public void setDataURL(URI uri);
	public void setMaxAge(Duration age);
	public void setRetrieverName(String retrieverName);
	
	default Logger createLogger(String logFileName, String oldAppenderName, String newAppenderName, String packageName, boolean append, Level level)
	{
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = context.getConfiguration();
		Layout<? extends Serializable> oldLayout = configuration.getAppender(oldAppenderName).getLayout();
		
		// delete old appender/logger
		// configuration.getAppender(appender_name).stop();
		// configuration.removeLogger(package_name);

		// create new appender/logger
		LoggerConfig loggerConfig = new LoggerConfig(packageName, level, false);
		FileAppender appender = FileAppender.createAppender(logFileName.replace(".log","-retriever.log"), Boolean.toString(append), "false",
															newAppenderName, "true", "true", "true", "8192", oldLayout, null, "false", "", configuration);
		appender.start();
		loggerConfig.addAppender(appender, level, null);
		configuration.addLogger(packageName, loggerConfig);

		context.updateLoggers();
		
		return context.getLogger(packageName);
	}
}
