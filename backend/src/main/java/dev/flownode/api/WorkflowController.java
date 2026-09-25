package dev.flownode.api;

import dev.flownode.engine.ExecutionEngine;
import dev.flownode.engine.InvalidWorkflowException;
import dev.flownode.engine.RunState;
import dev.flownode.engine.RunView;
import dev.flownode.engine.WorkflowSpec;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
class WorkflowController {
    private static final int MAX_RUNS_KEPT = 200;

    private final ExecutionEngine engine;

    /** Runs live in memory only, and the oldest are dropped after MAX_RUNS_KEPT. */
    private final Map<String, RunState> runs = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RunState> eldest) {
            return size() > MAX_RUNS_KEPT;
        }
    });

    WorkflowController(ExecutionEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/workflows/validate")
    Map<String, Object> validate(@RequestBody WorkflowSpec spec) {
        List<String> order = engine.validate(spec);
        return Map.of("valid", true, "executionOrder", order);
    }

    @PostMapping("/runs")
    ResponseEntity<Map<String, String>> start(@RequestBody WorkflowSpec spec) {
        RunState run = engine.start(spec);
        runs.put(run.runId(), run);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("runId", run.runId()));
    }

    @GetMapping("/runs/{runId}")
    ResponseEntity<RunView> get(@PathVariable String runId) {
        RunState run = runs.get(runId);
        return run == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(run.view());
    }

    @ExceptionHandler(InvalidWorkflowException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidWorkflowException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
