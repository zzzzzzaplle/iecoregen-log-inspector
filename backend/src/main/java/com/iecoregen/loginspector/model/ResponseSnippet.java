package com.iecoregen.loginspector.model;

import java.util.List;

public record ResponseSnippet(
        String label,
        int line,
        int startLine,
        int endLine,
        List<OperationSnippet> operations
) {
}
