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
package com.synopsys.integration.detectable.detectables.yarn.parse;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.bdio.graph.DependencyGraph;
import com.synopsys.integration.bdio.graph.builder.LazyExternalIdDependencyGraphBuilder;
import com.synopsys.integration.bdio.graph.builder.MissingExternalIdException;
import com.synopsys.integration.bdio.model.Forge;
import com.synopsys.integration.bdio.model.dependencyid.DependencyId;
import com.synopsys.integration.bdio.model.dependencyid.StringDependencyId;
import com.synopsys.integration.bdio.model.externalid.ExternalId;
import com.synopsys.integration.bdio.model.externalid.ExternalIdFactory;
import com.synopsys.integration.detectable.detectables.npm.packagejson.model.PackageJson;

public class YarnTransformer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ExternalIdFactory externalIdFactory;

    public YarnTransformer(ExternalIdFactory externalIdFactory) {
        this.externalIdFactory = externalIdFactory;
    }

    public DependencyGraph transform(YarnLockResult yarnLockResult, boolean productionOnly, MissingYarnDependencyHandler missingYarnDependencyHandler) throws MissingExternalIdException {
        LazyExternalIdDependencyGraphBuilder graphBuilder = new LazyExternalIdDependencyGraphBuilder();

        addRootNodesToGraph(graphBuilder, yarnLockResult.getPackageJson(), productionOnly);

        for (YarnLockEntry entry : yarnLockResult.getYarnLock().getEntries()) {
            for (YarnLockEntryId entryId : entry.getIds()) {
                StringDependencyId id = new StringDependencyId(entryId.getName() + "@" + entryId.getVersion());
                graphBuilder.setDependencyInfo(id, entryId.getName(), entry.getVersion(), externalIdFactory.createNameVersionExternalId(Forge.NPMJS, entryId.getName(), entry.getVersion()));
                for (YarnLockDependency dependency : entry.getDependencies()) {
                    StringDependencyId stringDependencyId = new StringDependencyId(dependency.getName() + "@" + dependency.getVersion());
                    if (!productionOnly || !dependency.isOptional()) {
                        graphBuilder.addChildWithParent(stringDependencyId, id);
                    } else {
                        logger.debug(String.format("Eluding optional dependency: %s", stringDependencyId.getValue()));
                    }
                }
            }
        }

        return graphBuilder.build((dependencyId, lazyDependencyInfo) -> missingYarnDependencyHandler.handleMissingYarnDependency(logger, externalIdFactory, dependencyId, lazyDependencyInfo, yarnLockResult.getYarnLockFilePath()));
    }

    public static ExternalId handleMissingExternalIds(Logger missingDependencyLogger, ExternalIdFactory externalIdFactory, DependencyId dependencyId, LazyExternalIdDependencyGraphBuilder.LazyDependencyInfo lazyDependencyInfo,
        String yarnLockFilePath) {
        DependencyId dependencyIdToLog = Optional.ofNullable(lazyDependencyInfo)
                                             .map(LazyExternalIdDependencyGraphBuilder.LazyDependencyInfo::getAliasId)
                                             .orElse(dependencyId);

        missingDependencyLogger.warn(String.format("Missing yarn dependency. Dependency '%s' is missing from %s.", dependencyIdToLog, yarnLockFilePath));
        return externalIdFactory.createNameVersionExternalId(Forge.NPMJS, dependencyId.toString());
    }

    private void addRootNodesToGraph(LazyExternalIdDependencyGraphBuilder graphBuilder, PackageJson packageJson, boolean productionOnly) {
        for (Map.Entry<String, String> packageDependency : packageJson.dependencies.entrySet()) {
            graphBuilder.addChildToRoot(new StringDependencyId(packageDependency.getKey() + "@" + packageDependency.getValue()));
        }

        if (!productionOnly) {
            for (Map.Entry<String, String> packageDependency : packageJson.devDependencies.entrySet()) {
                graphBuilder.addChildToRoot(new StringDependencyId(packageDependency.getKey() + "@" + packageDependency.getValue()));
            }
        }
    }
}
