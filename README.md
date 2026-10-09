# Idempotent Transaction Processor

## 1. Overview

This project implements an in-memory transaction processing service in Java. It demonstrates transaction validation, duplicate detection, sequence-based ordering, retry handling, account balance updates, and transaction processing summaries.

The application is designed as a small background-processing service without a user interface or production database.

## 2. Technology Stack

- Java
- Gradle
- JUnit 5
- In-memory data structures
- `BigDecimal` for monetary values

## 3. Key Features

- **Idempotency:** Detects repeated transaction identifiers and prevents the same valid transaction from applying its balance change more than once.
- **Duplicate conflict detection:** Rejects a reused transaction identifier when its business details conflict with the original request.
- **Sequence handling:** Queues transactions that arrive ahead of the next expected sequence and processes them when the missing sequence becomes available.
- **Validation:** Rejects invalid transaction requests according to the implemented validation rules.
- **Retry handling:** Retries simulated transient failures up to a configured maximum number of attempts.
- **Balance management:** Maintains account balances in memory and prevents debits that exceed the available balance.
- **Processing summary:** Reports transaction counts by status.

## 4. Project Structure

```text
idempotent-transaction-processor/
├── src/
│   ├── main/java/com/example/transactions/
│   │   ├── Main.java
│   │   ├── Transaction.java
│   │   ├── TransactionProcessor.java
│   │   ├── TransactionResult.java
│   │   ├── TransactionStatus.java
│   │   └── TransactionType.java
│   └── test/java/com/example/transactions/
│       └── TransactionProcessorTest.java
├── samples/
│   └── sample-output.txt
├── docs/
│   └── design.md
├── build.gradle
├── settings.gradle
└── README.md
```

## 5. Prerequisites

- A compatible Java Development Kit matching the Gradle toolchain configured in `build.gradle`.
- Gradle, or the included Gradle wrapper if available.

## 6. Build and Run

Run the automated tests:

```bash
gradle clean test
```

Run the example application:

```bash
gradle run
```

The example initializes an account with a balance of 1000.00 INR, processes a credit of 250.00 INR, handles a duplicate replay, and processes a debit of 100.00 INR.

Expected final balance:

```text
1150.00
```

The example processes two unique transactions successfully. The duplicate replay must not apply the credit again.

## 7. Testing

The automated test suite covers scenarios including:

- Successful credit processing.
- Duplicate transaction replay.
- Insufficient funds.
- Out-of-order transaction handling.
- Retry after a simulated transient failure.
- Reuse of a transaction identifier with different business data.
- Retry exhaustion.
- Invalid zero-amount transaction.
- Conflicting sequence numbers.
- Reuse of an already-processed sequence number.

Run `gradle clean test` to execute the suite. The test report is generated under `build/reports/tests/test/index.html`.

## 8. Design and Failure Handling

See [`docs/design.md`](docs/design.md) for the architecture, transaction lifecycle, idempotency rules, ordering, retry behavior, and known limitations.

## 9. Sample Output

See [`samples/sample-output.txt`](samples/sample-output.txt) for the example application output. The sample should be kept consistent with the output produced by `gradle run`.

## 10. Limitations and Production Considerations

- State is held in memory and is lost when the application restarts.
- The current implementation is intended as an assessment exercise, not a production-ready financial service.
- Concurrent submissions require appropriate synchronization or per-account locking before the service can safely be used by multiple workers.
- A production implementation should persist transaction records, balances, sequence checkpoints, and retry state.
- Failed transactions and blocked sequences require an operational recovery or dead-letter strategy.
- Production deployment would also require structured logging, monitoring, configuration management, security controls, and recovery testing.

## 11. Development Approach and AI Assistance

AI tools were used as development aids for exploring implementation approaches, identifying test scenarios, and preparing documentation. The code was run and tested, and the design decisions and limitations are documented in this repository. I am responsible for reviewing the solution and explaining its behavior and trade-offs.

## 12. Repository

Source code and assessment artifacts are maintained in this public GitHub repository.
