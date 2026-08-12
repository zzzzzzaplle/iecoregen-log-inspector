package com.iecoregen.loginspector.model;

import java.util.List;

public record StageAnalysis(
        String key,
        String title,
        Integer startLine,
        Integer endLine,
        boolean completed,
        List<LineEvent> events
) {
}
