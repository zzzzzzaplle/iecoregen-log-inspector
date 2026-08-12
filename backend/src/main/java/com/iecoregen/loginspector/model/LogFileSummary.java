package com.iecoregen.loginspector.model;

public record LogFileSummary(
        String id,
        String path,
        String caseName,
        String model,
        String run
) {
}
