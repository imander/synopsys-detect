package com.synopsys.integration.detect.workflow.report;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.synopsys.integration.detect.workflow.report.output.DetectorResultClassStatusCodeMap;

public class DetectorStatusCodeTest {

    @Test
    public void ensureAllResultClassesPresentInDetectorStatusCodeEnum() {
        DetectorResultClassStatusCodeMap detectorResultClassStatusCodeMap = new DetectorResultClassStatusCodeMap();
        Set<String> resultClassNames = getResultClassNames();
        for (Class resultClass : detectorResultClassStatusCodeMap.getMap().keySet()) {
             Assertions.assertTrue(resultClassNames.contains(resultClass.getSimpleName()));
        }
    }

    public Set<String> getResultClassNames() {
        String pathToDetectableResults = "detectable/src/main/java/com/synopsys/integration/detectable/detectable/result";
        String pathToDetectorResults = "detector/src/main/java/com/synopsys/integration/detector/result";
        String wd = System.getProperty("user.dir");

        File detectableResults = new File(pathToDetectableResults);
        File detectorResults = new File(pathToDetectorResults);
        List<File> allResults = new ArrayList<>();
        allResults.addAll(Arrays.asList(detectableResults.listFiles()));
        allResults.addAll(Arrays.asList(detectorResults.listFiles()));

        return allResults.stream()
                   .filter(it -> !it.isDirectory())
                   .map(File::getName)
                   .map(it -> it.replace(".java", ""))
                   .collect(Collectors.toSet());
    }
}
