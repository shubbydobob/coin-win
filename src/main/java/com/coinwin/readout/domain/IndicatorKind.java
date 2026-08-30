package com.coinwin.readout.domain;

/**
 * 화면에 서는 지표 다섯.
 *
 * <p><b>이름이 문자열이 아니라 타입인 이유.</b> 이름과 무리가 함께 있어야 세는 쪽이 둘을
 * 다시 짝지을 필요가 없다. 문자열로 두면 {@code "일목"} 이 어느 무리인지 아는 표가 어딘가에
 * 또 생기고, 지표를 하나 더하는 날 그 표만 낡는다 — <b>무리가 빠진 지표는 두 무리를 그리는
 * 화면에서 아무 데도 나타나지 않으므로 없는 것과 같아진다.</b>
 *
 * <p><b>순서가 화면 순서다.</b> {@link StanceReadout} 이 이 순서로 낸다.
 */
public enum IndicatorKind {

    ICHIMOKU("일목", IndicatorFamily.TREND),
    BOLLINGER("볼린저", IndicatorFamily.REVERSION),
    MOVING_AVERAGE("이동평균", IndicatorFamily.TREND),
    RSI("RSI", IndicatorFamily.REVERSION),
    MACD("MACD", IndicatorFamily.TREND);

    private final String label;

    private final IndicatorFamily family;

    IndicatorKind(String label, IndicatorFamily family) {
        this.label = label;
        this.family = family;
    }

    /** 화면에 적히는 이름. */
    public String label() {
        return label;
    }

    /** 어느 무리인가. 합치면 안 되는 둘 중 어느 쪽인지를 말한다. */
    public IndicatorFamily family() {
        return family;
    }
}
