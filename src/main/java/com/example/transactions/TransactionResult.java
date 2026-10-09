package com.example.transactions;

public record TransactionResult(
        String transactionId,
        TransactionStatus status,
        String message,
        int attempts
) {}
