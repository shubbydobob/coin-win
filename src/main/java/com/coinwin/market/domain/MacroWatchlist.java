package com.coinwin.market.domain;

import java.util.List;

/**
 * 화면에 놓을 거시 자산.
 *
 * <p><b>다섯이던 것을 열둘로 늘리고 묶었다.</b> 늘린 이유는 "원유가 WTI 하나뿐" 이라는 물음
 * 때문이다 — 벤치마크가 둘인데 하나만 놓으면 그 하나가 유종 전체인 것처럼 읽힌다. 묶은 이유는
 * 그 반대다: 열둘을 한 줄로 늘어놓으면 목록이 되고, <b>목록은 읽히지 않는다.</b>
 *
 * <p><b>바이낸스에 있는 것만 놓는다.</b> 같은 거래소·같은 시계라야 나란히 놓는 것이 성립한다
 * ({@code MacroQuote}). 그 제약이 실제로 무엇을 막는지는 아래에 적어 둔다.
 *
 * <h2>여기 없는 것과 그 이유</h2>
 *
 * <p><b>금리는 하나도 없다.</b> 상장 목록 744종 중 비-코인 172종을 전수로 확인했고
 * {@code TNX}(미 10년) · {@code IRX} · {@code FVX} · {@code TYX} · {@code DXY} · 유로/엔
 * 어느 것도 없다. <b>국채 ETF 둘이 유일한 금리 대리물</b>이며 그마저 레버리지 상품이다.
 *
 * <p><b>유럽·일본 금리는 대리물조차 없다.</b> 이 화면에서 그 둘은 답할 수 없다.
 *
 * <p><b>국채는 이 둘뿐이다.</b> {@code TLT} · {@code IEF} · {@code SHY} 는 미상장이라 만기별로
 * 나눠 볼 수 없다. 대신 방향이 반대인 둘을 함께 놓는다 — <b>같은 사실을 두 번 보는 것이 아니라
 * 서로를 검산한다.</b> 한쪽만 움직이면 그것은 금리가 아니라 그 상품의 사정이다.
 *
 * <p><b>레버리지 배수를 이름에 적는다.</b> {@code TMF} 는 3배, {@code TBT} 는 인버스 2배,
 * {@code UVXY} 는 1.5배다. 배수를 숨기면 "미 장기국채 +0.4%" 가 국채가 0.4% 움직였다는 뜻으로
 * 읽히는데 실제로는 그 3분의 1이다.
 *
 * <h2>비트코인 현물이 여기 있는 이유</h2>
 *
 * <p>나머지 열둘과 달리 이것은 "비트코인 밖" 이 아니다. 그럼에도 이 목록에 있는 이유는
 * <b>같은 자산의 두 가격이 갈라지는 것 자체가 관측값</b>이기 때문이다 — 화면 왼쪽 위의 큰
 * 수는 무기한 선물가이고, 그것이 현물보다 비싸면 롱이 프리미엄을 내고 있다는 뜻이다.
 * 펀딩비와 같은 종류의 사실이고, 같은 이유로 <b>어느 쪽이 유리한가는 말하지 않는다.</b>
 *
 * <p>이것 하나 때문에 {@link Venue} 가 생겼다. 나머지는 전부 무기한이라 어댑터가 경로를
 * 하나만 알면 됐는데, 현물은 호스트와 경로가 둘 다 다르다.
 *
 * <p>근거: {@code docs/spec/market-watch.md} § 6.5.4
 */
public final class MacroWatchlist {

    /**
     * 어느 시장에서 거래되는가. <b>같은 심볼이 두 시장에 다 있어서</b> 이름만으로는 갈리지
     * 않는다 — {@code BTCUSDT} 는 현물에도 무기한에도 있고 값이 다르다.
     */
    public enum Venue {
        /** 무기한 선물. 열둘이 전부 여기다. */
        PERPETUAL,
        /** 현물. */
        SPOT
    }

    /** 무엇으로 묶는가. 순서가 화면 순서다. */
    public enum Group {
        CRYPTO("비트코인"),
        EQUITY("주가"),
        METAL("금속"),
        ENERGY("에너지"),
        RATES("국채"),
        FEAR("공포");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 자산 하나. 화면에 뜨는 이름과 어느 묶음인지, 그리고 어느 시장인지를 함께 갖는다. */
    public record Asset(String symbol, String label, Group group, Venue venue) {
    }

    private static final List<Asset> ASSETS = List.of(
            new Asset("BTCUSDT", "비트코인 현물", Group.CRYPTO, Venue.SPOT),

            new Asset("QQQUSDT", "나스닥 100", Group.EQUITY, Venue.PERPETUAL),
            new Asset("SPYUSDT", "S&P 500", Group.EQUITY, Venue.PERPETUAL),
            new Asset("IWMUSDT", "러셀 2000", Group.EQUITY, Venue.PERPETUAL),

            new Asset("XAUUSDT", "금", Group.METAL, Venue.PERPETUAL),
            new Asset("XAGUSDT", "은", Group.METAL, Venue.PERPETUAL),
            new Asset("COPPERUSDT", "구리", Group.METAL, Venue.PERPETUAL),

            new Asset("CLUSDT", "WTI 원유", Group.ENERGY, Venue.PERPETUAL),
            new Asset("BZUSDT", "브렌트 원유", Group.ENERGY, Venue.PERPETUAL),
            new Asset("NATGASUSDT", "천연가스", Group.ENERGY, Venue.PERPETUAL),

            new Asset("TMFUSDT", "미 장기국채 3배", Group.RATES, Venue.PERPETUAL),
            new Asset("TBTUSDT", "미 장기국채 인버스 2배", Group.RATES, Venue.PERPETUAL),

            new Asset("UVXYUSDT", "변동성 1.5배", Group.FEAR, Venue.PERPETUAL));

    private MacroWatchlist() {
    }

    public static List<Asset> assets() {
        return ASSETS;
    }

    /** 화면 순서를 지킨 목록. */
    public static List<Symbol> ordered() {
        return ASSETS.stream().map(asset -> Symbol.of(asset.symbol())).toList();
    }

    /** 몇 종목을 물었는가. <b>화면이 "몇 개를 못 읽었나" 를 셀 때 쓴다</b> — 상수를 복창하면 갈라진다. */
    public static int size() {
        return ASSETS.size();
    }

    public static String labelOf(Symbol symbol) {
        return find(symbol).map(Asset::label).orElseGet(symbol::value);
    }

    public static Group groupOf(Symbol symbol) {
        return find(symbol).map(Asset::group).orElse(Group.EQUITY);
    }

    /** 어느 시장에서 읽어야 하는가. 목록에 없으면 무기한이다 — 열둘 중 열둘이 그렇다. */
    public static Venue venueOf(Symbol symbol) {
        return find(symbol).map(Asset::venue).orElse(Venue.PERPETUAL);
    }

    private static java.util.Optional<Asset> find(Symbol symbol) {
        return ASSETS.stream().filter(asset -> asset.symbol().equals(symbol.value())).findFirst();
    }
}
