package com.iecoregen.loginspector.controller;

import com.iecoregen.loginspector.model.LogAnalysisResponse;
import com.iecoregen.loginspector.model.LogFileSummary;
import com.iecoregen.loginspector.model.LogLinesResponse;
import com.iecoregen.loginspector.service.LogAnalysisService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final LogAnalysisService service;

    public LogController(LogAnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public List<LogFileSummary> listLogs() throws IOException {
        return service.listLogs();
    }

    @GetMapping("/{id}/analysis")
    public LogAnalysisResponse analyze(@PathVariable String id) throws IOException {
        return service.analyze(id);
    }

    @GetMapping("/{id}/lines")
    public LogLinesResponse lines(
            @PathVariable String id,
            @RequestParam int start,
            @RequestParam int end
    ) throws IOException {
        return service.lines(id, start, end);
    }
}
