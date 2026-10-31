package com.financetracker.transaction;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    @Query("select t from Transaction t where t.id = :id and t.account.userId = :userId")
    Optional<Transaction> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
