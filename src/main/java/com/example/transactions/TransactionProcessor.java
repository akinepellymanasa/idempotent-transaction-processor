package com.example.transactions;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

public class TransactionProcessor {

    private static final Logger LOG =
            Logger.getLogger(TransactionProcessor.class.getName());

    private final Map<String, Transaction> transactions = new HashMap<>();
    private final Map<String, TransactionStatus> statuses = new HashMap<>();
    private final Map<String, BigDecimal> balances = new HashMap<>();
    private final Map<String, Long> lastProcessedSequence = new HashMap<>();
    private final Map<String, TreeMap<Long, Transaction>> pending =
            new HashMap<>();
    private final Map<String, Integer> attempts = new HashMap<>();
    private final Set<String> transientFailures = new java.util.HashSet<>();

    private final int maxAttempts;

    public TransactionProcessor(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public void setOpeningBalance(String accountId, BigDecimal amount) {
        if (accountId == null || accountId.isBlank()
                || amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Invalid account or opening balance");
        }
        balances.put(accountId, amount);
    }

    public void simulateOneTransientFailure(String transactionId) {
        transientFailures.add(transactionId);
    }

    public TransactionResult submit(Transaction tx) {
    String error = validate(tx);

    if (error != null) {
        return new TransactionResult(
                tx == null ? "<null>" : tx.transactionId(),
                TransactionStatus.REJECTED, error, 0);
    }

    Transaction existing = transactions.get(tx.transactionId());

    if (existing != null) {
        if (!sameBusinessRequest(existing, tx)) {
            return new TransactionResult(
                    tx.transactionId(), TransactionStatus.REJECTED,
                    "Transaction ID reused with different data", 0);
        }

        return new TransactionResult(
                tx.transactionId(), TransactionStatus.DUPLICATE,
                "Transaction already registered; no additional effect",
                attempts.getOrDefault(tx.transactionId(), 0));
    }

    long expected =
            lastProcessedSequence.getOrDefault(tx.accountId(), 0L) + 1;

    if (tx.sequenceNumber() < expected) {
        return new TransactionResult(
                tx.transactionId(), TransactionStatus.REJECTED,
                "Stale sequence number", 0);
    }

    if (tx.sequenceNumber() > expected) {
        TreeMap<Long, Transaction> queue =
                pending.computeIfAbsent(tx.accountId(), k -> new TreeMap<>());

        if (queue.containsKey(tx.sequenceNumber())) {
            return new TransactionResult(
                    tx.transactionId(), TransactionStatus.REJECTED,
                    "Another transaction already occupies this sequence", 0);
        }

        transactions.put(tx.transactionId(), tx);
        statuses.put(tx.transactionId(), TransactionStatus.PENDING);
        queue.put(tx.sequenceNumber(), tx);

        return new TransactionResult(
                tx.transactionId(), TransactionStatus.PENDING,
                "Waiting for sequence " + expected, 0);
    }

    transactions.put(tx.transactionId(), tx);
    statuses.put(tx.transactionId(), TransactionStatus.RECEIVED);

    TransactionResult result = process(tx);
    drainPending(tx.accountId());
    return result;
}

private String validate(Transaction tx) {
    if (tx == null) return "Transaction is required";
    if (tx.transactionId() == null || tx.transactionId().isBlank())
        return "transactionId is required";
    if (tx.accountId() == null || tx.accountId().isBlank())
        return "accountId is required";
    if (tx.requestId() == null || tx.requestId().isBlank())
        return "requestId is required";
    if (tx.transactionType() == null)
        return "transactionType is required";
    if (tx.amount() == null || tx.amount().signum() <= 0)
        return "Amount must be positive";
    if (tx.currency() == null || !tx.currency().matches("[A-Z]{3}"))
        return "Currency must be a three-letter uppercase code";
    if (tx.sequenceNumber() < 1)
        return "Sequence must be positive";
    if (tx.eventTime() == null)
        return "eventTime is required";

    return null;
}

private boolean sameBusinessRequest(Transaction a, Transaction b) {
    return a.accountId().equals(b.accountId())
            && a.transactionType() == b.transactionType()
            && a.amount().compareTo(b.amount()) == 0
            && a.currency().equals(b.currency())
            && a.sequenceNumber() == b.sequenceNumber();
}


private TransactionResult process(Transaction tx) {
    String id = tx.transactionId();
    statuses.put(id, TransactionStatus.PROCESSING);

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        attempts.put(id, attempt);

        // Simulate a temporary failure so retry handling can be tested.
        if (transientFailures.remove(id)) {
            LOG.warning("Simulated transient failure for " + id);

            if (attempt < maxAttempts) {
                statuses.put(id, TransactionStatus.RETRY_PENDING);
                continue;
            }

            statuses.put(id, TransactionStatus.FAILED);
            return new TransactionResult(
                    id, TransactionStatus.FAILED,
                    "Retry limit exhausted", attempt);
        }

        BigDecimal current =
                balances.getOrDefault(tx.accountId(), BigDecimal.ZERO);

        if (tx.transactionType() == TransactionType.DEBIT
                && current.compareTo(tx.amount()) < 0) {
            statuses.put(id, TransactionStatus.FAILED);
            return new TransactionResult(
                    id, TransactionStatus.FAILED,
                    "Insufficient funds", attempt);
        }

        BigDecimal updated =
                tx.transactionType() == TransactionType.CREDIT
                        ? current.add(tx.amount())
                        : current.subtract(tx.amount());

        balances.put(tx.accountId(), updated);
        lastProcessedSequence.put(tx.accountId(), tx.sequenceNumber());
        statuses.put(id, TransactionStatus.PROCESSED);

        LOG.info("Processed transaction " + id);

        return new TransactionResult(
                id, TransactionStatus.PROCESSED,
                "Processed successfully", attempt);
    }

    statuses.put(id, TransactionStatus.FAILED);
    return new TransactionResult(
            id, TransactionStatus.FAILED,
            "Processing failed", maxAttempts);
}

private void drainPending(String accountId) {
    TreeMap<Long, Transaction> queue = pending.get(accountId);

    if (queue == null) {
        return;
    }

    while (true) {
        long expected =
                lastProcessedSequence.getOrDefault(accountId, 0L) + 1;

        Transaction next = queue.get(expected);

        if (next == null) {
            break;
        }

        TransactionResult result = process(next);
        queue.remove(expected);

        if (result.status() != TransactionStatus.PROCESSED) {
            break;
        }
    }

    if (queue.isEmpty()) {
        pending.remove(accountId);
    }
}


public BigDecimal getBalance(String accountId) {
    return balances.getOrDefault(accountId, BigDecimal.ZERO);
}

public TransactionStatus getStatus(String transactionId) {
    return statuses.get(transactionId);
}

public Map<TransactionStatus, Long> summary() {
    Map<TransactionStatus, Long> result =
            new EnumMap<>(TransactionStatus.class);

    for (TransactionStatus status : statuses.values()) {
        result.merge(status, 1L, Long::sum);
    }

    return result;
}


}
