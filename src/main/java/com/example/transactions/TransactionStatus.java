package com.example.transactions;

public enum TransactionStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    DUPLICATE,
    PENDING,
    RETRY_PENDING,
    FAILED,
    REJECTED
}
