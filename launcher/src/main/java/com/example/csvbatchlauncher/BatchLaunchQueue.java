package com.example.csvbatchlauncher;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BatchLaunchQueue {
    private final JdbcTemplate jdbc;
    private final BatchProcessExecutor executor;
    private final BatchReadinessStrategies readinessStrategies;

    public BatchLaunchQueue(JdbcTemplate jdbc, BatchProcessExecutor executor, BatchReadinessStrategies readinessStrategies) {
        this.jdbc = jdbc;
        this.executor = executor;
        this.readinessStrategies = readinessStrategies;
    }

    @PostConstruct
    public void recoverInterruptedJobs() {
        jdbc.update("UPDATE batch_launch_request SET status='QUEUED', started_at=NULL, error='Recovered after launcher restart' WHERE status='RUNNING'");
    }

    public Result enqueue(Path file) {
        try {
            Path normalizedFile = file.toAbsolutePath().normalize();
            boolean isRegularFile = Files.isRegularFile(normalizedFile);
            if (!isRegularFile) {
                throw new IllegalArgumentException("CSV file does not exist");
            }
            BatchType type = BatchType.from(normalizedFile)
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported CSV filename"));
            BatchReadiness readiness = readinessStrategies.assess(type, normalizedFile);
            String status = readiness.ready() ? "QUEUED" : "BLOCKED";
            String checksum = checksum(normalizedFile);
            String requestId = "csv-sha256-" + checksum;
            jdbc.update("""
                    INSERT INTO batch_launch_request(request_id, file_path, checksum, batch_type, status, error)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(request_id) DO UPDATE SET
                        file_path=EXCLUDED.file_path,
                        batch_type=EXCLUDED.batch_type,
                        status=CASE WHEN batch_launch_request.status IN ('FAILED', 'BLOCKED') THEN EXCLUDED.status ELSE batch_launch_request.status END,
                        error=CASE WHEN batch_launch_request.status IN ('FAILED', 'BLOCKED') THEN EXCLUDED.error ELSE batch_launch_request.error END,
                        finished_at=CASE WHEN batch_launch_request.status='FAILED' THEN NULL ELSE batch_launch_request.finished_at END
                    """, requestId, normalizedFile.toString(), checksum, type.key(), status, readiness.reason());
            return status(requestId);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read CSV", exception);
        }
    }

    @Scheduled(fixedDelayString = "${app.launcher.poll-interval}")
    public void processNext() {
        requeueReadyBlockedRequests();
        queuedRequests().stream().min(Comparator.comparingInt((QueuedRequest request) -> request.type().priority())
                .thenComparing(QueuedRequest::createdAt)).ifPresent(this::process);
    }

    public Result status(String requestId) {
        return jdbc.queryForObject("SELECT request_id, status, batch_type, error FROM batch_launch_request WHERE request_id=?",
                (resultSet, rowNumber) -> new Result(resultSet.getString(1), resultSet.getString(2),
                        BatchType.valueOf(resultSet.getString(3).replace('-', '_').toUpperCase()), resultSet.getString(4)), requestId);
    }

    private List<QueuedRequest> queuedRequests() {
        return jdbc.query("SELECT request_id, file_path, batch_type, created_at FROM batch_launch_request WHERE status='QUEUED'",
                (resultSet, rowNumber) -> new QueuedRequest(resultSet.getString(1), Path.of(resultSet.getString(2)),
                        BatchType.valueOf(resultSet.getString(3).replace('-', '_').toUpperCase()), resultSet.getTimestamp(4).toInstant()));
    }

    private void process(QueuedRequest request) {
        int claimedRequests = jdbc.update("UPDATE batch_launch_request SET status='RUNNING', started_at=CURRENT_TIMESTAMP, attempts=attempts+1 WHERE request_id=? AND status='QUEUED'", request.requestId());
        boolean isClaimed = claimedRequests == 1;
        if (!isClaimed) {
            return;
        }
        try {
            boolean completedSuccessfully = executor.execute(request.type(), request.file());
            String completedStatus = completedSuccessfully ? "COMPLETED" : "FAILED";
            Integer exitCode = completedSuccessfully ? 0 : 1;
            jdbc.update("UPDATE batch_launch_request SET status=?, finished_at=CURRENT_TIMESTAMP, exit_code=?, error=NULL WHERE request_id=?",
                    completedStatus, exitCode, request.requestId());
        } catch (RuntimeException exception) {
            jdbc.update("UPDATE batch_launch_request SET status='FAILED', finished_at=CURRENT_TIMESTAMP, error=? WHERE request_id=?",
                    exception.toString(), request.requestId());
        }
    }

    private void requeueReadyBlockedRequests() {
        List<BlockedRequest> blockedRequests = jdbc.query("SELECT request_id, file_path, batch_type FROM batch_launch_request WHERE status='BLOCKED'",
                (resultSet, rowNumber) -> new BlockedRequest(resultSet.getString(1), Path.of(resultSet.getString(2)),
                        BatchType.valueOf(resultSet.getString(3).replace('-', '_').toUpperCase())));
        for (BlockedRequest request : blockedRequests) {
            requeueWhenReady(request);
        }
    }

    private void requeueWhenReady(BlockedRequest request) {
        try {
            BatchReadiness readiness = readinessStrategies.assess(request.type(), request.file());
            boolean isReady = readiness.ready();
            if (isReady) {
                jdbc.update("UPDATE batch_launch_request SET status='QUEUED', error=NULL WHERE request_id=? AND status='BLOCKED'", request.requestId());
                return;
            }
            jdbc.update("UPDATE batch_launch_request SET error=? WHERE request_id=? AND status='BLOCKED'", readiness.reason(), request.requestId());
        } catch (RuntimeException exception) {
            jdbc.update("UPDATE batch_launch_request SET error=? WHERE request_id=? AND status='BLOCKED'", exception.toString(), request.requestId());
        }
    }

    private String checksum(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Result(String requestId, String status, BatchType type, String reason) {}

    private record QueuedRequest(String requestId, Path file, BatchType type, java.time.Instant createdAt) {}

    private record BlockedRequest(String requestId, Path file, BatchType type) {}
}
