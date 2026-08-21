package com.coinwin.indicator.domain;

/**
 * 두 경계에 대한 가격의 위치.
 *
 * <p>일목 구름과 볼린저 밴드가 이 하나를 공유한다. 구름용·밴드용으로 나누면 같은 세 값이
 * 두 벌 생기고, 두 지표를 함께 보는 필터(scope.md 의 매매 전제)에서 변환 코드가 필요해진다.
 */
public enum BandPosition {
    ABOVE,
    INSIDE,
    BELOW
}
