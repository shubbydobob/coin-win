package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.market.domain.MarketOutliers;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.readout.domain.TimeframeReadout;
import java.util.Optional;

/**
 * 봇이 시장에 대해 <b>읽어낸 것 전부</b> — 지표·대·매물대 · 호가 · 군중의 위치.
 *
 * <p><b>이 타입이 생긴 이유는 봇의 눈이 화면의 눈보다 좁았기 때문이다.</b> 판독 화면은 일목·
 * 볼린저·RSI·MACD·이동평균과 지지저항대·매물대를 보고, 감시 화면은 호가와 펀딩비·미결제약정
 * ·롱숏비율·테이커비율·상위 계정 포지션을 본다. 그런데 봇에게 넘어가던 것은 <b>표시가 하나와
 * 캔들뿐</b>이었다 — 판단을 시키고 싶어도 재료가 안 들어가 있었다.
 *
 * <p><b>셋 다 {@link Optional} 인 것이 이 타입의 핵심이다.</b> 못 읽는 것은 고장이 아니라
 * 정상적으로 일어나는 일이고(워밍업 부족 · 스트림 끊김 · 거래소 응답 실패), 그때 <b>전략은
 * 못 읽었다는 사실을 알아야 한다.</b> 빈 값을 "특별한 일 없음" 으로 읽으면 그것은 관측이
 * 아니라 침묵이며, 침묵 위에서 주문이 나간다.
 *
 * <p>같은 규칙이 이 저장소에 세 번 나왔다 — {@code account} 가 키 없을 때 인메모리로 대신
 * 올리지 않는 것, 환율을 못 얻으면 원화가 통째로 비는 것, 손절 주문을 못 읽었을 때 화면이
 * 그 사실을 적는 것. <b>비어 있는 것과 알 수 없는 것은 다른 사실이다.</b>
 *
 * <p><b>셋이 따로 비는 것도 의도다.</b> 호가를 못 읽었다고 지표까지 버리면 읽은 것을 버리는
 * 것이고, 한 덩어리로 묶으면 하나가 실패할 때 전부가 사라진다. 손절 경고가 포지션 질의와
 * 주문 질의의 실패를 따로 받는 것과 같은 판단이다.
 *
 * <p><b>이 타입은 판단하지 않는다.</b> "좋다/나쁘다" 도 "롱/숏" 도 여기 없다 — 읽은 것을
 * 그대로 들고만 있고, 그것을 무엇으로 바꿀지는 전략의 일이다. 판정이 여기 섞이면 모든 전략이
 * 같은 해석을 강제로 물려받는다.
 *
 * @param readout 한 주기의 판독. 지표 다섯 · 지지저항대 · 매물대 · 피보나치 · ATR.
 *     <b>봉이 모자라면 비어 있다</b> — 일목 워밍업이 가장 길다
 * @param book 호가. 스프레드 · 잔량 · 불균형 · 벽
 * @param outliers 펀딩비 · 미결제약정 · 롱숏비율 · 테이커비율 · <b>상위 계정 포지션</b>이
 *     최근 표본에서 어느 분위인가와 얼마나 변했는가
 */
public record MarketReading(
        Optional<TimeframeReadout> readout,
        Optional<OrderBook> book,
        Optional<MarketOutliers> outliers) {

    public MarketReading {
        DomainValues.required(readout, "판독");
        DomainValues.required(book, "호가");
        DomainValues.required(outliers, "이상치");
    }

    /**
     * 아무것도 못 읽었다. <b>"아무 일도 없다" 가 아니다.</b>
     *
     * <p>이름이 {@code empty} 가 아니라 {@code none} 인 것은 그 구분 때문이다 — 빈 목록은
     * 관측 결과일 수 있지만 이것은 관측이 없었다는 뜻이다.
     */
    public static MarketReading none() {
        return new MarketReading(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** 셋 다 읽혔는가. 셋을 전부 요구하는 전략이 한 번에 물을 자리다. */
    public boolean complete() {
        return readout.isPresent() && book.isPresent() && outliers.isPresent();
    }
}
