package com.example.csvteamstats.batch;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;

class TeamStatsCsvReaderTest {
    @TempDir Path root;

    @Test void preservesRawValuesNullsAndQuotedMultilineFieldsAcrossRestart() throws Exception {
        Path file = csv("m,Q1,Shots,Percentage,Home,\"45,5%\",Away,,\"https://example.test/one\nsecond\"\n"
                + "m,Q2,Shots,Points,Home,16,Away,12,\n");
        var state = new ExecutionContext();
        var reader = reader(file);
        reader.open(state);
        var first = reader.read();
        assertThat(first.homeValue()).isEqualTo("45,5%");
        assertThat(first.awayValue()).isNull();
        assertThat(first.sourceUrl()).contains("\nsecond");
        reader.update(state);
        reader.close();
        var restarted = reader(file);
        restarted.open(state);
        assertThat(restarted.read().period()).isEqualTo("Q2");
        assertThat(restarted.read()).isNull();
        restarted.close();
    }

    @Test void requiredFieldsFailWithRecordAndFieldName() throws Exception {
        var reader = reader(csv("m,Q1,Shots,,Home,1,Away,2,\n"));
        reader.open(new ExecutionContext());
        assertThatThrownBy(reader::read).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recordNumber=1").hasMessageContaining("metric");
        reader.close();
    }

    @Test void rejectsWrongHeaderFilenameRelativePathAndOutsideRoot() throws Exception {
        Path file = csv("m,Q1,Shots,Points,Home,1,Away,2,\n");
        var input = new CsvInput(root.toString());
        assertThatThrownBy(() -> input.validate("stats_all_periods.csv")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CsvInput(root.resolve("other").toString()).validate(file.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        Path wrongName = root.resolve("other.csv");
        Files.copy(file, wrongName);
        assertThatThrownBy(() -> input.validate(wrongName.toString())).hasMessageContaining("TEAM_STATS requires");
        Files.writeString(file, "wrong,header\na,b\n");
        assertThatThrownBy(() -> input.validate(file.toString())).hasMessageContaining("header");
    }

    @Test void rejectsChangedFileBeforeOpeningAndAcceptsCanonicalName() throws Exception {
        var file = csv("m,Q1,Shots,Points,Home,1,Away,2,\n");
        var reader = reader(file);
        Files.writeString(file, Files.readString(file).replace("Home,1", "Home,9"));
        assertThatThrownBy(() -> reader.open(new ExecutionContext())).hasRootCauseMessage("CSV changed since job launch; use an immutable input file");
        var canonical = root.resolve("STATS_ALL_PERIODS_20261014093000_match.csv");
        Files.copy(file, canonical);
        assertThat(new CsvInput(root.toString()).validate(canonical.toString())).isEqualTo(canonical.toRealPath());
    }

    @Test void rejectsOversizeFieldsBeforeDatabaseAndChecksColumnCount() throws Exception {
        var reader = reader(csv("m,Q1,Shots," + "x".repeat(256) + ",Home,1,Away,2,\n"));
        reader.open(new ExecutionContext());
        assertThat(reader.read().rejection()).contains("metric length 256 exceeds 255");
        reader.close();
        var malformed = reader(csv("m,Q1,Shots,Points,Home,1,Away,2,extra,extra\n"));
        malformed.open(new ExecutionContext());
        assertThatThrownBy(malformed::read).hasMessageContaining("column count");
        malformed.close();
    }

    private Path csv(String rows) throws Exception {
        return Files.writeString(root.resolve("stats_all_periods.csv"), String.join(",", CsvInput.HEADER) + "\n" + rows);
    }
    private TeamStatsCsvReader reader(Path file) {
        return new TeamStatsCsvReader(new CsvInput(root.toString()), file.toString(), CsvInput.checksum(file));
    }
}
