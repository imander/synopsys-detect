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

    public YarnTransformer(final ExternalIdFactory externalIdFactory) {
        this.externalIdFactory = externalIdFactory;
    }

    public DependencyGraph transform(final PackageJson packageJson, final YarnLock yarnLock, final boolean productionOnly) throws MissingExternalIdException {
        final LazyExternalIdDependencyGraphBuilder graphBuilder = new LazyExternalIdDependencyGraphBuilder();

        for (final Map.Entry<String, String> packageDependency : packageJson.dependencies.entrySet()) {
            graphBuilder.addChildToRoot(new StringDependencyId(packageDependency.getKey() + "@" + packageDependency.getValue()));
        }

        if (!productionOnly) {
            for (final Map.Entry<String, String> packageDependency : packageJson.devDependencies.entrySet()) {
                graphBuilder.addChildToRoot(new StringDependencyId(packageDependency.getKey() + "@" + packageDependency.getValue()));
            }
        }

        for (final YarnLockEntry entry : yarnLock.getEntries()) {
            for (final YarnLockEntryId entryId : entry.getIds()) {
                final StringDependencyId id = new StringDependencyId(entryId.getName() + "@" + entryId.getVersion());
                graphBuilder.setDependencyInfo(id, entryId.getName(), entry.getVersion(), externalIdFactory.createNameVersionExternalId(Forge.NPMJS, entryId.getName(), entry.getVersion()));
                for (final YarnLockDependency dependency : entry.getDependencies()) {
                    final StringDependencyId stringDependencyId = new StringDependencyId(dependency.getName() + "@" + dependency.getVersion());
                    if (!productionOnly || !dependency.isOptional()) {
                        graphBuilder.addChildWithParent(stringDependencyId, id);
                    } else {
                        logger.debug(String.format("Eluding optional dependency: %s", stringDependencyId.getValue()));
                    }
                }
            }
        }

        return graphBuilder.build(this::handleMissingExternalIds);
    }

    private ExternalId handleMissingExternalIds(final DependencyId dependencyId, final LazyExternalIdDependencyGraphBuilder.LazyDependencyInfo lazyDependencyInfo) throws MissingExternalIdException {
        if (lazyDependencyInfo != null) {
            DependencyId idToLog = dependencyId;
            if (lazyDependencyInfo.getAliasId() != null) {
                idToLog = lazyDependencyInfo.getAliasId();
            }
            logger.warn(String.format("Missing yarn dependency. Dependency '%s' is missing from yarn.lock.", idToLog));

            // TODO: This is to trick LazyExternalIdDependencyGraphBuilder::build to not throw an exception. This won't match in the KB. LazyExternalIdDependencyGraphBuilder::build should allow for not throwing an exception. See IDETECT-1974.
            return externalIdFactory.createNameVersionExternalId(Forge.NPMJS, dependencyId.toString());
        } else {
            // The graph is bad. Nothing more Detect can do until the ability to never throw exists.
            throw new MissingExternalIdException(dependencyId);
        }
    }
}
