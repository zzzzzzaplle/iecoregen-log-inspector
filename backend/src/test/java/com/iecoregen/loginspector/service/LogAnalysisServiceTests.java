package com.iecoregen.loginspector.service;

import com.iecoregen.loginspector.model.LogAnalysisResponse;
import com.iecoregen.loginspector.model.LogFileSummary;
import com.iecoregen.loginspector.model.SampleAnalysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LogAnalysisServiceTests {

    @TempDir
    Path logsRoot;

    @Test
    void analyzeKeepsPartialCodeFixingStageWhenSuccessMarkerIsMissing() throws IOException {
        Path logPath = logsRoot.resolve("PA19/minimax-m3/sample5/log.txt");
        Files.createDirectories(logPath.getParent());
        Files.writeString(logPath, """
                [minimax-m3] 正在启动 sample1.mwe2...
                103 [main] INFO  .sei.mde.mwe2.EMFGeneratorFragment2  - Generating EMF model code
                104 [main] INFO  du.ustb.sei.mde.mwe2.EcoreGenerator  - Code Completion for Game
                105 [main] INFO  du.ustb.sei.mde.mwe2.EcoreGenerator  - Code Fixing
                106 [main] INFO  du.ustb.sei.mde.mwe2.EcoreGenerator  - Fixing Game
                107 [main] INFO  du.ustb.sei.mde.mwe2.EcoreGenerator  - LLM Response:
                fixed code
                108 [main] INFO  .emf.mwe2.runtime.workflow.Workflow  - Done.
                sample1.mwe2 启动完成
                """, StandardCharsets.UTF_8);

        LogAnalysisService service = new LogAnalysisService(logsRoot.toString());
        LogFileSummary summary = service.listLogs().getFirst();
        LogAnalysisResponse response = service.analyze(summary.id());
        SampleAnalysis sample = response.samples().getFirst();

        assertEquals("NEEDS_ATTENTION", sample.status());
        assertEquals(1, sample.fixingClasses().size());
        assertEquals("Game", sample.fixingClasses().getFirst().name());
        assertNotNull(sample.fixingClasses().getFirst().responseStartLine());
    }

    @Test
    void analyzeSplitsMultipleOperationMarkersOnOneResponseLine() throws IOException {
        Path logPath = logsRoot.resolve("PA19/qwen3.6-flash/sample1/log.txt");
        Files.createDirectories(logPath.getParent());
        Files.writeString(logPath, """
                [qwen3.6-flash] 正在启动 sample1.mwe2...
                360 [main] INFO  du.ustb.sei.mde.mwe2.EcoreGenerator  - Annotating EOperations
                949 [main] INFO  du.ustb.sei.mde.mwe2.EcoreGenerator  - LLM Response:
                {org.eclipse.emf.ecore.impl.EOperationImpl@aaa (name: firstOperation) (ordered: true, unique: true, lowerBound: 0, upperBound: 1)=Summary: First operation., org.eclipse.emf.ecore.impl.EOperationImpl@bbb (name: secondOperation) (ordered: true, unique: true, lowerBound: 0, upperBound: 1)=Summary: Second operation.
                Details for second operation.
                1189 [main] INFO  du.ustb.sei.mde.mwe2.EcoreGenerator  - Verify Annotations
                1190 [main] INFO  du.ustb.sei.mde.mwe2.EcoreGenerator  - Verification End
                2000 [main] ERROR du.ustb.sei.mde.mwe2.EcoreGenerator  - Cannot invoke "java.lang.Iterable.iterator()" because "iterable" is null
                sample1.mwe2 启动完成
                """, StandardCharsets.UTF_8);

        LogAnalysisService service = new LogAnalysisService(logsRoot.toString());
        LogFileSummary summary = service.listLogs().getFirst();
        SampleAnalysis sample = service.analyze(summary.id()).samples().getFirst();

        assertEquals(1, sample.operationAnnotationResponses().size());
        assertEquals(2, sample.operationAnnotationResponses().getFirst().operations().size());
        assertEquals("firstOperation", sample.operationAnnotationResponses().getFirst().operations().get(0).name());
        assertEquals("secondOperation", sample.operationAnnotationResponses().getFirst().operations().get(1).name());
        assertFalse(sample.operationAnnotationResponses().getFirst().operations().get(0).content().contains("secondOperation"));
    }
}
