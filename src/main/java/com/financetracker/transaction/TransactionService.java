package com.financetracker.transaction;

import static com.financetracker.transaction.TransactionSpecs.from;
import static com.financetracker.transaction.TransactionSpecs.inAccount;
import static com.financetracker.transaction.TransactionSpecs.inCategory;
import static com.financetracker.transaction.TransactionSpecs.ownedBy;
import static com.financetracker.transaction.TransactionSpecs.to;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.CurrentUser;
import com.financetracker.common.NotFoundException;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final CurrentUser currentUser;

    TransactionService(TransactionRepository transactions, AccountRepository accounts,
            CategoryRepository categories, CurrentUser currentUser) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.currentUser = currentUser;
    }

    public Page<TransactionResponse> list(Long accountId, Long categoryId, LocalDate from, LocalDate to,
            Pageable pageable) {
        Specification<Transaction> spec = Specification.where(ownedBy(currentUser.id()))
                .and(inAccount(accountId))
                .and(inCategory(categoryId))
                .and(from(from))
                .and(to(to));
        return transactions.findAll(spec, pageable).map(TransactionResponse::from);
    }

    public TransactionResponse get(Long id) {
        return TransactionResponse.from(find(id));
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Transaction transaction = new Transaction(
                ownAccount(request.accountId()), ownCategory(request.categoryId()),
                request.amountMinor(), request.occurredOn(), request.description());
        return TransactionResponse.from(transactions.save(transaction));
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction transaction = find(id);
        transaction.update(
                ownAccount(request.accountId()), ownCategory(request.categoryId()),
                request.amountMinor(), request.occurredOn(), request.description());
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(Long id) {
        transactions.delete(find(id));
    }

    private Transaction find(Long id) {
        return transactions.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Transaction", id));
    }

    // A foreign account/category looks exactly like a missing one, so ids cannot be probed.
    private Account ownAccount(Long accountId) {
        return accounts.findByIdAndUserId(accountId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Account", accountId));
    }

    private Category ownCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findByIdAndUserId(categoryId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Category", categoryId));
    }
}
