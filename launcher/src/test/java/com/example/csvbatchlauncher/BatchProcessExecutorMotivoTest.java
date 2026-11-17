package com.example.csvbatchlauncher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchProcessExecutorMotivoTest {

    private static final Path LOG = Path.of("batch-logs", "stats-player_X.csv.log");

    @Test
    void usaLaUltimaCausaRaiz() {
        // Salida real (recortada) del fallo de h81tlrkd el 2026-11-17.
        List<String> salida = List.of(
                "2026-11-17T15:51:45 ERROR o.s.batch.core.step.AbstractStep : Encountered an error executing step",
                "org.springframework.jdbc.BadSqlGrammarException: PreparedStatementCallback; bad SQL grammar",
                "Caused by: org.springframework.jdbc.core.AggregatedBatchUpdateException: Batch entry 0 INSERT",
                "Caused by: org.postgresql.util.PSQLException: ERROR: ON CONFLICT DO UPDATE command cannot affect row a second time");
        String motivo = BatchProcessExecutor.motivo(salida, 1, LOG);
        assertTrue(motivo.startsWith("Caused by: org.postgresql.util.PSQLException: ERROR: ON CONFLICT"), motivo);
        assertTrue(motivo.endsWith("(log: " + LOG + ")"));
    }

    @Test
    void sinCausaUsaLaExcepcion() {
        List<String> salida = List.of("arrancando", "java.lang.IllegalArgumentException: Invalid PLAYER_STATS header", "\tat x");
        assertEquals("java.lang.IllegalArgumentException: Invalid PLAYER_STATS header (log: " + LOG + ")",
                BatchProcessExecutor.motivo(salida, 1, LOG));
    }

    @Test
    void sinNadaUtilDaElCodigoDeSalida() {
        assertEquals("exit code 3 (log: " + LOG + ")", BatchProcessExecutor.motivo(List.of("hola"), 3, LOG));
    }

    @Test
    void recortaA500Caracteres() {
        String larga = "Caused by: " + "x".repeat(800);
        assertEquals(500 + (" (log: " + LOG + ")").length(), BatchProcessExecutor.motivo(List.of(larga), 1, LOG).length());
    }
}
