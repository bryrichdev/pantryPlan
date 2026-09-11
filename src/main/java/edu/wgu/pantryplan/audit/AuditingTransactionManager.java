package edu.wgu.pantryplan.audit;

import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.security.Impersonation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The normal JPA transaction manager, plus one step: it tells the database who
 * is making the change.
 *
 * <p>The audit triggers in V12 record every row change, but a trigger cannot
 * see the signed-in user. So as each read-write transaction begins, this sets
 * two transaction-local database settings that the trigger reads: the
 * signed-in account's email and, if an admin is viewing as that account, the
 * admin's email. "Transaction-local" means Postgres discards them at commit or
 * rollback, so a pooled connection never carries one request's user into the
 * next.
 *
 * <p>Read-only transactions are skipped. They cannot change anything, and most
 * transactions are read-only, so this keeps the extra statement off them.
 */
public class AuditingTransactionManager extends JpaTransactionManager {

    public AuditingTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        if (definition.isReadOnly()) {
            return;
        }
        EntityManagerHolder holder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Authentication admin = Impersonation.sourceOf(authentication);

        EntityManager entityManager = holder.getEntityManager();
        entityManager.createNativeQuery(
                        "SELECT set_config('pantryplan.actor', ?1, true), "
                                + "set_config('pantryplan.impersonator', ?2, true)")
                .setParameter(1, emailOf(authentication))
                .setParameter(2, emailOf(admin))
                .getSingleResult();
    }

    /** Empty for no one, including the anonymous visitor who is registering. */
    private static String emailOf(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AppUserDetails details
                ? details.getEmail()
                : "";
    }
}
