package com.example.transactions;

import java.math.BigDecimal;
import java.time.Instant;

public record Transaction(
        String transactionId,
        String accountId,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        long sequenceNumber,
        String requestId,
        Instant eventTime
) {}
