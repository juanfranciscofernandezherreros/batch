package com.example.csvstatsplayerbatch.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

class ResultRowTest {

    private static final String CABECERA = "match_id,Player,Team,PTS,REB,AST,MIN,FGM,FGA,2PM,2PA,3PM,3PA,FTM,FTA,+/-,OR,DR,PF,ST,TO,BS,BA,TFS";
    private static final String VALORES = "h81tlrkd,Williams J.,OKC,12,7,2,29:56,5,16,4,14,1,2,1,2,-5,-,7,4,1,-,1,-,3";

    private static List<CSVRecord> leer(String csv) throws IOException {
        return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(csv)).getRecords();
    }

    @Test
    void csvAntiguoDe24ColumnasSinId() throws IOException {
        ResultRow r = ResultRow.parse(leer(CABECERA + "\n" + VALORES + "\n").get(0));
        assertEquals("Williams J.", r.values()[1]);
        assertNull(r.playerId());
    }

    @Test
    void csvNuevoConPlayerUrlSacaElId() throws IOException {
        String csv = CABECERA + ",player_url\n"
                + VALORES + ",https://www.flashscore.com/player/williams-jalen/4G5tbqUi/\n"
                + VALORES.replace(",12,", ",5,") + ",https://www.flashscore.com/player/williams-jaylin/jD1xaPEo/\n";
        List<CSVRecord> filas = leer(csv);
        assertEquals("4G5tbqUi", ResultRow.parse(filas.get(0)).playerId());
        assertEquals("jD1xaPEo", ResultRow.parse(filas.get(1)).playerId());
    }

    @Test
    void enlaceVacioONoDeJugadorNoDaId() {
        assertNull(ResultRow.playerId(""));
        assertNull(ResultRow.playerId("https://www.flashscore.com/team/okc/abc/"));
    }

    @Test
    void cabeceraAdmite24ColumnasOMasPlayerUrl() {
        java.util.ArrayList<String> conUrl = new java.util.ArrayList<>(CsvInput.HEADER);
        conUrl.add("player_url");
        java.util.ArrayList<String> otra = new java.util.ArrayList<>(CsvInput.HEADER);
        otra.add("otra");
        org.junit.jupiter.api.Assertions.assertTrue(CsvInput.cabeceraValida(CsvInput.HEADER));
        org.junit.jupiter.api.Assertions.assertTrue(CsvInput.cabeceraValida(conUrl));
        org.junit.jupiter.api.Assertions.assertFalse(CsvInput.cabeceraValida(otra));
    }

    @Test
    void numeroDeColumnasIncorrectoFalla() throws IOException {
        List<CSVRecord> filas = leer("a,b,c\n1,2,3\n");
        assertThrows(IllegalArgumentException.class, () -> ResultRow.parse(filas.get(0)));
    }
}
