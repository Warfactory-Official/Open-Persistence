package com.norwood.openpersistence;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

public class Logger {

    private static org.apache.logging.log4j.Logger log = LogManager.getLogger(OpenpersistenceLegacyForge.MODID);

    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }

    public static void warning(String msg) {
        log.log(Level.WARN, msg);
    }

    public static void error(String msg) {
        log.log(Level.ERROR, msg);
    }

    public static void setLogger(org.apache.logging.log4j.Logger logger) {
        log = logger;
    }
}
