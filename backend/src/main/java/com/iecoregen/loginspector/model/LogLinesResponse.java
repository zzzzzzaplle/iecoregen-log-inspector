package com.iecoregen.loginspector.model;

import java.util.List;

public record LogLinesResponse(
        String id,
        String path,
        int startLine,
        int endLine,
        List<LineEvent> lines
) {
}
