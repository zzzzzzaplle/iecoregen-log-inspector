package com.iecoregen.loginspector.model;

public record OperationSnippet(
        String name,
        int line,
        int startLine,
        int endLine,
        String content
) {
}
