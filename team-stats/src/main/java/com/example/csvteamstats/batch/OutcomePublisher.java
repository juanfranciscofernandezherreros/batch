package com.example.csvteamstats.batch;

@FunctionalInterface
public interface OutcomePublisher {
    void publish(OutcomeEvent event) throws Exception;
}
