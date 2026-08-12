package com.iecoregen.loginspector.model;

public record ClassEvent(
        String name,
        int line,
        Integer responseStartLine,
        Integer responseEndLine
) {
}
