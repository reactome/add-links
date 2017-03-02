package org.reactome.addlinks;

import java.io.Serializable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public interface CustomLoggable
{
	default Logger createLogger(String logFileName, String oldAppenderName, String newAppenderName, String packageName, boolean append, Level level)
	{
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = context.getConfiguration();
		Layout<? extends Serializable> oldLayout = configuration.getAppender(oldAppenderName).getLayout();
		
		// create new appender/logger
		LoggerConfig loggerConfig = new LoggerConfig(packageName, level, false);
		FileAppender appender = FileAppender.createAppender(logFileName, Boolean.toString(append), "false",
															newAppenderName, "true", "true", "true", "8192", oldLayout, null, "false", "", configuration);
		appender.start();
		loggerConfig.addAppender(appender, level, null);
		configuration.addLogger(packageName, loggerConfig);

		context.updateLoggers();
		
		return context.getLogger(packageName);
	}
}
