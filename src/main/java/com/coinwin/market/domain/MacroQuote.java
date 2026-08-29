package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import java.math.BigDecimal;

/**
 * 비트코인 밖의 자산 하나. 나스닥·금·원유·국채·변동성이 이 모양이다.
 *
 * <p><b>대부분은 바이낸스 안에서 해결된다.</b> 이 거래소는 비-코인 기초자산을 170종 상장해
 * 두었고({@code underlyingSubType: TradFi}) 전부 같은 공개 엔드포인트로 읽힌다. 같은 시계·
 * 같은 형식이라야 <b>나란히 놓는 것이 성립하기</b> 때문에 그것을 기본으로 삼는다.
 *
 * <p><b>셋만 밖에서 온다 — 그리고 그것이 이 타입에 {@code venue} 가 생긴 이유다.</b>
 * 나스닥 선물·S&P 500 지수·달러지수는 바이낸스에 <b>없다</b>. 상장 목록을 값으로 훑어
 * 2만~4만 구간의 USDT 무기한이 하나도 없음을 확인했고, 나스닥은 ETF 셋뿐이며
 * {@code SPXUSDT} 는 0.5달러짜리 코인이지 지수가 아니다. ETF 로 대신하면 값의 크기가
 * 달라지고(QQQ 711 대 나스닥 29,199) 달러지수는 대리물조차 없다.
 *
 * <p>대가는 <b>시계</b>다. 야후에서 오는 셋은 미국 장 시간(선물은 거의 24시간이되 주말은
 * 쉼)에만 움직이므로 24시간 변동률의 뜻이 코인과 같지 않다. <b>그 사실을 지우지 않고
 * {@code venue} 로 들고 다닌다</b> — 화면이 그것을 말해야 한다.
 *
 * <p><b>상관관계를 계산하지 않는다.</b> "나스닥이 오르니 BTC 도 오른다" 는 예측이고
 * {@code scope.md} 가 금지한 것이다. 이 타입이 담는 것은 값과 변동률까지이며, 읽는 것은
 * 사람이다.
 *
 * @param label 사람이 읽는 이름. 심볼({@code QQQUSDT})만으로는 그것이 나스닥인지 알 수 없다
 * @param group 어느 묶음인가. <b>열둘을 한 줄로 늘어놓으면 목록이 되고 목록은 읽히지 않는다</b>
 */
public record MacroQuote(
        MacroTicker ticker,
        String label,
        MacroWatchlist.Group group,
        MacroWatchlist.Venue venue,
        Price last,
        BigDecimal change24hPercent) {

    private static final int PERCENT_SCALE = 6;

    public MacroQuote {
        DomainValues.required(ticker, "표기");
        DomainValues.required(label, "이름");
        DomainValues.required(group, "묶음");
        DomainValues.required(venue, "출처");
        DomainValues.required(last, "현재가");
        change24hPercent = DomainValues.scaled(change24hPercent, PERCENT_SCALE, "24시간 변동률");
    }
}
