package com.projectflow.application;

import com.projectflow.domain.WbsImportException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link WbsImportParser} turns an uploaded file into a flat, validated row list before
 * {@code WbsService.importRows} ever sees it — so everything about column reading, header
 * skipping and row-level validation belongs here, not in a service-level test.
 */
class WbsImportParserTest {

    @Nested
    @DisplayName("CSV")
    class Csv {

        @Test
        @DisplayName("여러 레벨의 들여쓰기 구조를 트리로 읽는다")
        void parsesLevels() {
            String csv = """
                    레벨,업무명,시작일,종료일,진행률
                    1,설계,2026-01-01,2026-01-10,100
                    2,화면 설계,2026-01-01,2026-01-05,100
                    3,로그인 화면,2026-01-01,2026-01-03,50
                    2,DB 설계,2026-01-06,2026-01-10,0
                    1,개발,2026-01-11,2026-01-20,0
                    """;

            List<WbsImportRow> rows = WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8));

            assertThat(rows).extracting(WbsImportRow::level, WbsImportRow::name)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(1, "설계"),
                            org.assertj.core.groups.Tuple.tuple(2, "화면 설계"),
                            org.assertj.core.groups.Tuple.tuple(3, "로그인 화면"),
                            org.assertj.core.groups.Tuple.tuple(2, "DB 설계"),
                            org.assertj.core.groups.Tuple.tuple(1, "개발"));
            assertThat(rows.get(0).startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(rows.get(0).endDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        }

        @Test
        @DisplayName("첫 행은 무엇이 적혀 있든 머리글로 보고 건너뛴다")
        void alwaysSkipsFirstRowAsHeader() {
            String csv = """
                    1,이것도 머리글처럼 건너뛴다,,,
                    1,실제 업무,2026-01-01,2026-01-02,0
                    """;

            List<WbsImportRow> rows = WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8));

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).name()).isEqualTo("실제 업무");
        }

        @Test
        @DisplayName("BOM이 붙은 파일도 첫 셀이 깨지지 않고 읽힌다")
        void stripsLeadingBom() {
            String csv = "﻿레벨,업무명,시작일,종료일,진행률\n1,업무,2026-01-01,2026-01-02,0\n";

            List<WbsImportRow> rows = WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8));

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).name()).isEqualTo("업무");
        }

        @Test
        @DisplayName("레벨이 직전 행보다 두 단계 이상 깊어지면 거부한다")
        void rejectsLevelJump() {
            String csv = """
                    레벨,업무명,시작일,종료일,진행률
                    1,설계,2026-01-01,2026-01-10,0
                    3,잘못된 깊이,2026-01-01,2026-01-02,0
                    """;

            assertThatThrownBy(() -> WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(WbsImportException.class)
                    .hasMessageContaining("3행")
                    .hasMessageContaining("두 단계");
        }

        @Test
        @DisplayName("업무명이 비어 있으면 거부한다")
        void rejectsBlankName() {
            String csv = """
                    레벨,업무명,시작일,종료일,진행률
                    1,,2026-01-01,2026-01-10,0
                    """;

            assertThatThrownBy(() -> WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(WbsImportException.class)
                    .hasMessageContaining("2행")
                    .hasMessageContaining("업무명");
        }

        @Test
        @DisplayName("날짜 형식이 잘못되면 거부한다")
        void rejectsBadDate() {
            String csv = """
                    레벨,업무명,시작일,종료일,진행률
                    1,업무,2026-13-40,2026-01-10,0
                    """;

            assertThatThrownBy(() -> WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(WbsImportException.class)
                    .hasMessageContaining("2행")
                    .hasMessageContaining("시작일");
        }

        @Test
        @DisplayName("진행률이 0~100 범위를 벗어나면 거부한다")
        void rejectsOutOfRangeProgress() {
            String csv = """
                    레벨,업무명,시작일,종료일,진행률
                    1,업무,2026-01-01,2026-01-10,150
                    """;

            assertThatThrownBy(() -> WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(WbsImportException.class)
                    .hasMessageContaining("2행")
                    .hasMessageContaining("진행률");
        }

        @Test
        @DisplayName("여러 행에 문제가 있으면 전부 모아 거부하고 아무것도 반환하지 않는다")
        void collectsAllErrors() {
            String csv = """
                    레벨,업무명,시작일,종료일,진행률
                    1,정상 업무,2026-01-01,2026-01-10,0
                    1,,2026-01-01,2026-01-10,0
                    1,업무2,잘못된날짜,2026-01-10,0
                    1,업무3,2026-01-01,2026-01-10,200
                    """;

            assertThatThrownBy(() -> WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(WbsImportException.class)
                    .satisfies(e -> {
                        String message = e.getMessage();
                        assertThat(message).contains("3행");
                        assertThat(message).contains("4행");
                        assertThat(message).contains("5행");
                        // 세 개의 오류가 줄바꿈으로 구분된 별도 항목이어야 한다.
                        assertThat(message.split("\n")).hasSize(3);
                    });
        }

        @Test
        @DisplayName("빈 행은 건너뛰고 나머지를 읽는다")
        void skipsBlankLines() {
            String csv = "레벨,업무명,시작일,종료일,진행률\n1,업무,2026-01-01,2026-01-10,0\n,,,,\n1,업무2,2026-01-11,2026-01-20,0\n";

            List<WbsImportRow> rows = WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8));

            assertThat(rows).hasSize(2);
        }

        @Test
        @DisplayName("머리글만 있고 데이터 행이 없으면 거부한다")
        void rejectsHeaderOnlyFile() {
            String csv = "레벨,업무명,시작일,종료일,진행률\n";

            assertThatThrownBy(() -> WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(WbsImportException.class);
        }

        @Test
        @DisplayName("진행률을 비워 두면 0으로 취급한다")
        void blankProgressDefaultsToZero() {
            String csv = """
                    레벨,업무명,시작일,종료일,진행률
                    1,업무,2026-01-01,2026-01-10,
                    """;

            List<WbsImportRow> rows = WbsImportParser.parse("plan.csv", csv.getBytes(StandardCharsets.UTF_8));

            assertThat(rows.get(0).progress()).isZero();
        }
    }

    @Nested
    @DisplayName("Excel")
    class Excel {

        @Test
        @DisplayName("날짜 서식 셀을 LocalDate로 정확히 읽는다")
        void readsDateFormattedCells() throws IOException {
            byte[] content = buildWorkbook(workbook -> {
                Sheet sheet = workbook.createSheet();
                CreationHelper helper = workbook.getCreationHelper();
                org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

                writeHeader(sheet);
                Row row = sheet.createRow(1);
                row.createCell(0).setCellValue(1);
                row.createCell(1).setCellValue("업무");
                Cell start = row.createCell(2);
                start.setCellValue(LocalDate.of(2026, 3, 15));
                start.setCellStyle(dateStyle);
                Cell end = row.createCell(3);
                end.setCellValue(LocalDate.of(2026, 3, 20));
                end.setCellStyle(dateStyle);
                row.createCell(4).setCellValue(50);
            });

            List<WbsImportRow> rows = WbsImportParser.parse("plan.xlsx", content);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).startDate()).isEqualTo(LocalDate.of(2026, 3, 15));
            assertThat(rows.get(0).endDate()).isEqualTo(LocalDate.of(2026, 3, 20));
            assertThat(rows.get(0).progress()).isEqualTo(50);
        }

        @Test
        @DisplayName("여러 레벨 구조를 읽는다")
        void parsesLevels() throws IOException {
            byte[] content = buildWorkbook(workbook -> {
                Sheet sheet = workbook.createSheet();
                writeHeader(sheet);
                writeRow(sheet, 1, 1, "설계", "2026-01-01", "2026-01-10", 0);
                writeRow(sheet, 2, 2, "화면 설계", "2026-01-01", "2026-01-05", 0);
            });

            List<WbsImportRow> rows = WbsImportParser.parse("plan.xlsx", content);

            assertThat(rows).extracting(WbsImportRow::level, WbsImportRow::name)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(1, "설계"),
                            org.assertj.core.groups.Tuple.tuple(2, "화면 설계"));
        }

        @Test
        @DisplayName("손상된 파일은 읽을 수 없다는 메시지로 거부한다")
        void rejectsCorruptFile() {
            byte[] garbage = "이것은 엑셀 파일이 아닙니다".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> WbsImportParser.parse("plan.xlsx", garbage))
                    .isInstanceOf(WbsImportException.class)
                    .hasMessageContaining("엑셀");
        }

        private void writeHeader(Sheet sheet) {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("레벨");
            header.createCell(1).setCellValue("업무명");
            header.createCell(2).setCellValue("시작일");
            header.createCell(3).setCellValue("종료일");
            header.createCell(4).setCellValue("진행률");
        }

        private void writeRow(Sheet sheet, int rowIndex, int level, String name,
                               String start, String end, int progress) {
            Row row = sheet.createRow(rowIndex);
            row.createCell(0).setCellValue(level);
            row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(start);
            row.createCell(3).setCellValue(end);
            row.createCell(4).setCellValue(progress);
        }

        private byte[] buildWorkbook(java.util.function.Consumer<XSSFWorkbook> writer) throws IOException {
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                writer.accept(workbook);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }
}
