package com.example.csvstatsplayerbatch.batch;

import org.apache.commons.csv.CSVRecord;

/**
 * Una fila de PLAYER_STATS: las 24 columnas históricas y, si el CSV la trae (columna 25,
 * {@code player_url}), el id de Flashscore del jugador. Ese id distingue a dos jugadores con
 * el mismo nombre abreviado en un partido (Jalen y Jaylin Williams, los dos "Williams J.").
 */
public record ResultRow(long recordNumber, String[] values, String playerId) {

    static final int COLUMNAS = 24;

    static ResultRow parse(CSVRecord r) {
        if (r.size() != COLUMNAS && r.size() != COLUMNAS + 1)
            throw new IllegalArgumentException("PLAYER_STATS requires 24 columns (or 25 with player_url)");
        String[] v = new String[COLUMNAS];
        for (int i = 0; i < COLUMNAS; i++) v[i] = r.get(i).isBlank() || r.get(i).equals("-") ? null : r.get(i);
        if (v[0] == null || v[1] == null || v[2] == null)
            throw new IllegalArgumentException("match_id, Player and Team are required");
        String playerId = r.size() > COLUMNAS ? playerId(r.get(COLUMNAS)) : null;
        return new ResultRow(r.getRecordNumber(), v, playerId);
    }

    /** Último segmento de {@code https://www.flashscore.com/player/<slug>/<id>/}, o null. */
    static String playerId(String url) {
        if (url == null || url.isBlank()) return null;
        String[] partes = url.trim().replaceAll("/+$", "").split("/");
        String id = partes[partes.length - 1];
        return url.contains("/player/") && !id.isBlank() ? id : null;
    }
}
