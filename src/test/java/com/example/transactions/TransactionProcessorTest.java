
package com.example.transactions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TransactionProcessorTest {

    private TransactionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionProcessor(3);
        processor.setOpeningBalance(
                "ACC100", new BigDecimal("1000.00"));
    }

    private Transaction transaction(
            String id,
            TransactionType type,
            String amount,
            long sequence) {

        return new Transaction(
                id,
                "ACC100",
                type,
                new BigDecimal(amount),
                "INR",
                sequence,
                "REQ-" + id,
                Instant.parse("2026-10-09T10:00:00Z")
        );
    }

    @Test
    void creditShouldIncreaseBalance() {
        TransactionResult result = processor.submit(
                transaction("TXN1", TransactionType.CREDIT,
                        "250.00", 1));

        assertEquals(TransactionStatus.PROCESSED, result.status());
        assertEquals(0, new BigDecimal("1250.00")
                .compareTo(processor.getBalance("ACC100")));
    }

    @Test
    void duplicateShouldNotApplyCreditTwice() {
        Transaction tx = transaction(
                "TXN1", TransactionType.CREDIT, "250.00", 1);

        processor.submit(tx);
        TransactionResult duplicate = processor.submit(tx);

        assertEquals(TransactionStatus.DUPLICATE, duplicate.status());
        assertEquals(0, new BigDecimal("1250.00")
                .compareTo(processor.getBalance("ACC100")));
    }

    @Test
    void insufficientFundsShouldNotChangeBalance() {
        TransactionResult result = processor.submit(
                transaction("TXN1", TransactionType.DEBIT,
                        "1500.00", 1));

        assertEquals(TransactionStatus.FAILED, result.status());
        assertEquals(0, new BigDecimal("1000.00")
                .compareTo(processor.getBalance("ACC100")));
    }

    @Test
    void outOfOrderTransactionShouldWaitForMissingSequence() {
        Transaction second = transaction(
                "TXN2", TransactionType.CREDIT, "200.00", 2);

        TransactionResult pending = processor.submit(second);

        assertEquals(TransactionStatus.PENDING, pending.status());
        assertEquals(0, new BigDecimal("1000.00")
                .compareTo(processor.getBalance("ACC100")));

        Transaction first = transaction(
                "TXN1", TransactionType.CREDIT, "100.00", 1);

        TransactionResult processed = processor.submit(first);

        assertEquals(TransactionStatus.PROCESSED, processed.status());
        assertEquals(TransactionStatus.PROCESSED,
                processor.getStatus("TXN2"));
        assertEquals(0, new BigDecimal("1300.00")
                .compareTo(processor.getBalance("ACC100")));
    }

    @Test
    void transientFailureShouldBeRetried() {
        Transaction tx = transaction(
                "TXN1", TransactionType.CREDIT, "250.00", 1);

        processor.simulateOneTransientFailure("TXN1");

        TransactionResult result = processor.submit(tx);

        assertEquals(TransactionStatus.PROCESSED, result.status());
        assertEquals(2, result.attempts());
        assertEquals(0, new BigDecimal("1250.00")
                .compareTo(processor.getBalance("ACC100")));
    }

    
@Test
void reusingTransactionIdWithDifferentAmountShouldBeRejected() {
    Transaction original = transaction(
            "TXN1", TransactionType.CREDIT, "250.00", 1);

    Transaction conflicting = transaction(
            "TXN1", TransactionType.CREDIT, "500.00", 1);

    TransactionResult first = processor.submit(original);
    TransactionResult second = processor.submit(conflicting);

    assertEquals(TransactionStatus.PROCESSED, first.status());
    assertEquals(TransactionStatus.REJECTED, second.status());

    assertEquals(0, new BigDecimal("1250.00")
            .compareTo(processor.getBalance("ACC100")));
}


@Test
void retryExhaustionShouldFailWithoutChangingBalance() {
    TransactionProcessor limitedProcessor = new TransactionProcessor(1);
    limitedProcessor.setOpeningBalance(
            "ACC100", new BigDecimal("1000.00"));

    Transaction tx = transaction(
            "TXN-FAIL", TransactionType.CREDIT, "250.00", 1);

    limitedProcessor.simulateOneTransientFailure("TXN-FAIL");

    TransactionResult result = limitedProcessor.submit(tx);

    assertEquals(TransactionStatus.FAILED, result.status());
    assertEquals(1, result.attempts());
    assertEquals(0, new BigDecimal("1000.00")
            .compareTo(limitedProcessor.getBalance("ACC100")));
}

@Test
void differentTransactionAtSamePendingSequenceShouldBeRejected() {
    Transaction firstPending = transaction(
            "TXN2", TransactionType.CREDIT, "200.00", 2);

    Transaction conflictingPending = transaction(
            "TXN3", TransactionType.CREDIT, "300.00", 2);

    TransactionResult first = processor.submit(firstPending);
    TransactionResult second = processor.submit(conflictingPending);

    assertEquals(TransactionStatus.PENDING, first.status());
    assertEquals(TransactionStatus.REJECTED, second.status());
    assertEquals(0, new BigDecimal("1000.00")
            .compareTo(processor.getBalance("ACC100")));
}


@Test
void zeroAmountShouldBeRejectedWithoutChangingBalance() {
    Transaction invalid = transaction(
            "TXN-ZERO", TransactionType.CREDIT, "0.00", 1);

    TransactionResult result = processor.submit(invalid);

    assertEquals(TransactionStatus.REJECTED, result.status());
    assertEquals(0, new BigDecimal("1000.00")
            .compareTo(processor.getBalance("ACC100")));
}


@Test
void differentTransactionsWithSameSequenceShouldNotBothBeProcessed() {
    Transaction first = transaction(
            "TXN-SEQ-1", TransactionType.CREDIT, "100.00", 1);

    Transaction second = transaction(
            "TXN-SEQ-2", TransactionType.CREDIT, "200.00", 1);

    TransactionResult firstResult = processor.submit(first);
    TransactionResult secondResult = processor.submit(second);

    assertEquals(TransactionStatus.PROCESSED, firstResult.status());
    assertEquals(TransactionStatus.REJECTED, secondResult.status());

    assertEquals(0, new BigDecimal("1100.00")
            .compareTo(processor.getBalance("ACC100")));
}


@Test
void alreadyProcessedSequenceShouldBeRejected() {
    Transaction first = transaction(
            "TXN-STALE-1", TransactionType.CREDIT, "100.00", 1);

    Transaction stale = transaction(
            "TXN-STALE-2", TransactionType.CREDIT, "200.00", 1);

    processor.submit(first);
    TransactionResult result = processor.submit(stale);

    assertEquals(TransactionStatus.REJECTED, result.status());

    assertEquals(0, new BigDecimal("1100.00")
            .compareTo(processor.getBalance("ACC100")));
}


}
