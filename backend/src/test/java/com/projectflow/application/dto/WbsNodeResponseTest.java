package com.projectflow.application.dto;

import com.projectflow.domain.DelayStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.ProgressBasis;
import com.projectflow.domain.ProgressCalculator.ProgressResult;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsNodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결함 수정 검증: 지연 판정은 저장된(수동) progress가 아니라 공통 집계의 {@code computedProgress}를
 * 우선해야 한다 (CLAUDE.md "지연 판정 설계상 알아둘 점"). Agile Work Package는 저장된 progress가
 * 낡아도 실행 방식 기반 값이 100%면 지연 배지도 완료로 봐야 한다.
 */
class WbsNodeResponseTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 6, 15);

    @Test
    @DisplayName("Agile Work Package는 computedProgress가 100%면 저장된 progress가 낮아도 완료로 본다")
    void usesComputedProgressWhenPresent() {
        // 종료일이 이미 지났으니 저장된 progress(0)만 봤다면 DELAYED다.
        WbsItem item = leaf("개발", ExecutionMode.AGILE, 0,
                REFERENCE.minusDays(20), REFERENCE.minusDays(1));
        WbsNode node = leafNode(item);
        ProgressResult computed = new ProgressResult(100.0, ProgressBasis.AGILE, false, false, null);

        WbsNodeResponse response = WbsNodeResponse.from(node, REFERENCE, Map.of(),
                Map.of(item.getId(), computed));

        assertThat(response.delayStatus()).isEqualTo(DelayStatus.COMPLETED);
        assertThat(response.delayDays()).isZero();
        // computedProgress 필드 자체는 그대로 실제 값을 보여준다.
        assertThat(response.computedProgress()).isEqualTo(100.0);
        // progress(저장값)는 손대지 않는다 — 프론트가 그대로 쓴다.
        assertThat(response.progress()).isZero();
    }

    @Test
    @DisplayName("산정 전(computedProgress == null)이면 저장된 progress로 판정한다")
    void fallsBackToStoredProgressWhenNotEstimable() {
        WbsItem item = leaf("설계", ExecutionMode.AGILE, 50,
                REFERENCE.minusDays(9), REFERENCE.plusDays(10));
        WbsNode node = leafNode(item);
        ProgressResult computed = new ProgressResult(null, ProgressBasis.NOT_ESTIMABLE, false, false,
                "집계 대상 Story·Bug가 없습니다.");

        WbsNodeResponse response = WbsNodeResponse.from(node, REFERENCE, Map.of(),
                Map.of(item.getId(), computed));

        // 20일 계획 중 10일째, 저장된 progress 50%로 판정하면 정확히 기대치에 맞아 ON_TRACK이다.
        assertThat(response.delayStatus()).isEqualTo(DelayStatus.ON_TRACK);
    }

    @Test
    @DisplayName("미지정(MANUAL)은 computedProgress가 저장값과 같으므로 기존과 동일하게 판정한다")
    void manualBasisMatchesPreviousBehaviour() {
        WbsItem item = leaf("기획", null, 30,
                REFERENCE.minusDays(9), REFERENCE.plusDays(10));
        WbsNode node = leafNode(item);
        // MANUAL: computed.percent() == item.getProgress() (ProgressCalculator의 계약).
        ProgressResult computed = new ProgressResult(30.0, ProgressBasis.MANUAL, false, false, null);

        WbsNodeResponse withComputed = WbsNodeResponse.from(node, REFERENCE, Map.of(),
                Map.of(item.getId(), computed));
        WbsNodeResponse withoutComputed = WbsNodeResponse.from(node, REFERENCE, Map.of(), Map.of());

        assertThat(withComputed.delayStatus()).isEqualTo(withoutComputed.delayStatus());
        assertThat(withComputed.progressGap()).isEqualTo(withoutComputed.progressGap());
    }

    @Test
    @DisplayName("실적·예상 종료일이 응답에 실려 왕복된다 — 편집 폼이 값을 몰라 null로 덮어쓰지 않는다")
    void carriesActualAndForecastDatesThrough() {
        WbsItem item = leaf("개발", null, 40, REFERENCE.minusDays(9), REFERENCE.plusDays(10));
        LocalDate actualStart = REFERENCE.minusDays(9);
        LocalDate actualEnd = REFERENCE.minusDays(1);
        LocalDate forecastEnd = REFERENCE.plusDays(15);
        item.restoreActualDates(actualStart, actualEnd, forecastEnd);
        WbsNode node = leafNode(item);

        WbsNodeResponse response = WbsNodeResponse.from(node, REFERENCE, Map.of(), Map.of());

        assertThat(response.actualStartDate()).isEqualTo(actualStart);
        assertThat(response.actualEndDate()).isEqualTo(actualEnd);
        assertThat(response.forecastEndDate()).isEqualTo(forecastEnd);
    }

    @Test
    @DisplayName("실적·예상 종료일을 기록하지 않은 항목은 null로 내려간다")
    void nullWhenNotRecorded() {
        WbsItem item = leaf("설계", null, 0, REFERENCE, REFERENCE.plusDays(5));
        WbsNode node = leafNode(item);

        WbsNodeResponse response = WbsNodeResponse.from(node, REFERENCE, Map.of(), Map.of());

        assertThat(response.actualStartDate()).isNull();
        assertThat(response.actualEndDate()).isNull();
        assertThat(response.forecastEndDate()).isNull();
    }

    private WbsItem leaf(String name, ExecutionMode mode, int progress,
                          LocalDate start, LocalDate end) {
        WbsItem item = new WbsItem(1L, null, name, null, start, end, progress, 0,
                WbsNodeType.WORK_PACKAGE, mode);
        ReflectionTestUtils.setField(item, "id", 1L);
        return item;
    }

    private WbsNode leafNode(WbsItem item) {
        return new WbsNode(item, "1", 1, item.getStartDate(), item.getEndDate(),
                item.getProgress(), null, List.of());
    }
}
