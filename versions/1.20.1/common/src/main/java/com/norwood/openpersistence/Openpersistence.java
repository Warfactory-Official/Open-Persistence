package com.norwood.openpersistence;

import com.norwood.openpersistence.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Openpersistence {
    public static final String MOD_ID = "openpersistence";
    public static final String MOD_NAME = "Open Persistence";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static void init() {
        LOGGER.info("{} initializing on {}", MOD_NAME, Services.PLATFORM.getPlatformName());
    }
}
