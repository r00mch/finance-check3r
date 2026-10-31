package com.financetracker.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

    // The balance is computed in the database in one grouped query, not by loading transactions.
    @Query("""
            select new com.financetracker.account.AccountResponse(
                a.id, a.name, a.currency, coalesce(sum(t.amountMinor), 0L))
            from Account a left join Transaction t on t.account = a
            where a.userId = :userId
            group by a.id, a.name, a.currency
            order by a.name""")
    List<AccountResponse> findAllWithBalance(@Param("userId") Long userId);

    @Query("""
            select new com.financetracker.account.AccountResponse(
                a.id, a.name, a.currency, coalesce(sum(t.amountMinor), 0L))
            from Account a left join Transaction t on t.account = a
            where a.userId = :userId and a.id = :id
            group by a.id, a.name, a.currency""")
    Optional<AccountResponse> findOneWithBalance(@Param("id") Long id, @Param("userId") Long userId);
}
