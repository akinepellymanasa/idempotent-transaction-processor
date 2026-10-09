# Design Notes — Idempotent Transaction Processor

## 1. Objective

The objective is to process account transactions while protecting against duplicate submissions, out-of-order arrival, transient processing failures, and invalid requests.

The current implementation uses in-memory storage to keep the assessment simple.

## 2. Components

- **Transaction:** Represents a transaction request, including its identifier, account, type, amount, currency, sequence number, request identifier, and event time.
- **TransactionType:** Defines supported transaction types, such as credit and debit.
- **TransactionStatus:** Represents processing states and outcomes.
- **TransactionResult:** Provides the transaction identifier, result status, message, and attempt count.
- **TransactionProcessor:** Validates requests, detects duplicates, maintains account balances and sequence numbers, handles retries, and creates processing summaries.
- **Main:** Runs the example scenario.
- **TransactionProcessorTest:** Verifies important business rules using automated tests.

## 3. Idempotency

The transaction identifier is used to identify repeated submissions.

When an identifier has already been seen, the processor compares the incoming transaction's business fields with the original transaction. A matching replay is treated as a duplicate; a conflicting request using the same identifier is rejected.

The intended invariant is that replaying a successfully processed transaction must not apply its balance change a second time.

## 4. Sequence and Ordering

The processor tracks the last processed sequence number for each account.

- A transaction with the next expected sequence number can be processed.
- A transaction with a higher sequence number is held in a pending queue.
- When the missing sequence arrives and is processed, the processor attempts to drain the pending queue in order.
- A transaction using an already-processed sequence number is rejected.

A production system must define how sequence gaps are resolved when a transaction permanently fails.

## 5. Validation and Balances

Transactions are validated before processing. The implementation uses `BigDecimal` for monetary arithmetic to avoid binary floating-point rounding errors.

Debits are checked against the available balance. An insufficient-funds transaction must not reduce the balance.

The precise validation rules should remain aligned with the `validate` method in `TransactionProcessor`.

## 6. Retry Strategy

The processor supports a configurable maximum number of attempts and a simulated transient-failure mechanism for testing.

Transient failures are retried within the configured limit. When retries are exhausted, the transaction is marked failed according to the implementation.

A production retry strategy should distinguish transient errors from permanent business failures, use appropriate backoff, and record retry metadata durably.

## 7. Processing Summary

The processor provides a summary of transaction counts by status. This supports a basic view of processed, rejected, pending, and other recorded outcomes.

Duplicate replay responses and stored transaction statuses should be interpreted according to the implementation's status-storage rules.

## 8. Current Limitations

- Data is held in memory and is not durable across restarts.
- The current maps and balance updates are not safe for concurrent access without synchronization.
- A permanently failed transaction may leave a sequence gap that blocks later transactions.
- The transient-failure mechanism is a simulation rather than integration with an external service.
- The implementation does not provide a web API, message broker, or database-backed transaction log.

## 9. Production Hardening

A production version would need:

1. Durable storage and atomic transaction updates.
2. A unique constraint or equivalent protection for transaction identifiers.
3. Concurrency control for balance and sequence updates.
4. A documented recovery policy for missing or permanently failed sequences.
5. Durable retry state and a dead-letter or manual-reconciliation workflow.
6. Structured logs, metrics, alerting, and audit records.
7. Authentication, authorization, and validation of untrusted requests.
8. Integration tests and crash-recovery tests.

## 10. Testing Approach

JUnit tests cover successful processing, duplicate submissions, balance protection, ordering, retry behavior, retry exhaustion, invalid amounts, and sequence conflicts.

Tests should verify both the returned status and the resulting account balance so that a rejected or duplicate transaction cannot silently change financial state.
