package com.iecoregen.loginspector.model;

import java.util.List;

public record LogAnalysisResponse(
        String id,
        String path,
        int totalLines,
        List<SampleAnalysis> samples
) {
}
