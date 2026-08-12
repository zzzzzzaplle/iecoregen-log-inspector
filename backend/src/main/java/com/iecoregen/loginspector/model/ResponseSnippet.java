package com.iecoregen.loginspector.model;

public record ResponseSnippet(
        String label,
        int line,
        int startLine,
        int endLine
) {
}
