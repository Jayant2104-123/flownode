package dev.flownode.api;

import dev.flownode.engine.EngineEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class EngineEventLogger {
    private static final Logger log = LoggerFactory.getLogger(EngineEventLogger.class);

    @EventListener
    void on(EngineEvent e) {
        log.info("run={} node={} {} {}", e.runId(), e.nodeId() == null ? "-" : e.nodeId(), e.type(), e.detail());
    }
}
