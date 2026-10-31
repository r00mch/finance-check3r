package com.financetracker.transaction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /** Which of the given import fingerprints are already stored for this account. */
    @Query("select t.externalHash from Transaction t "
            + "where t.account.id = :accountId and t.externalHash in :hashes")
    List<String> findExistingHashes(@Param("accountId") Long accountId,
            @Param("hashes") Collection<String> hashes);

    @Query("select t from Transaction t where t.id = :id and t.account.userId = :userId")
    Optional<Transaction> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
