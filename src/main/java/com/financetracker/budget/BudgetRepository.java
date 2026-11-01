package com.financetracker.budget;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    // join fetch loads the category in the same query, so mapping to a response costs no extra selects
    @Query("select b from Budget b join fetch b.category where b.userId = :userId order by b.category.name, b.currency")
    List<Budget> findAllByUser(@Param("userId") Long userId);

    @Query("select b from Budget b join fetch b.category where b.id = :id and b.userId = :userId")
    Optional<Budget> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByUserIdAndCategory_IdAndCurrency(Long userId, Long categoryId, String currency);

    boolean existsByUserIdAndCategory_IdAndCurrencyAndIdNot(Long userId, Long categoryId, String currency, Long id);
}
