package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import java.math.BigDecimal;

/**
 * 비트코인 밖의 자산 하나. 나스닥·금·원유·국채·변동성이 이 모양이다.
 *
 * <p><b>바이낸스 안에서 해결된다.</b> 이 거래소는 비-코인 기초자산을 170종 상장해 두었고
 * (`underlyingSubType: TradFi`) 전부 같은 공개 엔드포인트로 읽힌다. 외부 제공자를 붙이지
 * 않은 이유가 그것이다 — 같은 시계·같은 형식이라야 <b>나란히 놓는 것이 성립한다.</b>
 * 출처가 다르면 "10분 전 나스닥과 지금 BTC" 를 비교하게 된다.
 *
 * <p><b>상관관계를 계산하지 않는다.</b> "나스닥이 오르니 BTC 도 오른다" 는 예측이고
 * {@code scope.md} 가 금지한 것이다. 이 타입이 담는 것은 값과 변동률까지이며, 읽는 것은
 * 사람이다.
 *
 * @param label 사람이 읽는 이름. 심볼({@code QQQUSDT})만으로는 그것이 나스닥인지 알 수 없다
 * @param group 어느 묶음인가. <b>열둘을 한 줄로 늘어놓으면 목록이 되고 목록은 읽히지 않는다</b>
 */
public record MacroQuote(
        Symbol symbol,
        String label,
        MacroWatchlist.Group group,
        Price last,
        BigDecimal change24hPercent) {

    private static final int PERCENT_SCALE = 6;

    public MacroQuote {
        DomainValues.required(symbol, "종목");
        DomainValues.required(label, "이름");
        DomainValues.required(group, "묶음");
        DomainValues.required(last, "현재가");
        change24hPercent = DomainValues.scaled(change24hPercent, PERCENT_SCALE, "24시간 변동률");
    }
}
