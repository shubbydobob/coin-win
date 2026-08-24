package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 화면에 놓을 거시 자산 목록.
 *
 * <p>여기서 못 박는 것은 <b>목록이 늘어나도 깨지지 않아야 하는 자리들</b>이다. 다섯에서 열둘로
 * 늘리면서 실제로 두 곳이 깨졌다 — 인메모리 어댑터의 상수 배열과 화면의 "다섯 중 몇 개" 뺄셈.
 */
class MacroWatchlistTest {

    @Test
    void 심볼이_겹치지_않는다() {
        List<String> symbols = MacroWatchlist.assets().stream()
                .map(MacroWatchlist.Asset::symbol)
                .toList();

        assertThat(symbols).doesNotHaveDuplicates();
    }

    /**
     * <b>물어본 수를 화면이 스스로 알면 안 된다.</b> 목록이 늘어난 날 그 상수가 조용히
     * 거짓말이 되고, 화면은 "다 읽었다" 고 말하면서 절반을 빠뜨린다.
     */
    @Test
    void 물어본_수는_목록의_길이다() {
        assertThat(MacroWatchlist.size()).isEqualTo(MacroWatchlist.assets().size());
        assertThat(MacroWatchlist.ordered()).hasSize(MacroWatchlist.size());
    }

    @Test
    void 심볼로_이름과_묶음을_찾는다() {
        Symbol brent = Symbol.of("BZUSDT");

        assertThat(MacroWatchlist.labelOf(brent)).isEqualTo("브렌트 원유");
        assertThat(MacroWatchlist.groupOf(brent)).isEqualTo(MacroWatchlist.Group.ENERGY);
    }

    /** 목록에 없는 것을 물으면 심볼 그대로 돌려준다. 화면이 빈 칸을 그리지 않게. */
    @Test
    void 모르는_심볼은_이름_대신_심볼을_준다() {
        assertThat(MacroWatchlist.labelOf(Symbol.of("NOPEUSDT"))).isEqualTo("NOPEUSDT");
        assertThat(MacroWatchlist.groupOf(Symbol.of("NOPEUSDT")))
                .isEqualTo(MacroWatchlist.Group.EQUITY);
    }

    /**
     * <b>같은 묶음은 붙어 있어야 한다.</b> 화면이 서버가 준 차례를 그대로 묶으므로, 목록에서
     * 흩어져 있으면 같은 이름의 묶음이 두 번 그려진다.
     */
    @Test
    void 같은_묶음끼리_이어져_있다() {
        List<MacroWatchlist.Group> 순서 = MacroWatchlist.assets().stream()
                .map(MacroWatchlist.Asset::group)
                .toList();

        assertThat(순서).containsExactlyElementsOf(연속으로_압축(순서));
    }

    /**
     * <b>레버리지 상품은 배수를 이름에 적는다.</b> 숨기면 "미 장기국채 +0.4%" 가 국채가 0.4%
     * 움직였다는 뜻으로 읽히는데 실제로는 그 3분의 1이다.
     */
    @Test
    void 레버리지_상품은_배수를_이름에_갖는다() {
        assertThat(labelOfSymbol("TMFUSDT")).contains("3배");
        assertThat(labelOfSymbol("TBTUSDT")).contains("2배");
        assertThat(labelOfSymbol("UVXYUSDT")).contains("배");
    }

    /** 묶음 이름은 사람이 읽는 말이다. 화면이 enum 이름을 그대로 쓰면 "EQUITY" 가 뜬다. */
    @Test
    void 묶음마다_사람이_읽는_이름이_있다() {
        for (MacroWatchlist.Group group : MacroWatchlist.Group.values()) {
            assertThat(group.label()).isNotBlank().isNotEqualTo(group.name());
        }
    }

    /**
     * <b>비트코인 현물만 다른 시장에서 읽는다.</b> 심볼로 판단하면 갈리지 않는다 —
     * {@code BTCUSDT} 는 현물에도 무기한에도 있고 값이 다르다. 어느 쪽인지는 이 목록만 안다.
     */
    @Test
    void 비트코인_현물만_현물_시장이다() {
        assertThat(MacroWatchlist.venueOf(Symbol.of("BTCUSDT")))
                .isEqualTo(MacroWatchlist.Venue.SPOT);

        assertThat(MacroWatchlist.assets().stream()
                .filter(asset -> asset.venue() == MacroWatchlist.Venue.SPOT)
                .map(MacroWatchlist.Asset::symbol))
                .containsExactly("BTCUSDT");
    }

    /**
     * 목록에 없는 종목은 무기한으로 본다. <b>열둘 중 열둘이 그렇기 때문</b>이고, 여기서 현물을
     * 기본값으로 두면 새 종목을 더할 때 조용히 다른 호스트를 때리게 된다.
     */
    @Test
    void 목록에_없는_종목은_무기한이다() {
        assertThat(MacroWatchlist.venueOf(Symbol.of("ETHUSDT")))
                .isEqualTo(MacroWatchlist.Venue.PERPETUAL);
    }

    private static String labelOfSymbol(String symbol) {
        return MacroWatchlist.labelOf(Symbol.of(symbol));
    }

    /** 이어진 같은 값을 하나로 줄인 뒤 다시 펼친다. 원본과 같으면 흩어져 있지 않은 것이다. */
    private static List<MacroWatchlist.Group> 연속으로_압축(List<MacroWatchlist.Group> 순서) {
        List<MacroWatchlist.Group> 묶음 = new java.util.ArrayList<>();
        for (MacroWatchlist.Group group : 순서) {
            if (묶음.isEmpty() || 묶음.getLast() != group) {
                묶음.add(group);
            }
        }
        return 순서.stream().filter(묶음::contains).toList();
    }
}
