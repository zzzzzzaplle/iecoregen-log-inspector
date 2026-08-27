package com.iecoregen.loginspector.service;

import com.iecoregen.loginspector.model.ClassEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogAnalysisServiceTest {
    @Test
    void collectClassEventsUsesAvailableLinesWhenStageEndIsMissing() throws Exception {
        LogAnalysisService service = new LogAnalysisService("logs");
        List<Object> entries = List.of(
                lineEntry(1, "Code Completion for Foo"),
                lineEntry(2, "LLM Response:"),
                lineEntry(3, "public class Foo {"),
                lineEntry(4, "}"),
                lineEntry(5, "12:00:00.000 INFO next workflow line")
        );

        @SuppressWarnings("unchecked")
        List<ClassEvent> events = (List<ClassEvent>) collectClassEvents().invoke(
                service,
                entries,
                Pattern.compile("Code Completion for\\s+([A-Za-z_][A-Za-z0-9_]*)"),
                1,
                null
        );

        assertEquals(1, events.size());
        assertEquals("Foo", events.getFirst().name());
        assertEquals(2, events.getFirst().responseStartLine());
        assertEquals(4, events.getFirst().responseEndLine());
    }

    private static Method collectClassEvents() throws NoSuchMethodException {
        Method method = LogAnalysisService.class.getDeclaredMethod(
                "collectClassEvents",
                List.class,
                Pattern.class,
                Integer.class,
                Integer.class
        );
        method.setAccessible(true);
        return method;
    }

    private static Object lineEntry(int lineNumber, String text) throws Exception {
        Class<?> type = Class.forName("com.iecoregen.loginspector.service.LogAnalysisService$LineEntry");
        Constructor<?> constructor = type.getDeclaredConstructor(int.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(lineNumber, text);
    }
}
