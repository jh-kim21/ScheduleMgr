package com.projectflow.domain;

/**
 * How a Work Package is executed, and therefore how its progress will be counted once the common
 * aggregation lands (Step 5).
 *
 * <p>There is no {@code UNSPECIFIED} constant: an entry that has not been assigned a mode stores
 * {@code null}, which reads as 미지정 and means "keep using the manually entered progress". That
 * is the state every pre-existing row starts in, so introducing execution modes changes no
 * number on any existing screen. A constant would have to be given the same meaning in the same
 * places while also being a value someone could pick from a dropdown.
 */
public enum ExecutionMode {

    /** 산출물과 검토·승인 체크포인트로 관리한다. */
    WATERFALL,

    /** Backlog·Sprint·Board로 관리한다. */
    AGILE,

    /** 반복 실행과 공식 인수가 함께 있는 범위. 두 요소를 사전 합의된 비중으로 합산한다. */
    HYBRID,
}
