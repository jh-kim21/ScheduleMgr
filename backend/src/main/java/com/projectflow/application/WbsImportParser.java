package com.projectflow.application;

import com.projectflow.domain.WbsImportException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads an uploaded WBS file into a flat, validated row list — the tree itself is built afterwards
 * by {@link WbsService#importRows}, which is the only place that needs the project's existing items.
 *
 * <p>Column order is fixed and matches the downloadable template: 레벨 · 업무명 · 시작일 · 종료일 ·
 * 진행률. This is deliberately the same "basic info only" set the WBS edit form always accepted —
 * execution mode, weight and node type are not read from the file so a plain spreadsheet a PM
 * already has stays importable without first learning this app's vocabulary. The first row is
 * always treated as a header and skipped, whatever it says.
 *
 * <p>Everything is validated before anything is returned: a single bad row aborts the whole file
 * with every problem row named, so nothing is ever partially imported (요구사항 - 검증이 삽입보다
 * 앞선다, ImportService와 같은 태도).
 */
public final class WbsImportParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd")
    );

    private WbsImportParser() {
    }

    public static List<WbsImportRow> parse(String filename, byte[] content) {
        List<List<String>> rawRows = isExcel(filename) ? parseExcel(content) : parseCsv(content);
        return toImportRows(rawRows);
    }

    private static boolean isExcel(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private static List<List<String>> parseExcel(byte[] content) {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                int lastCol = Math.max(row.getLastCellNum(), (short) 0);
                for (int c = 0; c < lastCol; c++) {
                    cells.add(cellText(row.getCell(c), formatter));
                }
                rows.add(cells);
            }
            return rows;
        } catch (IOException | RuntimeException e) {
            throw new WbsImportException("엑셀 파일을 읽을 수 없습니다. 올바른 .xlsx/.xls 파일인지 확인하세요.");
        }
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private static List<List<String>> parseCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        // 우리 자신의 CSV 내보내기가 UTF-8 BOM을 붙이므로(csv.ts), 다시 읽어들일 때 걷어낸다.
        // 코드 포인트 비교로 적어, 리터럴 BOM 문자를 소스에 두었다가 편집 중 눈에 안 띄게 사라지는
        // 일을 막는다 (csv.ts가 이스케이프로 적어 둔 것과 같은 이유).
        final int bom = 0xFEFF;
        if (!text.isEmpty() && text.codePointAt(0) == bom) {
            text = text.substring(1);
        }
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(false).build()
                .parse(new StringReader(text))) {
            List<List<String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                List<String> cells = new ArrayList<>();
                record.forEach(cells::add);
                rows.add(cells);
            }
            return rows;
        } catch (IOException e) {
            throw new WbsImportException("CSV 파일을 읽을 수 없습니다.");
        }
    }

    private static List<WbsImportRow> toImportRows(List<List<String>> rawRows) {
        if (rawRows.isEmpty()) {
            throw new WbsImportException("파일에 내용이 없습니다.");
        }

        List<WbsImportRow> result = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int previousLevel = 0;

        // rawRows.get(0)은 머리글이므로 건너뛴다 — 무엇이 적혀 있든 첫 행은 항상 머리글로 본다.
        for (int i = 1; i < rawRows.size(); i++) {
            int rowNumber = i + 1;
            List<String> cells = rawRows.get(i);
            if (isBlank(cells)) {
                continue;
            }

            String name = cellAt(cells, 1);
            if (name.isBlank()) {
                errors.add(rowNumber + "행: 업무명이 비어 있습니다.");
                continue;
            }

            Integer level = parseLevel(cellAt(cells, 0));
            if (level == null) {
                errors.add(rowNumber + "행: 레벨 값 '" + cellAt(cells, 0) + "'을(를) 읽을 수 없습니다. 1 이상의 정수를 입력하세요.");
                continue;
            }
            if (level > previousLevel + 1) {
                errors.add(rowNumber + "행: 레벨이 " + previousLevel + "에서 " + level
                        + "(으)로 두 단계 이상 깊어질 수 없습니다. 한 단계씩만 들어갈 수 있습니다.");
                continue;
            }

            LocalDate startDate = null;
            LocalDate endDate = null;
            String startText = cellAt(cells, 2);
            String endText = cellAt(cells, 3);
            try {
                startDate = parseDate(startText);
            } catch (DateTimeParseException e) {
                errors.add(rowNumber + "행: 시작일 값 '" + startText + "'을(를) 날짜로 읽을 수 없습니다 (예: 2026-09-10).");
            }
            try {
                endDate = parseDate(endText);
            } catch (DateTimeParseException e) {
                errors.add(rowNumber + "행: 종료일 값 '" + endText + "'을(를) 날짜로 읽을 수 없습니다 (예: 2026-09-10).");
            }

            Integer progress = parseProgress(cellAt(cells, 4));
            if (progress == null) {
                errors.add(rowNumber + "행: 진행률 값 '" + cellAt(cells, 4) + "'은(는) 0~100 사이 숫자여야 합니다.");
                continue;
            }

            result.add(new WbsImportRow(rowNumber, level, name, startDate, endDate, progress));
            previousLevel = level;
        }

        if (result.isEmpty() && errors.isEmpty()) {
            errors.add("가져올 업무 행이 없습니다. 머리글 아래에 행을 추가하세요.");
        }
        if (!errors.isEmpty()) {
            throw new WbsImportException(String.join("\n", errors));
        }
        return result;
    }

    private static String cellAt(List<String> cells, int index) {
        if (index >= cells.size()) {
            return "";
        }
        String value = cells.get(index);
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(List<String> cells) {
        return cells.stream().allMatch(c -> c == null || c.isBlank());
    }

    private static Integer parseLevel(String text) {
        if (text.isBlank()) {
            return null;
        }
        try {
            int level = Integer.parseInt(text);
            return level >= 1 ? level : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseProgress(String text) {
        if (text.isBlank()) {
            return 0;
        }
        String cleaned = text.replace("%", "").trim();
        try {
            int value = Integer.parseInt(cleaned);
            return (value >= 0 && value <= 100) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String text) {
        if (text.isBlank()) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new DateTimeParseException("지원하지 않는 날짜 형식입니다.", text, 0);
    }
}
