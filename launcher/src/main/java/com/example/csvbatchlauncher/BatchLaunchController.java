package com.example.csvbatchlauncher;

import java.nio.file.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/batch-launches")
public class BatchLaunchController {
    private final BatchLaunchQueue queue;
    public BatchLaunchController(BatchLaunchQueue queue) { this.queue = queue; }
    @PostMapping
    public ResponseEntity<LaunchResponse> submit(@RequestBody LaunchRequest request) {
        var result = queue.enqueue(Path.of(request.file()));
        return ResponseEntity.accepted().body(new LaunchResponse(result.requestId(), result.status(), result.type().key()));
    }
    @GetMapping("/{requestId}")
    public LaunchResponse status(@PathVariable String requestId) { var result = queue.status(requestId); return new LaunchResponse(result.requestId(), result.status(), result.type().key()); }
}
