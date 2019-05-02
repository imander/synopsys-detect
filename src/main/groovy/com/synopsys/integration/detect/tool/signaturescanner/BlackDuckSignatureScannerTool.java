/**
 * synopsys-detect
 *
 * Copyright (c) 2019 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.detect.tool.signaturescanner;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.detect.configuration.ConnectionManager;
import com.synopsys.integration.detect.configuration.DetectConfiguration;
import com.synopsys.integration.detect.configuration.DetectConfigurationFactory;
import com.synopsys.integration.detect.configuration.DetectProperty;
import com.synopsys.integration.detect.configuration.PropertyAuthority;
import com.synopsys.integration.detect.exception.DetectUserFriendlyException;
import com.synopsys.integration.detect.lifecycle.DetectContext;
import com.synopsys.integration.detect.lifecycle.run.data.BlackDuckRunData;
import com.synopsys.integration.detect.workflow.file.DirectoryManager;
import com.synopsys.integration.blackduck.codelocation.CodeLocationCreationData;
import com.synopsys.integration.blackduck.codelocation.CodeLocationCreationService;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatchOutput;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatchRunner;
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfig;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.util.IntEnvironmentVariables;
import com.synopsys.integration.util.NameVersion;

public class BlackDuckSignatureScannerTool {
    private final Logger logger = LoggerFactory.getLogger(BlackDuckSignatureScannerTool.class);
    private DetectContext detectContext;
    private BlackDuckSignatureScannerOptions signatureScannerOptions;

    public BlackDuckSignatureScannerTool(final BlackDuckSignatureScannerOptions signatureScannerOptions, DetectContext detectContext) {
        this.signatureScannerOptions = signatureScannerOptions;
        this.detectContext = detectContext;
    }

    public SignatureScannerToolResult runScanTool(BlackDuckRunData blackDuckRunData, NameVersion projectNameVersion, Optional<File> dockerTar, final Optional<String> detectorProvidedJavaOptions) throws DetectUserFriendlyException {
        DetectConfiguration detectConfiguration = detectContext.getBean(DetectConfiguration.class);
        DetectConfigurationFactory detectConfigurationFactory = detectContext.getBean(DetectConfigurationFactory.class);
        ConnectionManager connectionManager = detectContext.getBean(ConnectionManager.class);
        DirectoryManager directoryManager = detectContext.getBean(DirectoryManager.class);

        Optional<BlackDuckServerConfig> blackDuckServerConfig = Optional.empty();
        if (blackDuckRunData.isOnline() && blackDuckRunData.getBlackDuckServerConfig().isPresent()) {
            blackDuckServerConfig = blackDuckRunData.getBlackDuckServerConfig();
        }

        logger.info("Will run the signature scanner tool.");
        final String offlineLocalScannerInstallPath = detectConfiguration.getProperty(DetectProperty.DETECT_BLACKDUCK_SIGNATURE_SCANNER_OFFLINE_LOCAL_PATH, PropertyAuthority.None);
        final String onlineLocalScannerInstallPath = detectConfiguration.getProperty(DetectProperty.DETECT_BLACKDUCK_SIGNATURE_SCANNER_LOCAL_PATH, PropertyAuthority.None);

        String localScannerInstallPath = "";
        if (StringUtils.isNotBlank(offlineLocalScannerInstallPath)) {
            localScannerInstallPath = offlineLocalScannerInstallPath;
            logger.debug("Determined offline local scanner path: " + localScannerInstallPath);
        } else if (StringUtils.isNotBlank(onlineLocalScannerInstallPath)) {
            localScannerInstallPath = onlineLocalScannerInstallPath;
            logger.debug("Determined online local scanner path: " + localScannerInstallPath);
        }

        final String userProvidedScannerInstallUrl = detectConfiguration.getProperty(DetectProperty.DETECT_BLACKDUCK_SIGNATURE_SCANNER_HOST_URL, PropertyAuthority.None);

        BlackDuckSignatureScannerOptions blackDuckSignatureScannerOptions = detectConfigurationFactory.createBlackDuckSignatureScannerOptions();
        final ExecutorService executorService = Executors.newFixedThreadPool(blackDuckSignatureScannerOptions.getParallelProcessors());
        IntEnvironmentVariables intEnvironmentVariables = new IntEnvironmentVariables();

        ScanBatchRunnerFactory scanBatchRunnerFactory = new ScanBatchRunnerFactory(intEnvironmentVariables, executorService);
        ScanBatchRunner scanBatchRunner;
        File installDirectory = directoryManager.getPermanentDirectory();
        if (blackDuckServerConfig.isPresent() && StringUtils.isBlank(userProvidedScannerInstallUrl) && StringUtils.isBlank(localScannerInstallPath)) {
            logger.debug("Signature scanner will use the Black Duck server to download/update the scanner - this is the most likely situation.");
            scanBatchRunner = scanBatchRunnerFactory.withInstall(blackDuckServerConfig.get());
        } else {
            if (StringUtils.isNotBlank(userProvidedScannerInstallUrl)) {
                logger.debug("Signature scanner will use the provided url to download/update the scanner.");
                scanBatchRunner = scanBatchRunnerFactory.withUserProvidedUrl(userProvidedScannerInstallUrl, connectionManager);
            } else {
                logger.debug("Signature scanner either given an existing path for the scanner or is offline - either way, we won't attempt to manage the install.");
                if (StringUtils.isNotBlank(localScannerInstallPath)) {
                    logger.debug("Using provided path: " + localScannerInstallPath);
                    installDirectory = new File(localScannerInstallPath);
                } else {
                    logger.debug("Using default scanner path.");
                }
                scanBatchRunner = scanBatchRunnerFactory.withoutInstall(installDirectory);
            }
        }
        logger.debug("Determined install directory: " + installDirectory.getAbsolutePath());

        try {
            if (blackDuckServerConfig.isPresent()) {
                logger.debug("Signature scan is online.");
                CodeLocationCreationService codeLocationCreationService = blackDuckRunData.getBlackDuckServicesFactory().get().createCodeLocationCreationService();
                OnlineBlackDuckSignatureScanner blackDuckSignatureScanner = detectContext.getBean(OnlineBlackDuckSignatureScanner.class, signatureScannerOptions, scanBatchRunner, codeLocationCreationService, blackDuckServerConfig.get());
                CodeLocationCreationData<ScanBatchOutput> codeLocationCreationData = blackDuckSignatureScanner.performOnlineScan(projectNameVersion, installDirectory, dockerTar.orElse(null), detectorProvidedJavaOptions.orElse(null));
                return SignatureScannerToolResult.createOnlineResult(codeLocationCreationData);
            } else {
                logger.debug("Signature scan is offline.");
                OfflineBlackDuckSignatureScanner blackDuckSignatureScanner = detectContext.getBean(OfflineBlackDuckSignatureScanner.class, signatureScannerOptions, scanBatchRunner);
                ScanBatchOutput scanBatchOutput = blackDuckSignatureScanner.performScanActions(projectNameVersion, installDirectory, dockerTar.orElse(null), detectorProvidedJavaOptions.orElse(null));
                return SignatureScannerToolResult.createOfflineResult(scanBatchOutput);
            }
        } catch (IOException | InterruptedException | IntegrationException e) {
            logger.error(String.format("Signature scan failed: %s", e.getMessage()));
            logger.debug("Signature scan error", e);
            return SignatureScannerToolResult.createFailureResult();
        } finally {
            executorService.shutdownNow();
        }
    }

}