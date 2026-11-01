package com.financetracker.budget;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets")
public class BudgetController {

    private final BudgetService service;

    BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping
    List<BudgetResponse> list() {
        return service.list();
    }

    @GetMapping("/status")
    @Operation(summary = "Spent / remaining / state of every budget for a month, with an end-of-month projection")
    List<BudgetStatus> status(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.status(month);
    }

    @GetMapping("/{id}")
    BudgetResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<BudgetResponse> create(@Valid @RequestBody BudgetRequest request) {
        BudgetResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    BudgetResponse update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
