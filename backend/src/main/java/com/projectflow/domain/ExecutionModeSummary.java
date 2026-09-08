package com.projectflow.domain;

/**
 * How the Work Packages beneath a summary node are executed — counted per mode.
 *
 * <p>A summary node has no execution mode of its own (설계 §5: 상위 WBS는 자식들의 실행 방식을
 * 요약 표시하며 별도 실행 실적을 입력하지 않는다), so this is what a summary row shows instead.
 *
 * <p>Counted over <em>all descendant Work Packages</em>, not direct children: a summary whose
 * children are themselves summaries would otherwise report nothing at all.
 *
 * <p>Fixed fields rather than a map keyed by mode, because 미지정 is the absence of a mode and a
 * {@code null} map key serialises badly. {@code unspecified} counts exactly those.
 */
public record ExecutionModeSummary(
        int waterfall,
        int agile,
        int hybrid,
        int unspecified
) {
    public static final ExecutionModeSummary EMPTY = new ExecutionModeSummary(0, 0, 0, 0);

    /** The contribution of a single Work Package with the given mode ({@code null} = 미지정). */
    public static ExecutionModeSummary of(ExecutionMode mode) {
        if (mode == null) {
            return new ExecutionModeSummary(0, 0, 0, 1);
        }
        return switch (mode) {
            case WATERFALL -> new ExecutionModeSummary(1, 0, 0, 0);
            case AGILE -> new ExecutionModeSummary(0, 1, 0, 0);
            case HYBRID -> new ExecutionModeSummary(0, 0, 1, 0);
        };
    }

    public ExecutionModeSummary plus(ExecutionModeSummary other) {
        return new ExecutionModeSummary(
                waterfall + other.waterfall,
                agile + other.agile,
                hybrid + other.hybrid,
                unspecified + other.unspecified
        );
    }

    public int total() {
        return waterfall + agile + hybrid + unspecified;
    }
}
