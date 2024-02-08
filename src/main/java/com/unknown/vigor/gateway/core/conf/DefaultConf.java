package com.unknown.vigor.gateway.core.conf;

import com.unknown.vigor.gateway.common.constant.GeneralConstant;
import com.unknown.vigor.gateway.common.event.LogIdMetadata;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class DefaultConf extends AbstractProxyConf {

    private final static Logger LOGGER = LoggerFactory.getLogger(DefaultConf.class);
    private String systemConfigPath;

    @Override
    public void start() throws Exception {
        configPath = System.getenv(GeneralConstant.PROJECT_HOME);

        if (StringUtils.isEmpty(configPath)) {
            LOGGER.warn("systemEnv can't find local config file path, use classpath instead");
            URL url = DefaultConf.class.getClassLoader().getResource("");
            configPath = url.getPath();
        }

        systemConfigPath = String.format("%s/%s", configPath, GeneralConstant.PROJECT_CONFIG_FILE);

        LOGGER.info("localConfigPath: {}", systemConfigPath);
        super.start();
    }

    @Override
    public Properties updateSystemConfig() throws Exception {
        Properties properties = new Properties();
        properties.load(new FileInputStream(systemConfigPath));
        return properties;
    }

    @Override
    public Map<String, LogIdMetadata> updateLogIdMetas() {
        return localLoadLogIdMetas();
    }
}
