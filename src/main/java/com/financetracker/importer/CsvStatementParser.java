package com.financetracker.importer;

import com.financetracker.common.BadRequestException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Reads a bank statement exported as CSV. Banks disagree on formatting, so the parser is lenient:
 * <ul>
 *   <li>delimiter {@code , ; TAB} is detected from the header line;</li>
 *   <li>UTF-8 (with or without BOM) or, failing that, windows-1251 text;</li>
 *   <li>dates {@code yyyy-MM-dd} or {@code dd.MM.yyyy};</li>
 *   <li>amounts like {@code -1250.50}, {@code -1250,50}, {@code 1 250,50}, {@code 1,250.50};</li>
 *   <li>English or Russian column names (see {@link Column}).</li>
 * </ul>
 * A broken row never aborts the import: it is reported in {@link ParsedStatement#errors()}.
 */
@Component
public class CsvStatementParser {

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    // STRICT: the default SMART mode would silently turn 31.02.2025 into 28.02.2025
    private static final DateTimeFormatter DOTTED_DATE =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_CATEGORY = 100;
    private static final int MAX_REPORTED_ERRORS = 50;

    private enum Column {
        DATE(true, "date", "дата"),
        AMOUNT(true, "amount", "сумма"),
        DESCRIPTION(false, "description", "описание", "назначение"),
        CATEGORY(false, "category", "категория");

        final boolean required;
        final Set<String> aliases;

        Column(boolean required, String... aliases) {
            this.required = required;
            this.aliases = Set.of(aliases);
        }
    }

    public ParsedStatement parse(byte[] content) {
        String text = decode(content);
        if (text.isBlank()) {
            throw new BadRequestException("The file is empty");
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(detectDelimiter(text))
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(true)
                .setTrim(true)
                .get();

        try (CSVParser csv = CSVParser.parse(text, format)) {
            Map<Column, String> headers = resolveHeaders(csv.getHeaderNames());
            List<StatementRow> rows = new ArrayList<>();
            List<ImportError> errors = new ArrayList<>();
            int failed = 0;
            for (CSVRecord record : csv) {
                int row = (int) record.getRecordNumber();
                try {
                    rows.add(parseRow(row, record, headers));
                } catch (RowException e) {
                    failed++;
                    if (errors.size() < MAX_REPORTED_ERRORS) {
                        errors.add(new ImportError(row, e.getMessage()));
                    }
                }
            }
            return new ParsedStatement(rows, errors, failed);
        } catch (IOException | IllegalArgumentException e) {
            throw new BadRequestException("The file is not a valid CSV: " + e.getMessage());
        }
    }

    // ---- file level -----------------------------------------------------------------------

    /** Strict UTF-8 first; bytes that are not valid UTF-8 are assumed to be windows-1251. */
    private static String decode(byte[] content) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            text = new String(content, WINDOWS_1251);
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String header = end < 0 ? text : text.substring(0, end);
        char best = ',';
        long bestCount = count(header, ',');
        for (char candidate : new char[] {';', '\t'}) {
            long n = count(header, candidate);
            if (n > bestCount) {
                best = candidate;
                bestCount = n;
            }
        }
        return best;
    }

    private static long count(String s, char c) {
        return s.chars().filter(ch -> ch == c).count();
    }

    private static Map<Column, String> resolveHeaders(List<String> headerNames) {
        Map<Column, String> resolved = new java.util.EnumMap<>(Column.class);
        for (String name : headerNames) {
            if (name == null) {
                continue;
            }
            String normalised = name.replace("﻿", "").trim().toLowerCase(Locale.ROOT);
            for (Column column : Column.values()) {
                if (column.aliases.contains(normalised)) {
                    resolved.putIfAbsent(column, name);
                }
            }
        }
        for (Column column : Column.values()) {
            if (column.required && !resolved.containsKey(column)) {
                throw new BadRequestException("Missing required column '"
                        + column.name().toLowerCase(Locale.ROOT) + "'. Expected a header row like: "
                        + "date,amount,description,category");
            }
        }
        return resolved;
    }

    // ---- row level ------------------------------------------------------------------------

    private static StatementRow parseRow(int row, CSVRecord record, Map<Column, String> headers) {
        LocalDate date = parseDate(value(record, headers, Column.DATE));
        long amount = parseAmount(value(record, headers, Column.AMOUNT));

        String description = normaliseSpaces(value(record, headers, Column.DESCRIPTION));
        if (description.length() > MAX_DESCRIPTION) {
            throw new RowException("description is longer than " + MAX_DESCRIPTION + " characters");
        }
        String category = normaliseSpaces(value(record, headers, Column.CATEGORY));
        if (category.length() > MAX_CATEGORY) {
            throw new RowException("category is longer than " + MAX_CATEGORY + " characters");
        }
        return new StatementRow(row, date, amount, description, category.isEmpty() ? null : category);
    }

    private static String value(CSVRecord record, Map<Column, String> headers, Column column) {
        String header = headers.get(column);
        return header != null && record.isSet(header) ? record.get(header) : "";
    }

    private static String normaliseSpaces(String s) {
        return s.replace(' ', ' ').trim().replaceAll("\\s+", " ");
    }

    static LocalDate parseDate(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) {
            throw new RowException("date is missing");
        }
        try {
            return s.contains(".") ? LocalDate.parse(s, DOTTED_DATE) : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new RowException("invalid date '" + s + "' (use yyyy-MM-dd or dd.MM.yyyy)");
        }
    }

    /** Converts "-1 250,50" style text to minor units (-125050) without floating point. */
    static long parseAmount(String raw) {
        String s = raw.replace(' ', ' ').replace(" ", "").replace('−', '-');
        if (s.isEmpty()) {
            throw new RowException("amount is missing");
        }
        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            // both present: the later one is the decimal separator, the other groups thousands
            s = lastComma > lastDot ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (lastComma >= 0) {
            s = s.replace(',', '.');
        }
        BigDecimal minor;
        try {
            minor = new BigDecimal(s).movePointRight(2);
        } catch (NumberFormatException e) {
            throw new RowException("invalid amount '" + raw.trim() + "'");
        }
        if (minor.stripTrailingZeros().scale() > 0) {
            throw new RowException("amount '" + raw.trim() + "' has more than 2 decimal places");
        }
        long value;
        try {
            value = minor.longValueExact();
        } catch (ArithmeticException e) {
            throw new RowException("amount '" + raw.trim() + "' is too large");
        }
        if (value == 0) {
            throw new RowException("amount must not be zero");
        }
        return value;
    }

    /** Internal signal for a bad row; turned into an {@link ImportError} by the caller. */
    private static final class RowException extends RuntimeException {

        RowException(String message) {
            super(message, null, false, false); // no stack trace: these are expected, frequent, cheap
        }
    }
}
