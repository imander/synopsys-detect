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
package com.synopsys.integration.detectable.detectables.gradle.inspection.model;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

public class GradleTreeNode {
    public GradleTreeNode(NodeType nodeType, int level, @Nullable GradleGav gav, @Nullable ReplacedGradleGav replacedGav, @Nullable String projectName) {
        this.nodeType = nodeType;
        this.level = level;
        this.gav = gav;
        this.replacedGav = replacedGav;
        this.projectName = projectName;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public Optional<GradleGav> getGav() {
        return Optional.ofNullable(gav);
    }

    public Optional<ReplacedGradleGav> getReplacedGav() {
        return Optional.ofNullable(replacedGav);
    }

    public Optional<String> getProjectName() {
        return Optional.ofNullable(projectName);
    }

    public int getLevel() {
        return level;
    }

    public enum NodeType {
        GAV,
        UNKOWN,
        PROJECT
    }

    private final NodeType nodeType;
    private final int level;
    private final GradleGav gav;
    private final ReplacedGradleGav replacedGav;
    private final String projectName;

    public static GradleTreeNode newProject(int level, String projectName) {
        return new GradleTreeNode(NodeType.PROJECT, level, null, null, projectName);
    }

    public static GradleTreeNode newGav(int level, GradleGav resolvedGav) {
        return new GradleTreeNode(NodeType.GAV, level, resolvedGav, null, null);
    }

    public static GradleTreeNode newGavWithReplacement(int level, GradleGav resolvedGav, ReplacedGradleGav replacedGav) {
        return new GradleTreeNode(NodeType.GAV, level, resolvedGav, replacedGav, null);
    }

    public static GradleTreeNode newUnknown(int level) {
        return new GradleTreeNode(NodeType.UNKOWN, level, null, null, null);
    }
}
