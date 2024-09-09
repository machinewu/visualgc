package com.intellij.openapi.diagnostic;

import java.util.logging.Level;

public class Logger {
    private static final boolean debugEnableFlag = true;
    private static java.util.logging.Logger singleton = null;
    private static Logger singletonHelper = null;

    private Logger(String className) {
        singleton = java.util.logging.Logger.getLogger(className);
    }

    protected String getLoggerName() {
        return singleton.getName();
    }

    public static synchronized Logger getInstance(Class<?> clazz) {
        if (singletonHelper == null || !clazz.getName().equals(singletonHelper.getLoggerName())) {
            singletonHelper = new Logger(clazz.getName());
        }
        return singletonHelper;
    }

    public static synchronized Logger getInstance(String className) {
        if (singletonHelper == null ||
                (className != null && !className.equals(singletonHelper.getLoggerName()))
                || (className == null && singletonHelper.getLoggerName() != null)) {
            singletonHelper = new Logger(className);
        }
        return singletonHelper;
    }

    public void debug(String msg) {
        if (debugEnableFlag) {
            singleton.log(Level.FINE, msg);
        }
    }

    public void info(String msg) {
        singleton.log(Level.INFO, msg);
    }

    public void warn(String msg) {
        singleton.log(Level.WARNING, msg);
    }

    public void error(String msg) {
        singleton.log(Level.SEVERE, msg);
    }

    public void debug(String msg, Throwable thrown) {
        singleton.log(Level.FINE, msg, thrown);
    }

    public void info(String msg, Throwable thrown) {
        singleton.log(Level.INFO, msg, thrown);
    }

    public void warn(String msg, Throwable thrown) {
        singleton.log(Level.WARNING, msg, thrown);
    }

    public void error(String msg, Throwable thrown) {
        singleton.log(Level.SEVERE, msg, thrown);
    }

    public boolean isDebugEnabled() {
        return debugEnableFlag;
    }
}