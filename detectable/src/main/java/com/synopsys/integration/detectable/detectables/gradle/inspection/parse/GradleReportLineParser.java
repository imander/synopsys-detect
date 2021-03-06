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
package com.synopsys.integration.detectable.detectables.gradle.inspection.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.detectable.detectable.util.DetectableStringUtils;
import com.synopsys.integration.detectable.detectables.gradle.inspection.model.GradleGav;
import com.synopsys.integration.detectable.detectables.gradle.inspection.model.GradleTreeNode;
import com.synopsys.integration.detectable.detectables.gradle.inspection.model.ReplacedGradleGav;

public class GradleReportLineParser {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String[] TREE_LEVEL_TERMINALS = new String[] { "+---", "\\---" };
    private static final String[] PROJECT_INDICATORS = new String[] { "--- project " };
    private static final String COMPONENT_PREFIX = "--- ";
    private static final String[] REMOVE_SUFFIXES = new String[] { " (*)", " (c)", " (n)" };
    private static final String WINNING_INDICATOR = " -> ";

    public GradleTreeNode parseLine(String line) {
        int level = parseTreeLevel(line);
        if (!line.contains(COMPONENT_PREFIX)) {
            return GradleTreeNode.newUnknown(level);
        } else if (StringUtils.containsAny(line, PROJECT_INDICATORS)) {
            String projectName = parseProjectName(line);
            return GradleTreeNode.newProject(level, projectName);
        } else {
            GradleGavPieces gav = parseGav(line);
            if (gav.getGavPieces().size() != 3) {
                logger.trace(String.format("The line can not be reasonably split in to the necessary parts: %s", line)); //All project lines: +--- org.springframework.boot:spring-boot-starter-activemq (n)
                return GradleTreeNode.newUnknown(level);
            } else {
                String group = gav.getGavPieces().get(0);
                String artifact = gav.getGavPieces().get(1);
                String version = gav.getGavPieces().get(2);
                GradleGav resolvedGradleGav = new GradleGav(group, artifact, version);

                if (gav.getReplacedGavPieces().isEmpty()) {
                    return GradleTreeNode.newGav(level, resolvedGradleGav);
                } else if (gav.getReplacedGavPieces().size() == 2) {
                    String replacedGroup = gav.getReplacedGavPieces().get(0);
                    String replacedArtifact = gav.getReplacedGavPieces().get(1);
                    ReplacedGradleGav replacedGradleGav = new ReplacedGradleGav(replacedGroup, replacedArtifact);
                    return GradleTreeNode.newGavWithReplacement(level, resolvedGradleGav, replacedGradleGav);
                } else if (gav.getReplacedGavPieces().size() == 3) {
                    String replacedGroup = gav.getReplacedGavPieces().get(0);
                    String replacedArtifact = gav.getReplacedGavPieces().get(1);
                    String replacedVersion = gav.getReplacedGavPieces().get(2);
                    ReplacedGradleGav replacedGradleGav = new ReplacedGradleGav(replacedGroup, replacedArtifact, replacedVersion);
                    return GradleTreeNode.newGavWithReplacement(level, resolvedGradleGav, replacedGradleGav);
                } else {
                    logger.warn(String.format("The replacement gav is an unknown format: %s", line));
                    return GradleTreeNode.newGav(level, resolvedGradleGav);
                }
            }
        }
    }

    @Nullable
    private String parseProjectName(String line) {
        String cleanedOutput = StringUtils.trimToEmpty(line);
        for (String projectIndicator : PROJECT_INDICATORS) {
            cleanedOutput = cleanedOutput.substring(cleanedOutput.indexOf(projectIndicator) + projectIndicator.length());
        }
        cleanedOutput = removeSuffixes(cleanedOutput);

        return cleanedOutput;
    }

    private String removeSuffixes(String line) {
        for (String suffix : REMOVE_SUFFIXES) {
            if (line.endsWith(suffix)) {
                int lastSeenElsewhereIndex = line.lastIndexOf(suffix);
                line = line.substring(0, lastSeenElsewhereIndex);
            }
        }
        return line;
    }

    private GradleGavPieces parseGav(String line) {
        String cleanedOutput = StringUtils.trimToEmpty(line);
        cleanedOutput = cleanedOutput.substring(cleanedOutput.indexOf(COMPONENT_PREFIX) + COMPONENT_PREFIX.length());

        cleanedOutput = removeSuffixes(cleanedOutput);

        // we might need to modify the returned list, so it needs to be an actual ArrayList
        List<String> gavPieces = new ArrayList<>(Arrays.asList(cleanedOutput.split(":")));
        if (cleanedOutput.contains(WINNING_INDICATOR)) {
            // WINNING_INDICATOR can point to an entire GAV not just a version
            String winningSection = cleanedOutput.substring(cleanedOutput.indexOf(WINNING_INDICATOR) + WINNING_INDICATOR.length());
            String losingSection = cleanedOutput.substring(0, cleanedOutput.indexOf(WINNING_INDICATOR));
            if (winningSection.contains(":")) {
                gavPieces = Arrays.asList(winningSection.split(":"));
            } else {
                // the WINNING_INDICATOR is not always preceded by a : so if isn't, we need to clean up from the original split
                if (gavPieces.get(1).contains(WINNING_INDICATOR)) {
                    String withoutWinningIndicator = gavPieces.get(1).substring(0, gavPieces.get(1).indexOf(WINNING_INDICATOR));
                    gavPieces.set(1, withoutWinningIndicator);
                    // since there was no : we don't have a gav piece for version yet
                    gavPieces.add("");
                }
                gavPieces.set(2, winningSection);
            }
            return GradleGavPieces.createGavWithReplacement(gavPieces, Arrays.asList(losingSection.split(":")));
        } else {
            return GradleGavPieces.createGav(gavPieces);
        }
    }

    private int parseTreeLevel(String line) {
        if (StringUtils.startsWithAny(line, TREE_LEVEL_TERMINALS)) {
            return 0;
        }

        String modifiedLine = DetectableStringUtils.removeEvery(line, TREE_LEVEL_TERMINALS);

        if (!modifiedLine.startsWith("|") && modifiedLine.startsWith(" ")) {
            modifiedLine = "|" + modifiedLine;
        }
        modifiedLine = modifiedLine.replace("     ", "    |");
        modifiedLine = modifiedLine.replace("||", "|");
        if (modifiedLine.endsWith("|")) {
            modifiedLine = modifiedLine.substring(0, modifiedLine.length() - 5);
        }

        return StringUtils.countMatches(modifiedLine, "|");
    }

}
