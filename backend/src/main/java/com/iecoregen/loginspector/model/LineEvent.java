package com.iecoregen.loginspector.model;

public record LineEvent(
        int line,
        String type,
        String text
) {
}
