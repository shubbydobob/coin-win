package com.coinwin.account.adapter.out.binance;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 바이낸스 개인 엔드포인트의 HMAC-SHA256 서명.
 *
 * <p>이 클래스가 하는 일은 하나뿐이고, 그래서 <b>순수 함수</b>다. 시각도 난수도 네트워크도
 * 쓰지 않으므로 거래소 공개 문서의 예제 벡터로 고정할 수 있다 — 서명이 틀리면 401 만 오고
 * 거래소는 무엇이 틀렸는지 말해 주지 않기 때문에, 여기를 독립적으로 증명해 두는 것이 값을 한다.
 *
 * <p><b>질의 문자열을 만들지 않는다.</b> 받은 문자열을 그대로 서명한다. 파라미터 순서가
 * 서명의 일부이므로, 서명하는 쪽이 순서를 다시 정하면 보내는 쪽 순서와 어긋날 수 있다.
 * 만드는 곳과 서명하는 곳이 갈리면 그 불일치는 401 로만 드러난다.
 *
 * <p><b>시크릿은 어디에도 나오지 않는다.</b> {@code toString} 을 두지 않고(record 가 아닌
 * 이유), 예외 메시지에 질의 문자열도 넣지 않는다 — 질의에는 계좌를 특정할 수 있는 값이
 * 섞이고 로그는 시크릿보다 훨씬 넓게 흐른다.
 */
public final class BinanceSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public BinanceSigner(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("바이낸스 시크릿이 비어 있다");
        }
        this.secret = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 질의 문자열의 서명. 소문자 16진수 64자다.
     *
     * @param queryString {@code timestamp} 까지 포함해 <b>보낼 순서 그대로</b>인 질의 문자열
     */
    public String sign(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            throw new IllegalArgumentException("서명할 질의 문자열이 비어 있다");
        }
        return HexFormat.of().formatHex(mac().doFinal(
                queryString.getBytes(StandardCharsets.UTF_8)));
    }

    private Mac mac() {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // 메시지에 원인 객체만 넘긴다. 시크릿도 질의도 넣지 않는다.
            throw new IllegalStateException("HMAC-SHA256 서명을 준비하지 못했다", e);
        }
    }
}
