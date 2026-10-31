package com.financetracker.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financetracker.common.BadRequestException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Plain unit tests: the parser has no Spring dependencies. */
class CsvStatementParserTest {

    private final CsvStatementParser parser = new CsvStatementParser();

    private ParsedStatement parse(String csv) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesCommaSeparatedIsoStatement() {
        ParsedStatement result = parse("""
                date,amount,description,category
                2025-01-15,-150.50,Coffee beans,Food
                2025-01-16,2000,Refund,
                """);

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).containsExactly(
                new StatementRow(1, LocalDate.of(2025, 1, 15), -15050, "Coffee beans", "Food"),
                new StatementRow(2, LocalDate.of(2025, 1, 16), 200000, "Refund", null));
    }

    @Test
    void parsesRussianBankStyle() {
        ParsedStatement result = parse("""
                Дата;Сумма;Описание;Категория
                03.02.2025;-1 250,50;Пятёрочка;Продукты
                """);

        assertThat(result.rows()).containsExactly(
                new StatementRow(1, LocalDate.of(2025, 2, 3), -125050, "Пятёрочка", "Продукты"));
    }

    @Test
    void detectsTabDelimiter() {
        ParsedStatement result = parse("date\tamount\tdescription\n2025-03-01\t-10\tTea\n");

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).description()).isEqualTo("Tea");
    }

    @Test
    void quotedFieldMayContainTheDelimiter() {
        ParsedStatement result = parse("date,amount,description\n2025-03-01,-10,\"Shop, branch 5\"\n");

        assertThat(result.rows().get(0).description()).isEqualTo("Shop, branch 5");
    }

    @Test
    void toleratesUtf8Bom() {
        ParsedStatement result = parse("﻿date,amount\n2025-03-01,-10\n");

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).description()).isEmpty();
    }

    @Test
    void fallsBackToWindows1251() {
        byte[] bytes = "Дата;Сумма;Описание\n01.02.2025;-100;Кофе\n".getBytes(Charset.forName("windows-1251"));

        ParsedStatement result = parser.parse(bytes);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).description()).isEqualTo("Кофе");
    }

    @Test
    void understandsDifferentAmountFormats() {
        assertThat(CsvStatementParser.parseAmount("-1250.50")).isEqualTo(-125050);
        assertThat(CsvStatementParser.parseAmount("-1250,50")).isEqualTo(-125050);
        assertThat(CsvStatementParser.parseAmount("1 250,5")).isEqualTo(125050);
        assertThat(CsvStatementParser.parseAmount("1,250.50")).isEqualTo(125050);
        assertThat(CsvStatementParser.parseAmount("1.250,50")).isEqualTo(125050);
        assertThat(CsvStatementParser.parseAmount("−5")).isEqualTo(-500);
        assertThat(CsvStatementParser.parseAmount("0.10")).isEqualTo(10);
    }

    @Test
    void badRowsAreReportedAndGoodRowsKept() {
        ParsedStatement result = parse("""
                date,amount,description
                2025-01-01,-10,ok
                31.02.2025,-10,impossible date
                2025-01-03,abc,not a number
                2025-01-04,0,zero
                2025-01-05,-1.005,too precise
                2025-01-06,-20,also ok
                """);

        assertThat(result.rows()).extracting(StatementRow::row).containsExactly(1, 6);
        assertThat(result.failed()).isEqualTo(4);
        assertThat(result.rowsRead()).isEqualTo(6);
        assertThat(result.errors()).extracting(ImportError::row).containsExactly(2, 3, 4, 5);
        assertThat(result.errors().get(0).message()).contains("invalid date");
        assertThat(result.errors().get(3).message()).contains("decimal places");
    }

    @Test
    void shortRowsAreReportedNotCrashed() {
        ParsedStatement result = parse("date,amount,description\n2025-01-01\n");

        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("amount is missing");
    }

    @Test
    void missingRequiredColumnRejectsTheWholeFile() {
        assertThatThrownBy(() -> parse("date,description\n2025-01-01,x\n"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void emptyFileIsRejected() {
        assertThatThrownBy(() -> parse("  \n"))
                .isInstanceOf(BadRequestException.class);
    }
}
