package com.example.csvbatchlauncher;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight liveness endpoint used by the local operations MCP. */
@RestController
public class HealthController {
    @GetMapping("/")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
