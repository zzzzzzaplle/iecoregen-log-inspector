package com.iecoregen.loginspector.service;

import com.iecoregen.loginspector.model.ClassEvent;
import com.iecoregen.loginspector.model.LineEvent;
import com.iecoregen.loginspector.model.LogAnalysisResponse;
import com.iecoregen.loginspector.model.LogFileSummary;
import com.iecoregen.loginspector.model.LogLinesResponse;
import com.iecoregen.loginspector.model.ResponseSnippet;
import com.iecoregen.loginspector.model.SampleAnalysis;
import com.iecoregen.loginspector.model.StageAnalysis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class LogAnalysisService {
    private static final Pattern SAMPLE_START = Pattern.compile("\\[([^]]+)]\\s+正在启动\\s+(sample\\d+\\.mwe2)");
    private static final Pattern SAMPLE_END = Pattern.compile("^(sample\\d+\\.mwe2)\\s+启动完成");
    private static final Pattern CODE_COMPLETION = Pattern.compile("Code Completion for\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern FIXING_CLASS = Pattern.compile("Fixing\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern COMPILER_ERROR = Pattern.compile("ERROR in .*\\.java");
    private static final Pattern COMPILER_WARNING = Pattern.compile("WARNING in .*\\.java");
    private static final Pattern PROBLEM_SUMMARY = Pattern.compile("\\d+ problems? \\(\\d+ (?:errors?|warnings?)\\)");

    private static final String ITERABLE_ERROR = "Cannot invoke \"java.lang.Iterable.iterator()\" because \"iterable\" is null";
    private static final String REACTOR = "reactor.util.Loggers -- Using Slf4j logging framework";
    private static final String ANNOTATING = "Annotating EOperations";
    private static final String VERIFY = "Verify Annotations";
    private static final String VERIFICATION_END = "Verification End";
    private static final String GENERATING_CODE = "Generating EMF model code";
    private static final String CODE_FIXING = "Code Fixing";
    private static final String NO_COMPILATION_ERROR = "There is no more compilation error";
    private static final String WORKFLOW_DONE = ".emf.mwe2.runtime.workflow.Workflow  - Done.";
    private static final String LLM_RESPONSE = "LLM Response:";

    private final Path logsRoot;

    public LogAnalysisService(@Value("${app.logs-root:logs}") String logsRoot) {
        this.logsRoot = resolveLogsRoot(logsRoot);
    }

    public List<LogFileSummary> listLogs() throws IOException {
        if (!Files.isDirectory(logsRoot)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(logsRoot, 4)) {
            return stream
                    .filter(path -> path.getFileName().toString().equals("log.txt"))
                    .filter(path -> logsRoot.relativize(path).getNameCount() == 4)
                    .map(this::toSummary)
                    .sorted(Comparator.comparing(LogFileSummary::path))
                    .toList();
        }
    }

    public LogAnalysisResponse analyze(String id) throws IOException {
        LogFileSummary summary = findLog(id);
        Path logPath = logsRoot.resolve(summary.path()).normalize();
        List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
        List<SampleRange> ranges = splitSamples(lines, summary);
        List<SampleAnalysis> samples = ranges.stream()
                .map(range -> analyzeSample(range, lines))
                .toList();
        return new LogAnalysisResponse(summary.id(), summary.path(), lines.size(), samples);
    }

    public LogLinesResponse lines(String id, int startLine, int endLine) throws IOException {
        LogFileSummary summary = findLog(id);
        Path logPath = logsRoot.resolve(summary.path()).normalize();
        List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
        int safeStart = Math.max(1, startLine);
        int safeEnd = Math.min(lines.size(), Math.max(safeStart, endLine));
        List<LineEvent> events = new ArrayList<>();
        for (int line = safeStart; line <= safeEnd; line++) {
            events.add(new LineEvent(line, "raw", lines.get(line - 1)));
        }
        return new LogLinesResponse(summary.id(), summary.path(), safeStart, safeEnd, events);
    }

    private Path resolveLogsRoot(String configuredRoot) {
        Path configured = Paths.get(configuredRoot);
        if (configured.isAbsolute() && Files.isDirectory(configured)) {
            return configured.normalize();
        }

        Path cwd = Paths.get("").toAbsolutePath();
        List<Path> candidates = List.of(
                cwd.resolve(configured),
                cwd.resolve("logs"),
                cwd.resolve("..").resolve("logs")
        );
        return candidates.stream()
                .map(Path::normalize)
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(cwd.resolve(configured).normalize());
    }

    private LogFileSummary findLog(String id) throws IOException {
        return listLogs().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown log id: " + id));
    }

    private LogFileSummary toSummary(Path path) {
        Path relative = logsRoot.relativize(path);
        String normalized = relative.toString().replace('\\', '/');
        return new LogFileSummary(
                encodeId(normalized),
                normalized,
                relative.getName(0).toString(),
                relative.getName(1).toString(),
                relative.getName(2).toString()
        );
    }

    private String encodeId(String path) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }

    private List<SampleRange> splitSamples(List<String> lines, LogFileSummary summary) {
        List<SampleRange> ranges = new ArrayList<>();
        OpenSample current = null;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            Matcher start = SAMPLE_START.matcher(line);
            if (start.find()) {
                if (current != null) {
                    ranges.add(current.close(index - 1, true, summary));
                }
                current = new OpenSample(start.group(2), index, summary);
                continue;
            }

            Matcher end = SAMPLE_END.matcher(line);
            if (current != null && end.find() && end.group(1).equals(current.name())) {
                ranges.add(current.close(index, false, summary));
                current = null;
            }
        }

        if (current != null) {
            ranges.add(current.close(lines.size() - 1, true, summary));
        }

        return ranges;
    }

    private SampleAnalysis analyzeSample(SampleRange range, List<String> lines) {
        List<LineEntry> entries = entries(range, lines);
        List<LineEvent> iterableErrors = collectContains(entries, "iterable", ITERABLE_ERROR);
        Integer lastIterableLine = lastLine(iterableErrors);

        Integer effectiveStartLine = lastIterableLine == null
                ? findFirstContains(entries, ANNOTATING, range.startLine())
                : findFirstContains(entries, ANNOTATING, lastIterableLine + 1);

        Integer verifyLine = findFirstContains(entries, VERIFY, effectiveStartLine);
        Integer verificationEndLine = findFirstContains(entries, VERIFICATION_END, verifyLine);
        Integer generatingCodeLine = findFirstContains(entries, GENERATING_CODE, verificationEndLine);
        Integer codeFixingLine = findFirstContains(entries, CODE_FIXING, generatingCodeLine);
        Integer noCompilationErrorLine = findFirstContains(entries, NO_COMPILATION_ERROR, codeFixingLine);
        Integer workflowDoneLine = findFirstContains(entries, WORKFLOW_DONE, noCompilationErrorLine);

        List<LineEvent> exceptions = collectExceptions(entries);
        List<ResponseSnippet> annotationResponses = collectResponseSnippets(entries, effectiveStartLine, verifyLine);
        List<ResponseSnippet> verificationResponses = collectResponseSnippets(entries, verifyLine, verificationEndLine);
        List<ClassEvent> completionClasses = collectClassEvents(entries, CODE_COMPLETION, generatingCodeLine, codeFixingLine);
        List<ClassEvent> fixingClasses = collectClassEvents(entries, FIXING_CLASS, codeFixingLine, noCompilationErrorLine);

        List<StageAnalysis> stages = List.of(
                stage("exceptions", "异常列表", range.startLine(), range.endLine(), !exceptions.isEmpty(), exceptions),
                stage("operationAnnotation", "操作规格补全阶段", effectiveStartLine, verifyLine, effectiveStartLine != null && verifyLine != null,
                        eventsBetween(entries, effectiveStartLine, verifyLine, List.of(ANNOTATING, ITERABLE_ERROR))),
                stage("operationVerification", "操作规格校验阶段", verifyLine, verificationEndLine, verifyLine != null && verificationEndLine != null,
                        eventsBetween(entries, verifyLine, verificationEndLine, List.of(VERIFY, VERIFICATION_END))),
                stage("codeCompletion", "代码补全阶段", generatingCodeLine, codeFixingLine, generatingCodeLine != null && codeFixingLine != null,
                        completionClasses.stream().map(item -> new LineEvent(item.line(), "class", "Code Completion for " + item.name())).toList()),
                stage("codeFixing", "代码修复阶段", codeFixingLine, noCompilationErrorLine, codeFixingLine != null && noCompilationErrorLine != null,
                        fixingClasses.stream().map(item -> new LineEvent(item.line(), "class", "Fixing " + item.name())).toList()),
                stage("finalStatus", "最终状态", noCompilationErrorLine, range.endLine(), noCompilationErrorLine != null && !range.unclosed(),
                        finalEvents(entries, noCompilationErrorLine, workflowDoneLine, range.endLine()))
        );

        String status = noCompilationErrorLine != null && workflowDoneLine != null && !range.unclosed()
                ? "SUCCESS"
                : "NEEDS_ATTENTION";

        return new SampleAnalysis(
                range.id(),
                range.name(),
                range.startLine(),
                range.endLine(),
                range.endLine() - range.startLine() + 1,
                status,
                effectiveStartLine,
                lastIterableLine,
                exceptions,
                stages,
                annotationResponses,
                verificationResponses,
                completionClasses,
                fixingClasses
        );
    }

    private List<LineEntry> entries(SampleRange range, List<String> lines) {
        List<LineEntry> entries = new ArrayList<>();
        for (int index = range.startIndex(); index <= range.endIndex() && index < lines.size(); index++) {
            entries.add(new LineEntry(index + 1, lines.get(index)));
        }
        return entries;
    }

    private Integer findFirstContains(List<LineEntry> entries, String marker, Integer afterLine) {
        if (marker == null || afterLine == null) {
            return null;
        }
        return entries.stream()
                .filter(entry -> entry.lineNumber() >= afterLine)
                .filter(entry -> entry.text().contains(marker))
                .map(LineEntry::lineNumber)
                .findFirst()
                .orElse(null);
    }

    private List<LineEvent> collectExceptions(List<LineEntry> entries) {
        List<LineEvent> events = new ArrayList<>();
        events.addAll(collectContains(entries, "iterable", ITERABLE_ERROR));
        events.addAll(collectContains(entries, "retry-error", "Retry error"));
        events.addAll(collectContains(entries, "retry-failed", "Retry failed last attempt"));
        events.addAll(collectContains(entries, "eof", "EOF reached while reading"));
        events.addAll(collectContains(entries, "caused-by", "Caused by:"));
        events.addAll(collectPattern(entries, "compiler-error", COMPILER_ERROR));
        events.addAll(collectPattern(entries, "compiler-warning", COMPILER_WARNING));
        events.addAll(collectPattern(entries, "problem-summary", PROBLEM_SUMMARY));
        return events.stream()
                .sorted(Comparator.comparingInt(LineEvent::line))
                .toList();
    }

    private List<LineEvent> collectContains(List<LineEntry> entries, String type, String marker) {
        return entries.stream()
                .filter(entry -> entry.text().contains(marker))
                .map(entry -> new LineEvent(entry.lineNumber(), type, entry.text().trim()))
                .toList();
    }

    private List<LineEvent> collectPattern(List<LineEntry> entries, String type, Pattern pattern) {
        return entries.stream()
                .filter(entry -> pattern.matcher(entry.text()).find())
                .map(entry -> new LineEvent(entry.lineNumber(), type, entry.text().trim()))
                .toList();
    }

    private List<ClassEvent> collectClassEvents(List<LineEntry> entries, Pattern pattern, Integer startLine, Integer endLine) {
        List<LineEntry> filteredEntries = entries.stream()
                .filter(entry -> startLine == null || entry.lineNumber() >= startLine)
                .filter(entry -> endLine == null || entry.lineNumber() <= endLine)
                .toList();

        List<LineEntry> markerEntries = filteredEntries.stream()
                .filter(entry -> pattern.matcher(entry.text()).find())
                .toList();

        List<ClassEvent> result = new ArrayList<>();
        for (int index = 0; index < markerEntries.size(); index++) {
            LineEntry markerEntry = markerEntries.get(index);
            Integer nextMarkerLine = index + 1 < markerEntries.size() ? markerEntries.get(index + 1).lineNumber() : endLine;
            classEvent(markerEntry, pattern)
                    .map(event -> enrichClassEvent(event, filteredEntries, nextMarkerLine, endLine))
                    .ifPresent(result::add);
        }
        return result;
    }

    private Optional<ClassEvent> classEvent(LineEntry entry, Pattern pattern) {
        Matcher matcher = pattern.matcher(entry.text());
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new ClassEvent(matcher.group(1), entry.lineNumber(), null, null));
    }

    private ClassEvent enrichClassEvent(ClassEvent event, List<LineEntry> entries, Integer nextMarkerLine, Integer stageEndLine) {
        int responseSearchEnd = nextMarkerLine != null ? nextMarkerLine - 1 : (stageEndLine != null ? stageEndLine : event.line());
        Integer responseMarkerLine = findFirstContains(entries, LLM_RESPONSE, event.line());
        if (responseMarkerLine != null && responseMarkerLine > responseSearchEnd) {
            responseMarkerLine = null;
        }
        if (responseMarkerLine == null) {
            return event;
        }

        int responseStartLine = responseMarkerLine + 1;
        Integer responseEndLine = findResponseEnd(entries, responseStartLine, responseSearchEnd);

        return new ClassEvent(event.name(), event.line(), responseStartLine, responseEndLine);
    }

    private Integer findResponseEnd(List<LineEntry> entries, int startLine, int maxLine) {
        Integer lastContentLine = null;
        for (LineEntry entry : entries) {
            if (entry.lineNumber() < startLine) {
                continue;
            }
            if (entry.lineNumber() > maxLine) {
                break;
            }
            if (looksLikeLogPrefix(entry.text())) {
                break;
            }
            lastContentLine = entry.lineNumber();
        }
        return lastContentLine != null ? lastContentLine : startLine;
    }

    private List<ResponseSnippet> collectResponseSnippets(List<LineEntry> entries, Integer startLine, Integer endLine) {
        if (startLine == null || endLine == null) {
            return List.of();
        }

        List<LineEntry> filteredEntries = entries.stream()
                .filter(entry -> entry.lineNumber() >= startLine && entry.lineNumber() <= endLine)
                .toList();

        List<LineEntry> responseMarkers = filteredEntries.stream()
                .filter(entry -> entry.text().contains(LLM_RESPONSE))
                .toList();

        List<ResponseSnippet> result = new ArrayList<>();
        for (int index = 0; index < responseMarkers.size(); index++) {
            LineEntry marker = responseMarkers.get(index);
            int searchEnd = index + 1 < responseMarkers.size()
                    ? responseMarkers.get(index + 1).lineNumber() - 1
                    : endLine;
            int responseStartLine = responseContentStartsInline(marker) ? marker.lineNumber() : marker.lineNumber() + 1;
            int responseEndLine = findResponseEnd(entries, responseStartLine, searchEnd);
            result.add(new ResponseSnippet(
                    "LLM Response" + (index + 1),
                    marker.lineNumber(),
                    responseStartLine,
                    responseEndLine
            ));
        }
        return result;
    }

    private boolean responseContentStartsInline(LineEntry entry) {
        int markerIndex = entry.text().indexOf(LLM_RESPONSE);
        if (markerIndex < 0) {
            return false;
        }
        String trailing = entry.text().substring(markerIndex + LLM_RESPONSE.length()).trim();
        return !trailing.isEmpty();
    }

    private boolean looksLikeLogPrefix(String text) {
        return text.matches("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} .*")
                || text.matches("^\\d+ \\[.*");
    }

    private List<LineEvent> eventsBetween(List<LineEntry> entries, Integer startLine, Integer endLine, List<String> markers) {
        if (startLine == null || endLine == null) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> entry.lineNumber() >= startLine && entry.lineNumber() <= endLine)
                .filter(entry -> markers.stream().anyMatch(marker -> entry.text().contains(marker)))
                .map(entry -> new LineEvent(entry.lineNumber(), "marker", entry.text().trim()))
                .toList();
    }

    private List<LineEvent> finalEvents(List<LineEntry> entries, Integer noCompilationErrorLine, Integer workflowDoneLine, int endLine) {
        List<LineEvent> events = new ArrayList<>();
        if (noCompilationErrorLine != null) {
            events.add(textAt(entries, noCompilationErrorLine, "no-compilation-error"));
        }
        if (workflowDoneLine != null) {
            events.add(textAt(entries, workflowDoneLine, "workflow-done"));
        }
        events.add(textAt(entries, endLine, "sample-end"));
        return events.stream().filter(event -> event.text() != null).toList();
    }

    private LineEvent textAt(List<LineEntry> entries, int line, String type) {
        return entries.stream()
                .filter(entry -> entry.lineNumber() == line)
                .findFirst()
                .map(entry -> new LineEvent(line, type, entry.text().trim()))
                .orElse(new LineEvent(line, type, null));
    }

    private StageAnalysis stage(String key, String title, Integer startLine, Integer endLine, boolean completed, List<LineEvent> events) {
        return new StageAnalysis(key, title, startLine, endLine, completed, events);
    }

    private Integer lastLine(List<LineEvent> events) {
        if (events.isEmpty()) {
            return null;
        }
        return events.get(events.size() - 1).line();
    }

    private record LineEntry(int lineNumber, String text) {
    }

    private record OpenSample(String name, int startIndex, LogFileSummary summary) {
        SampleRange close(int endIndex, boolean unclosed, LogFileSummary summary) {
            String id = "%s/%s/%s#%s".formatted(summary.caseName(), summary.model(), summary.run(), name);
            return new SampleRange(id, name, startIndex, endIndex, startIndex + 1, endIndex + 1, unclosed);
        }
    }

    private record SampleRange(
            String id,
            String name,
            int startIndex,
            int endIndex,
            int startLine,
            int endLine,
            boolean unclosed
    ) {
    }
}
