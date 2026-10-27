package com.example.csvbatchlauncher;
public record LaunchResponse(String requestId, String status, String batch, String reason) {}
