package com.financetracker.importer;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.CurrentUser;
import com.financetracker.common.NotFoundException;
import com.financetracker.transaction.Transaction;
import com.financetracker.transaction.TransactionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportService {

    /** Keeps the IN (...) list well below SQLite's bound-variable limit. */
    private static final int HASH_LOOKUP_CHUNK = 500;

    private final CsvStatementParser parser;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final CurrentUser currentUser;

    ImportService(CsvStatementParser parser, AccountRepository accounts, CategoryRepository categories,
            TransactionRepository transactions, CurrentUser currentUser) {
        this.parser = parser;
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.currentUser = currentUser;
    }

    /** One database transaction: either every valid row is stored or, on a failure, none. */
    @Transactional
    public ImportResult importStatement(Long accountId, byte[] csv) {
        Account account = accounts.findByIdAndUserId(accountId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Account", accountId));
        ParsedStatement parsed = parser.parse(csv);

        List<String> hashes = fingerprints(parsed.rows());
        Set<String> alreadyImported = existingHashes(accountId, hashes);
        Map<String, Category> categoriesByName = categories.findByUserIdOrderByName(currentUser.id()).stream()
                .collect(Collectors.toMap(Category::getName, Function.identity(), (a, b) -> a));

        List<Transaction> toSave = new ArrayList<>();
        int duplicates = 0;
        for (int i = 0; i < parsed.rows().size(); i++) {
            if (alreadyImported.contains(hashes.get(i))) {
                duplicates++;
                continue;
            }
            StatementRow row = parsed.rows().get(i);
            Transaction transaction = new Transaction(account, categoryFor(row.category(), categoriesByName),
                    row.amountMinor(), row.date(), row.description().isEmpty() ? null : row.description());
            transaction.setExternalHash(hashes.get(i));
            toSave.add(transaction);
        }
        transactions.saveAll(toSave);

        return new ImportResult(parsed.rowsRead(), toSave.size(), duplicates, parsed.failed(), parsed.errors());
    }

    /**
     * A row's fingerprint is a hash of date, amount, description and which occurrence of that exact
     * row it is within the file. Counting occurrences matters: two identical coffees bought on the
     * same day are two real transactions, yet re-uploading the file must still be recognised as a
     * duplicate import (the n-th identical row always gets the same fingerprint).
     */
    private static List<String> fingerprints(List<StatementRow> rows) {
        Map<String, Integer> occurrences = new HashMap<>();
        List<String> hashes = new ArrayList<>(rows.size());
        for (StatementRow row : rows) {
            String key = row.date() + "|" + row.amountMinor() + "|" + row.description();
            int nth = occurrences.merge(key, 1, Integer::sum);
            hashes.add(sha256(key + "|" + nth));
        }
        return hashes;
    }

    private Set<String> existingHashes(Long accountId, List<String> hashes) {
        Set<String> existing = new HashSet<>();
        for (int from = 0; from < hashes.size(); from += HASH_LOOKUP_CHUNK) {
            List<String> chunk = hashes.subList(from, Math.min(from + HASH_LOOKUP_CHUNK, hashes.size()));
            existing.addAll(transactions.findExistingHashes(accountId, chunk));
        }
        return existing;
    }

    /** Unknown category names are created on the fly so a first import needs no setup. */
    private Category categoryFor(String name, Map<String, Category> byName) {
        if (name == null) {
            return null;
        }
        return byName.computeIfAbsent(name, n -> categories.save(new Category(currentUser.id(), n)));
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed by the JDK", e);
        }
    }
}
