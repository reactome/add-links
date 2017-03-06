package org.reactome.addlinks;

import java.io.Serializable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public interface CustomLoggable
{
	default Logger createLogger(String logFileName, String oldAppenderName, String newAppenderName, boolean append, Level level)
	{
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = context.getConfiguration();
		
		Appender oldAppender = configuration.getAppender(oldAppenderName);
		
		Layout<? extends Serializable> oldLayout = oldAppender.getLayout();
		
		// create new appender/logger
		LoggerConfig loggerConfig = new LoggerConfig(logFileName, level, false);
		
		Appender appender ;
		// TODO: Find a better way to create *any* appender of *any* type and still copy over all the config. This is probably much easier said than done. :(
		if (oldAppender instanceof RollingRandomAccessFileAppender)
		{
			int bufferSize = ((RollingRandomAccessFileAppender)oldAppender).getBufferSize();
			TriggeringPolicy triggerPolicy = ((RollingRandomAccessFileAppender)oldAppender).getManager().getTriggeringPolicy();
			RolloverStrategy rollStrategy = ((RollingRandomAccessFileAppender)oldAppender).getManager().getRolloverStrategy();
			Filter filter = ((RollingRandomAccessFileAppender)oldAppender).getFilter();
			// Inject new log file name into filePattern so that file rolling will work properly 
			String pattern = ((RollingRandomAccessFileAppender)oldAppender).getFilePattern().replaceAll("/[^/]*-\\%d\\{MM-dd-yyyy\\}\\.\\%i\\.log\\.gz", "/"+logFileName+"-%d{MM-dd-yyyy}.%i.log.gz");
			appender = RollingRandomAccessFileAppender.createAppender("logs/" + logFileName + ".log", pattern, Boolean.toString(append), newAppenderName, "true", Integer.toString(bufferSize), triggerPolicy, rollStrategy, oldLayout, filter, "true", "false", "false", configuration);
		}
		else
		{
			appender = FileAppender.createAppender("logs/" + logFileName + ".log", Boolean.toString(append), "false", newAppenderName, "true", "true", "true", "8192", oldLayout, null, "false", "", configuration);
		}
		appender.start();
		loggerConfig.addAppender(appender, level, null);
		configuration.addLogger(logFileName, loggerConfig);
		context.updateLoggers();

		return context.getLogger(logFileName);
	}
	
	default Logger createLogger(String logFileName, String oldAppenderName, String newAppenderName, boolean append, Level level, Logger oldLogger, String loggerContainerClassTypeName)
	{
		if (oldLogger == null)
		{
			oldLogger = LogManager.getLogger();
		}
		
		if (logFileName == null || logFileName.trim().equals(""))
		{
			oldLogger.warn("No custom log file name was set, so this " + loggerContainerClassTypeName + " will not use its own log file.");
			return oldLogger;
		}
		else
		{
			oldLogger.debug("Now creating new logger: {}", logFileName);
			return this.createLogger(logFileName , oldAppenderName, logFileName, true, level);
		}
		
	}
}
