
package com.example.transactions;

import java.math.BigDecimal;
import java.time.Instant;

public class Main {

    public static void main(String[] args) {
        TransactionProcessor processor = new TransactionProcessor(3);

        processor.setOpeningBalance("ACC100", new BigDecimal("1000.00"));

        Transaction credit = new Transaction(
                "TXN001",
                "ACC100",
                TransactionType.CREDIT,
                new BigDecimal("250.00"),
                "INR",
                1,
                "REQ001",
                Instant.now()
        );

        Transaction debit = new Transaction(
                "TXN002",
                "ACC100",
                TransactionType.DEBIT,
                new BigDecimal("100.00"),
                "INR",
                2,
                "REQ002",
                Instant.now()
        );

        System.out.println("Credit: " + processor.submit(credit));
        System.out.println("Credit replay: " + processor.submit(credit));
        System.out.println("Debit: " + processor.submit(debit));

        System.out.println(
                "Final balance: " + processor.getBalance("ACC100"));
        System.out.println("Summary: " + processor.summary());
    }
}
