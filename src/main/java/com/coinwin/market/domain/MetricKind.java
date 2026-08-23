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
 *
 * <p><b>변화의 창도 지표가 갖는다.</b> 모든 지표에 "최근 30분" 을 쓰면 펀딩비에서 무의미해진다 —
 * 8시간마다 갱신되므로 30분 안에는 같은 값이 그대로 있다. 근거:
 * {@code docs/spec/market-watch.md} § 6.5.2
 */
public enum MetricKind {

    /** 8시간 주기. 90개면 30일이고, 변화는 최근 3회(하루)로 본다. */
    FUNDING_RATE(90, 30, 3, Change.DIFFERENCE),

    /** 5분 주기. 30개면 2시간 반이고, 변화는 최근 6개(30분)로 본다. */
    OPEN_INTEREST(30, 20, 6, Change.RATIO),

    LONG_SHORT_RATIO(30, 20, 6, Change.RATIO),

    /** 시장가로 때린 쪽의 비율. 호가와 달리 <b>이미 체결된 것</b>이라 취소가 없다. */
    TAKER_RATIO(30, 20, 6, Change.RATIO),

    /** 상위 계정의 롱숏비. <b>포지션 크기 기준</b>이라 "큰손이 어느 쪽에 얼마나" 를 말한다. */
    TOP_POSITION_RATIO(30, 20, 6, Change.RATIO),

    /**
     * 가격. 지표가 아니라 <b>나란히 놓기 위한 기준선</b>이다.
     *
     * <p>미결제약정 −3.2% 는 가격 +1.1% 옆에서만 뜻이 된다 — 포지션이 줄면서 가격이 올랐다면
     * 청산이고, 포지션이 줄면서 가격도 내렸다면 그냥 손을 턴 것이다.
     */
    PRICE(30, 20, 6, Change.RATIO);

    /**
     * 변화를 비율로 말할 것인가 차이로 말할 것인가.
     *
     * <p><b>펀딩비는 부호가 바뀐다.</b> +0.001% 에서 −0.05% 로 갔을 때 비율은 −5000% 이고 그
     * 수는 아무 뜻이 없다. 0 근처에서 무한대로 튀는 것도 배수와 같은 문제다. 그래서 펀딩비만
     * 차이(%p)로 말한다.
     */
    public enum Change {
        RATIO,
        DIFFERENCE
    }

    private final int sampleSize;
    private final int minimumSamples;
    private final int recentWindow;
    private final Change change;

    MetricKind(int sampleSize, int minimumSamples, int recentWindow, Change change) {
        this.sampleSize = sampleSize;
        this.minimumSamples = minimumSamples;
        this.recentWindow = recentWindow;
        this.change = change;
    }

    public int sampleSize() {
        return sampleSize;
    }

    public int minimumSamples() {
        return minimumSamples;
    }

    /** 변화를 재는 창. 표본 <b>마지막</b>에서 이만큼을 본다. */
    public int recentWindow() {
        return recentWindow;
    }

    public Change change() {
        return change;
    }

    /** 화면에 놓이는 지표들. {@link #PRICE} 는 기준선이라 빠진다. */
    public static java.util.List<MetricKind> displayed() {
        return java.util.List.of(
                FUNDING_RATE, OPEN_INTEREST, LONG_SHORT_RATIO, TAKER_RATIO, TOP_POSITION_RATIO);
    }
}
