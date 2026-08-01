package com.labs.train.train_db.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * Enables JDBC savepoint-backed nested transactions ({@code
 * Propagation.NESTED}), which Spring Boot's auto-configured {@code
 * JpaTransactionManager} does not turn on by default.
 *
 * Added specifically for {@code RailwayImportRowService} - see its javadoc.
 * Per-row CSV import failures were cascading into every other row in the
 * same up-to-1000-row batch, because Postgres marks a transaction "aborted"
 * after any error and refuses every later statement until rollback. NESTED
 * gives each row its own savepoint inside the batch's outer transaction, so
 * one row's failure rolls back only that row instead of poisoning the rest
 * of the batch.
 *
 * Postgres (and the pgjdbc driver) supports savepoints, so this is safe to
 * enable project-wide, not just for the import path - it has no effect on
 * any existing REQUIRED-propagation code, which is the overwhelming
 * majority of the codebase.
 */
@Configuration
public class TransactionConfig {

    @Bean
    public JpaTransactionManager transactionManager(
                    @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {

        JpaTransactionManager transactionManager = new JpaTransactionManager(entityManagerFactory);
        transactionManager.setNestedTransactionAllowed(true);

        return transactionManager;
    }
}
