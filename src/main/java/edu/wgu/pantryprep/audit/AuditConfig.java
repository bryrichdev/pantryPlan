package edu.wgu.pantryprep.audit;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Replaces Spring Boot's transaction manager with the auditing one. Boot only
 * creates its own when none is defined, and every @Transactional method uses
 * the bean named transactionManager.
 */
@Configuration
public class AuditConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new AuditingTransactionManager(entityManagerFactory);
    }
}
