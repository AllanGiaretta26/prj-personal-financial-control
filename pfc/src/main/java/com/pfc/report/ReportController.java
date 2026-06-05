package com.pfc.report;

import com.pfc.report.dto.AccountBalanceResponse;
import com.pfc.report.dto.BudgetComparisonResponse;
import com.pfc.report.dto.CategorySpendingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/spending")
    @Operation(summary = "Get spending by category for a given month")
    public ResponseEntity<List<CategorySpendingResponse>> spendingByCategory(@RequestParam String month) {
        return ResponseEntity.ok(service.spendingByCategory(month));
    }

    @GetMapping("/budget-comparison")
    @Operation(summary = "Compare budget vs actual spending for a given month")
    public ResponseEntity<List<BudgetComparisonResponse>> budgetComparison(@RequestParam String month) {
        return ResponseEntity.ok(service.budgetComparison(month));
    }

    @GetMapping("/account-balances")
    @Operation(summary = "Get current balance for all accounts")
    public ResponseEntity<List<AccountBalanceResponse>> accountBalances() {
        return ResponseEntity.ok(service.accountBalances());
    }
}
