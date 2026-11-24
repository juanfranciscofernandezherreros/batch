package com.example.csvextra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class ExtraCsvImporterTest {

    private static final String H2H = """
            match_id,section,date,competition,home_team,away_team,note,home_score,away_score,result_for_section_team,source_url
            m1,Head-to-head matches,04.10.26,LA,Trieste,Venezia,,82,87,,https://x/?mid=m1
            """;
    private static final String ODDS = """
            match_id,market,period,bookmaker,bookmaker_id,line,home_line,away_line,home_odds,draw_odds,away_odds,over_odds,under_odds,raw_text,source_url
            m1,Home/Away,FT including OT,bet365,605,,,,1.05,,9.00,,,1.05 9.00,https://x/?mid=m1
            """;

    @Test
    void cargaVariosFicherosEnUnSoloArranqueConFilesFrom(@TempDir Path root) throws Exception {
        Path h2h = Files.writeString(root.resolve("H2H_20261020130100_m1.csv"), H2H);
        Path odds = Files.writeString(root.resolve("ODDS_HOME_AWAY_20261020130100_m1.csv"), ODDS);
        Path list = Files.writeString(root.resolve("lista.txt"), h2h + "\n\n" + odds + "\n");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new ExtraCsvImporter(jdbc, root.toString())
                .run(new DefaultApplicationArguments("--files-from=" + list));

        verify(jdbc).batchUpdate(contains("INSERT INTO h2h.h2h"), anyList());
        verify(jdbc).batchUpdate(contains("INSERT INTO odds.odds"), anyList());
    }

    @Test
    void unFicheroMaloNoFrenaALosDemasPeroFallaAlFinal(@TempDir Path root) throws Exception {
        Path good = Files.writeString(root.resolve("H2H_20261020130100_m1.csv"), H2H);
        Path bad = Files.writeString(root.resolve("H2H_20261020130100_m2.csv"), "match_id,section\nm2,x\n");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThatThrownBy(() -> new ExtraCsvImporter(jdbc, root.toString())
                .run(new DefaultApplicationArguments("--file=" + bad, "--file=" + good)))
                .hasMessageContaining("1 of 2");
        verify(jdbc, times(1)).batchUpdate(contains("INSERT INTO h2h.h2h"), anyList());
    }

    @Test
    void elMercadoSeSacaEnteroDelNombre() {
        assertThat(ExtraCsvImporter.marketType("ODDS_HOME_AWAY_20261021150907_0fAJZWz1.csv")).isEqualTo("home_away");
        assertThat(ExtraCsvImporter.marketType("ODDS_ASIAN_HANDICAP_20261021150907_0fAJZWz1.csv")).isEqualTo("asian_handicap");
        assertThat(ExtraCsvImporter.marketType("ODDS_OVER_UNDER_20261021150907_0fAJZWz1.csv")).isEqualTo("over_under");
        assertThat(ExtraCsvImporter.marketType("ODDS_1X2_20261014165803_0bG6tX1t.csv")).isEqualTo("1x2");
    }

    @Test
    void conUnSoloFicheroElErrorSaleTalCual(@TempDir Path root) throws Exception {
        Path bad = Files.writeString(root.resolve("H2H_20261020130100_m2.csv"), "match_id,section\nm2,x\n");

        assertThatThrownBy(() -> new ExtraCsvImporter(mock(JdbcTemplate.class), root.toString())
                .run(new DefaultApplicationArguments("--file=" + bad)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing CSV column");
        assertThat(bad).exists();
    }
}
