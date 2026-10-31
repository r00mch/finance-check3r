package com.financetracker.account;

import com.financetracker.common.ConflictException;
import com.financetracker.common.CurrentUser;
import com.financetracker.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accounts;
    private final CurrentUser currentUser;

    AccountService(AccountRepository accounts, CurrentUser currentUser) {
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    public List<AccountResponse> list() {
        return accounts.findAllWithBalance(currentUser.id());
    }

    public AccountResponse get(Long id) {
        return accounts.findOneWithBalance(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Account", id));
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        String name = request.name().trim();
        if (accounts.existsByUserIdAndName(currentUser.id(), name)) {
            throw new ConflictException("Account '" + name + "' already exists");
        }
        Account saved = accounts.save(new Account(currentUser.id(), name, request.currency()));
        return new AccountResponse(saved.getId(), saved.getName(), saved.getCurrency(), 0);
    }

    @Transactional
    public AccountResponse update(Long id, AccountRequest request) {
        Account account = find(id);
        String name = request.name().trim();
        if (accounts.existsByUserIdAndNameAndIdNot(currentUser.id(), name, id)) {
            throw new ConflictException("Account '" + name + "' already exists");
        }
        account.setName(name);
        account.setCurrency(request.currency());
        accounts.flush();
        return get(id);
    }

    /** Also removes the account's transactions (ON DELETE CASCADE in the schema). */
    @Transactional
    public void delete(Long id) {
        accounts.delete(find(id));
    }

    private Account find(Long id) {
        return accounts.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Account", id));
    }
}
