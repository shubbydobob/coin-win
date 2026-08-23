package com.coinwin.market.domain;

/**
 * 이상치를 재는 지표와 <b>그 지표의 표본 정책</b>.
 *
 * <p>표본 수를 여기 두는 이유는 지표마다 주기가 다르기 때문이다 — 펀딩비는 8시간마다 갱신되어
 * 90개가 30일이고, 미결제약정은 5분마다라 30개가 2시간 반이다. 그 수를 서비스에 두면 "왜 90인가"
 * 가 코드에서 사라진다.
 *
 * <p>{@code minimumSamples} 는 <b>이만큼은 있어야 위치를 말한다</b> 는 선이다. 그 아래면
 * {@code MetricHistory} 가 비어 있는 결과를 낸다.
 */
public enum MetricKind {

    /** 8시간 주기. 90개면 30일이다. */
    FUNDING_RATE(90, 30),

    /** 5분 주기. 30개면 2시간 반이다. */
    OPEN_INTEREST(30, 20),

    /** 5분 주기. */
    LONG_SHORT_RATIO(30, 20);

    private final int sampleSize;
    private final int minimumSamples;

    MetricKind(int sampleSize, int minimumSamples) {
        this.sampleSize = sampleSize;
        this.minimumSamples = minimumSamples;
    }

    public int sampleSize() {
        return sampleSize;
    }

    public int minimumSamples() {
        return minimumSamples;
    }
}
