package com.coinwin.market.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 화면에 놓을 거시 자산 다섯.
 *
 * <p><b>다섯으로 고정한다.</b> 바이낸스가 170종을 주지만 다 놓으면 화면이 목록이 되고,
 * 목록은 읽히지 않는다. 다섯이 위험자산·안전자산·원자재·금리·공포를 한 줄로 덮는다.
 *
 * <p><b>국내 종목은 넣지 않았다.</b> 삼성전자·SK하이닉스·KODEX200 도 상장돼 있지만, 장 시간이
 * 있는 자산과 24시간 도는 BTC 를 같은 줄에 놓으면 <b>장 마감 뒤의 "변화 없음" 이 사실처럼
 * 읽힌다.</b>
 *
 * <p>근거: {@code docs/spec/market-watch.md} § 6.5.4
 */
public final class MacroWatchlist {

    /** 순서가 화면 순서다. 위험자산에서 공포로 간다. */
    private static final Map<String, String> SYMBOLS = new LinkedHashMap<>();

    static {
        SYMBOLS.put("QQQUSDT", "나스닥 100");
        SYMBOLS.put("XAUUSDT", "금");
        SYMBOLS.put("CLUSDT", "WTI 원유");
        SYMBOLS.put("TMFUSDT", "미 장기국채");
        SYMBOLS.put("UVXYUSDT", "변동성");
    }

    private MacroWatchlist() {
    }

    public static Map<String, String> symbols() {
        return Map.copyOf(SYMBOLS);
    }

    /** 화면 순서를 지킨 목록. {@code Map.copyOf} 는 순서를 보장하지 않으므로 따로 낸다. */
    public static java.util.List<Symbol> ordered() {
        return SYMBOLS.keySet().stream().map(Symbol::of).toList();
    }

    public static String labelOf(Symbol symbol) {
        return SYMBOLS.getOrDefault(symbol.value(), symbol.value());
    }
}
