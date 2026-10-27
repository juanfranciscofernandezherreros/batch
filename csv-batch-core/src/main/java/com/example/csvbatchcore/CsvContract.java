package com.example.csvbatchcore;

import java.util.List;

/** Immutable description used to document and validate a CSV dataset contract. */
public record CsvContract(CsvDataset dataset, String filePattern, String schema, List<String> requiredColumns) {
    public CsvContract {
        requiredColumns = List.copyOf(requiredColumns);
    }
}
