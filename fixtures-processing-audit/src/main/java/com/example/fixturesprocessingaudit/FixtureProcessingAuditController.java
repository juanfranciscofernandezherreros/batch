package com.example.fixturesprocessingaudit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fixtures-processing-audit")
public class FixtureProcessingAuditController {
    private final FixtureProcessingAuditService audit;

    public FixtureProcessingAuditController(FixtureProcessingAuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/latest")
    public FixtureProcessingAuditService.AuditSnapshot latest() {
        return audit.latest();
    }

    @PostMapping("/run")
    public FixtureProcessingAuditService.AuditSnapshot run() {
        return audit.runAudit();
    }

    /**
     * Registra que la fuente no publica un dataset para un partido (CSV vacio tras los
     * reintentos). La auditoria deja de pedirlo y el orquestador no lo reabre mas.
     */
    @PostMapping("/dataset-unavailable")
    public ResponseEntity<FixtureProcessingAuditService.AuditSnapshot> markUnavailable(
            @RequestBody DatasetUnavailableRequest request) {
        audit.markDatasetUnavailable(request.matchId(), request.dataset(), request.country(), request.competition(),
                request.estado(), request.reason(), request.rows() == null ? 0 : request.rows(),
                request.sourceUrl(), request.attempts() == null ? 1 : request.attempts());
        return ResponseEntity.ok(audit.latest());
    }

    /** Datasets marcados como vacios/no publicados (para el dashboard de Grafana). */
    @GetMapping("/dataset-unavailable")
    public java.util.List<java.util.Map<String, Object>> listUnavailable() {
        return audit.listDatasetsVacios();
    }

    public record DatasetUnavailableRequest(String matchId, String dataset, String country, String competition,
            String estado, String reason, Integer rows, String sourceUrl, Integer attempts) {}
}
