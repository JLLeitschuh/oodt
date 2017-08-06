/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.oodt.config.distributed;

import org.apache.commons.io.FileUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.oodt.config.Component;
import org.apache.oodt.config.ConfigurationManager;
import org.apache.oodt.config.Constants;
import org.apache.oodt.config.Constants.Properties;
import org.apache.oodt.config.distributed.utils.ConfigUtils;
import org.apache.oodt.config.distributed.utils.CuratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.apache.oodt.config.Constants.Properties.ZK_CONNECT_STRING;
import static org.apache.oodt.config.Constants.Properties.ZK_PROPERTIES_FILE;
import static org.apache.oodt.config.distributed.utils.ConfigUtils.getOODTProjectName;

/**
 * Distributed configuration manager implementation. This class make use of a {@link CuratorFramework} instance to
 * connect to zookeeper.
 *
 * @author Imesha Sudasingha.
 */
public class DistributedConfigurationManager extends ConfigurationManager {

    private static final Logger logger = LoggerFactory.getLogger(DistributedConfigurationManager.class);

    /** Connection string required to connect to zookeeper */
    private String connectString;
    private CuratorFramework client;
    private ZNodePaths zNodePaths;

    private List<String> savedFiles = new ArrayList<>();

    public DistributedConfigurationManager(Component component) {
        super(component, getOODTProjectName());
        this.zNodePaths = new ZNodePaths(this.project, this.component.getName());

        if (System.getProperty(ZK_PROPERTIES_FILE) == null && System.getProperty(Constants.Properties.ZK_CONNECT_STRING) == null) {
            throw new IllegalArgumentException("Zookeeper requires system properties " + ZK_PROPERTIES_FILE + " or " + ZK_CONNECT_STRING + " to be set");
        }

        if (System.getProperty(ZK_PROPERTIES_FILE) != null) {
            try {
                CuratorUtils.loadZookeeperProperties();
            } catch (IOException e) {
                logger.error("Error occurred when loading properties from properties file");
            }
        }

        if (System.getProperty(Constants.Properties.ZK_CONNECT_STRING) == null) {
            throw new IllegalArgumentException("Zookeeper requires a proper connect string to connect to zookeeper ensemble");
        }

        connectString = System.getProperty(Constants.Properties.ZK_CONNECT_STRING);
        logger.info("Using zookeeper connect string : {}", connectString);
        startZookeeper();
    }

    /**
     * Creates a {@link CuratorFramework} instance and start it. This method will wait a maximum amount of {@link
     * Properties#ZK_STARTUP_TIMEOUT} milli-seconds until the client connects to the zookeeper ensemble.
     */
    private void startZookeeper() {
        client = CuratorUtils.newCuratorFrameworkClient(connectString, logger);
        client.start();
        logger.info("Curator framework start operation invoked");
        int startupTimeOutMs = Integer.parseInt(System.getProperty(Properties.ZK_STARTUP_TIMEOUT, "30000"));
        try {
            logger.info("Waiting to connect to zookeeper, startupTimeout : {}", startupTimeOutMs);
            client.blockUntilConnected(startupTimeOutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            logger.error("Interrupted while waiting to connect zookeeper (connectString : {}) : {}", ex, connectString);
        }

        if (!client.getZookeeperClient().isConnected()) {
            throw new IllegalStateException("Could not connect to ZooKeeper : " + connectString);
        }

        logger.info("CuratorFramework client started successfully");
    }

    @Override
    public void loadConfiguration() throws Exception {
        logger.debug("Loading properties for : {}", component);
        loadProperties();
        logger.info("Properties loaded for : {}", component);

        logger.debug("Saving configuration files for : {}", component);
        saveConfigFiles();
        logger.info("Configuration files saved for : {}", component);
    }

    /**
     * This method will fetch <pre>.properties</pre> files stored in zookeeper and load the properties in those files
     * to {@link System#props}.
     *
     * @throws Exception Zookeeper exceptions
     */
    private void loadProperties() throws Exception {
        String propertiesZNodePath = zNodePaths.getPropertiesZNodePath();
        List<String> propertiesFilesZNodePaths = CuratorUtils.getLeafZNodePaths(client, propertiesZNodePath);

        if (propertiesFilesZNodePaths.contains(propertiesZNodePath)) {
            propertiesFilesZNodePaths.remove(propertiesZNodePath);
        }

        for (String propertiesFileZNodePath : propertiesFilesZNodePaths) {
            logger.debug("Loading properties from ZNode at : {}", propertiesFileZNodePath);
            byte[] bytes = client.getData().forPath(propertiesFileZNodePath);
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                System.getProperties().load(in);
            }
            logger.info("Properties loaded from ZNode at : {}", propertiesFileZNodePath);

            String localFilePath = zNodePaths.getLocalPropertiesFilePath(propertiesFileZNodePath);
            saveFile(localFilePath, bytes);
        }
    }

    /**
     * Fetch and save all the configuration files from zookeeper. Local directories are created accordingly.
     * For example, if there is a ZNode under <pre>/components/{component}/configuration/etc/mime-types.xml</pre>, it
     * will be fetched and stored in the local path <pre>etc/mime-types.xml</pre>
     *
     * @throws Exception IOException or Zookeeper exception
     */
    private void saveConfigFiles() throws Exception {
        String configParentZNodePath = zNodePaths.getConfigurationZNodePath();
        List<String> configFilesZNodePaths = CuratorUtils.getLeafZNodePaths(client, configParentZNodePath);

        if (configFilesZNodePaths.contains(configParentZNodePath)) {
            configFilesZNodePaths.remove(configParentZNodePath);
        }

        for (String configFileZNodePath : configFilesZNodePaths) {
            logger.debug("Fetching configuration file from ZNode at : {}", configFileZNodePath);
            byte[] bytes = client.getData().forPath(configFileZNodePath);

            String localFilePath = zNodePaths.getLocalConfigFilePath(configFileZNodePath);
            saveFile(localFilePath, bytes);
        }
    }

    private void saveFile(String path, byte[] data) throws IOException {
        String localFilePath = ConfigUtils.fixForComponentHome(component, path);
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            logger.warn("Deleting already existing file at {} before writing new content", localFilePath);
            localFile.delete();
        }

        logger.debug("Storing configuration in file: {}", localFilePath);
        FileUtils.writeByteArrayToFile(localFile, data);
        logger.info("File from ZNode at {} saved to {}", path, localFilePath);
        savedFiles.add(localFilePath);
    }

    /** {@inheritDoc} */
    @Override
    public void clearConfiguration() {
        for (String path : savedFiles) {
            logger.debug("Removing saved file {}", path);
            File file = new File(path);
            if (file.delete()) {
                logger.debug("Deleted saved file {}", path);

                int lastIndex = path.lastIndexOf(Constants.SEPARATOR);
                String parentPath = path.substring(0, lastIndex == -1 ? 0 : lastIndex);
                while (!parentPath.isEmpty()) {
                    // Deleting parent if empty
                    File parent = new File(parentPath);
                    File[] files = parent.listFiles();
                    if (files == null || files.length != 0) {
                        break;
                    }

                    if (!parent.delete()) {
                        break;
                    }
                    logger.debug("Deleted directory {} since it is empty", parentPath);
                    lastIndex = parentPath.lastIndexOf(Constants.SEPARATOR);
                    parentPath = path.substring(0, lastIndex == -1 ? 0 : lastIndex);
                }
            } else {
                logger.warn("Unable to delete saved file {}", path);
            }
        }
        savedFiles.clear();
    }

    public Component getComponent() {
        return component;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getSavedFiles() {
        return new ArrayList<>(savedFiles);
    }

    public ZNodePaths getzNodePaths() {
        return zNodePaths;
    }
}