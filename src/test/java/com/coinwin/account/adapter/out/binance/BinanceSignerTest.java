package com.coinwin.account.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 서명 규칙.
 *
 * <p>기댓값은 <b>바이낸스 공개 문서의 예제 벡터</b>다. 우리 구현으로 기댓값을 만들면 구현과
 * 같은 실수를 두 번 하게 된다 — Phase 4 의 golden test 와 같은 이유다.
 *
 * <p>출처: Binance USDⓈ-M Futures API, "Signed (TRADE, USER_DATA) Endpoint Examples".
 *
 * <pre>
 * apiSecret  NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j
 * queryString symbol=LTCBTC&amp;side=BUY&amp;type=LIMIT&amp;timeInForce=GTC
 *             &amp;quantity=1&amp;price=0.1&amp;recvWindow=5000&amp;timestamp=1499827319559
 * signature  c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71
 * </pre>
 */
class BinanceSignerTest {

    private static final String DOC_SECRET =
            "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j";

    private static final String DOC_QUERY =
            "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC"
                    + "&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559";

    private static final String DOC_SIGNATURE =
            "c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71";

    @Test
    void 바이낸스_공개_문서의_예제_벡터를_재현한다() {
        assertThat(new BinanceSigner(DOC_SECRET).sign(DOC_QUERY)).isEqualTo(DOC_SIGNATURE);
    }

    /** 소문자 16진수다. 대문자로 내면 거래소가 서명 불일치로 거절한다. */
    @Test
    void 서명은_소문자_16진수_64자다() {
        String signature = new BinanceSigner(DOC_SECRET).sign(DOC_QUERY);

        assertThat(signature).hasSize(64).matches("[0-9a-f]{64}");
    }

    /**
     * 질의 문자열의 <b>순서가 서명의 일부</b>다. 같은 파라미터라도 순서가 다르면 다른 서명이
     * 나온다 — 어댑터가 질의를 만든 순서 그대로 서명해야 하는 이유다.
     */
    @Test
    void 파라미터_순서가_바뀌면_다른_서명이_나온다() {
        BinanceSigner signer = new BinanceSigner(DOC_SECRET);

        assertThat(signer.sign("a=1&b=2")).isNotEqualTo(signer.sign("b=2&a=1"));
    }

    @Test
    void 시크릿이_다르면_다른_서명이_나온다() {
        assertThat(new BinanceSigner(DOC_SECRET).sign(DOC_QUERY))
                .isNotEqualTo(new BinanceSigner(DOC_SECRET + "x").sign(DOC_QUERY));
    }

    /**
     * 빈 시크릿으로는 서명할 수 없다. 조용히 서명해서 보내면 거래소가 401 을 내고, 그 401 의
     * 원인이 "키가 설정되지 않았다" 인지 "키가 틀렸다" 인지 구분되지 않는다.
     */
    @Test
    void 시크릿이_비어_있으면_만들_수_없다() {
        assertThatThrownBy(() -> new BinanceSigner("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BinanceSigner(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 질의가_비어_있으면_서명하지_않는다() {
        assertThatThrownBy(() -> new BinanceSigner(DOC_SECRET).sign(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * <b>예외 메시지에 시크릿도 질의 문자열도 넣지 않는다.</b> 질의에는 계좌를 특정할 수 있는
     * 값이 섞이고, 로그는 시크릿보다 훨씬 넓게 흐른다.
     */
    @Test
    void 실패_메시지에_시크릿이_들어가지_않는다() {
        assertThatThrownBy(() -> new BinanceSigner(DOC_SECRET).sign(""))
                .hasMessageNotContaining(DOC_SECRET);
    }
}
