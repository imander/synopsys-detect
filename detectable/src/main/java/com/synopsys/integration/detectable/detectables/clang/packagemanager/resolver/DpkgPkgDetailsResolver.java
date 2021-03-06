/**
 * detectable
 *
 * Copyright (c) 2020 Synopsys, Inc.
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
package com.synopsys.integration.detectable.detectables.clang.packagemanager.resolver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.detectable.detectable.executable.ExecutableOutput;
import com.synopsys.integration.detectable.detectable.executable.ExecutableRunner;
import com.synopsys.integration.detectable.detectable.executable.ExecutableRunnerException;
import com.synopsys.integration.detectable.detectables.clang.packagemanager.ClangPackageManagerInfo;
import com.synopsys.integration.detectable.detectables.clang.packagemanager.PackageDetails;

public class DpkgPkgDetailsResolver {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final int PKG_INFO_LINE_LABEL_POSITION = 0;
    private static final int PKG_INFO_LINE_VALUE_POSITION = 1;

    public Optional<PackageDetails> resolvePackageDetails(ClangPackageManagerInfo currentPackageManager, ExecutableRunner executableRunner, File workingDirectory, String packageName) {
        try {
            List<String> args = new ArrayList<>(currentPackageManager.getPkgInfoArgs().get());
            args.add(packageName);
            ExecutableOutput packageInfoOutput = executableRunner.execute(workingDirectory, currentPackageManager.getPkgMgrCmdString(), args);
            return parsePackageDetailsFromInfoOutput(packageName, packageInfoOutput.getStandardOutput());
        } catch (ExecutableRunnerException e) {
            logger.warn(String.format("Error executing %s to get package info: %s", currentPackageManager.getPkgMgrName(), e.getMessage()));
        }
        return Optional.empty();
    }

    private Optional<PackageDetails> parsePackageDetailsFromInfoOutput(String packageName, String packageInfoOutput) {
        String packageArchitecture = null;
        String packageVersion = null;
        String[] packageInfoOutputLines = packageInfoOutput.split("\\n");
        for (String packageInfoOutputLine : packageInfoOutputLines) {
            if (foundUninstalledStatus(packageName, packageInfoOutputLine)) {
                return Optional.empty();
            }
            packageArchitecture = parseNeededValueFromLineIfPresent(packageName, packageInfoOutputLine, "Architecture", packageArchitecture);
            packageVersion = parseNeededValueFromLineIfPresent(packageName, packageInfoOutputLine, "Version", packageVersion);
        }
        if ((packageVersion == null) || (packageArchitecture == null)) {
            logger.warn(String.format("Unable to determine all details for package %s (version: %s; architecture: %s); this package will be omitted from the output",
                packageName, packageVersion, packageArchitecture));
            return Optional.empty();
        }
        return Optional.of(new PackageDetails(packageName, packageVersion, packageArchitecture));
    }

    private String parseNeededValueFromLineIfPresent(String packageName, String packageInfoOutputLine, String targetLabel, String currentValue) {
        if (currentValue != null) {
            return currentValue;
        }
        return parseValueFromLineIfPresent(packageName, packageInfoOutputLine, targetLabel).orElse(currentValue);
    }

    private Optional<String> parseValueFromLineIfPresent(String packageName, String packageInfoOutputLine, String targetLabel) {
        String[] packageInfoOutputLineParts = packageInfoOutputLine.split(":\\s+");
        String parsedLabel = packageInfoOutputLineParts[PKG_INFO_LINE_LABEL_POSITION].trim();
        if (targetLabel.equals(parsedLabel)) {
            if (packageInfoOutputLineParts.length > PKG_INFO_LINE_VALUE_POSITION) {
                String parsedValue = packageInfoOutputLineParts[PKG_INFO_LINE_VALUE_POSITION].trim();
                if (StringUtils.isNotBlank(parsedValue)) {
                    return Optional.of(parsedValue);
                }
            }
            logger.warn(String.format("Package %s: %s field value is missing", packageName, targetLabel));
        }
        return Optional.empty();
    }

    private boolean foundUninstalledStatus(String packageName, String packageInfoOutputLine) {
        String[] packageInfoOutputLineParts = packageInfoOutputLine.split(":\\s+");
        String label = packageInfoOutputLineParts[PKG_INFO_LINE_LABEL_POSITION];
        if ("Status".equals(label.trim())) {
            if (packageInfoOutputLineParts.length > PKG_INFO_LINE_VALUE_POSITION) {
                String value = packageInfoOutputLineParts[PKG_INFO_LINE_VALUE_POSITION];
                if ((value != null) && !value.contains("installed")) {
                    logger.debug(String.format("Package is not installed; Status is: %s", packageName, value));
                    return true;
                }
            } else {
                logger.warn(String.format("Missing value for Status field for package %s", packageName));
            }
        }
        return false;
    }
}
