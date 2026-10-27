package com.example.csvbatchlauncher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvMatchIdReaderTest {
    private final CsvMatchIdReader reader = new CsvMatchIdReader();

    @Test
    void readsMatchIdFromFirstCsvRecord(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("POINT_BY_POINT_20261014120000_m1.csv");
        Files.writeString(file, "match_id,record_type\nm1,event\n");

        assertEquals("m1", reader.read(file));
    }

    @Test
    void rejectsCsvWithoutMatchIdColumn(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("POINT_BY_POINT_20261014120000_m1.csv");
        Files.writeString(file, "record_type\nevent\n");

        assertThrows(IllegalArgumentException.class, () -> reader.read(file));
    }
}
