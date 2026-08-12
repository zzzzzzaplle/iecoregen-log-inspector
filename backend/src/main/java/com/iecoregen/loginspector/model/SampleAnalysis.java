package com.iecoregen.loginspector.model;

import java.util.List;

public record SampleAnalysis(
        String id,
        String name,
        int startLine,
        int endLine,
        int lineCount,
        String status,
        Integer effectiveStartLine,
        Integer lastIterableErrorLine,
        List<LineEvent> exceptions,
        List<StageAnalysis> stages,
        List<ResponseSnippet> operationAnnotationResponses,
        List<ResponseSnippet> operationVerificationResponses,
        List<ClassEvent> codeCompletionClasses,
        List<ClassEvent> fixingClasses
) {
}
