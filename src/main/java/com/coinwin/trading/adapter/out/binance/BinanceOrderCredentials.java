package com.coinwin.trading.adapter.out.binance;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 주문을 낼 수 있는 키. <b>계좌를 읽는 키와 따로 둔다.</b>
 *
 * <p>{@code account} 의 키는 읽기 전용으로 발급해야 하고 이쪽은 거래 권한이 필요하다. 한
 * record 에 섞으면 <b>읽기만 하던 자리가 어느 날 주문을 낼 수 있는 키를 들게 된다</b> —
 * 시크릿이 흐르는 범위는 좁을수록 좋고, 권한이 다른 키는 다른 값이어야 한다.
 *
 * <p><b>출금 권한은 키에서 끈다.</b> 코드에 출금 엔드포인트가 없는 것과 별개로 해야 한다 —
 * 한쪽이 무너져도 다른 쪽이 남는다.
 *
 * <p><b>{@code toString} 을 쓰지 않는다.</b> record 의 기본 구현은 시크릿을 그대로 찍는다.
 *
 * @param apiKey {@code X-MBX-APIKEY} 헤더에 실린다
 * @param secretKey HMAC-SHA256 서명 키. 절대 밖으로 나가지 않는다
 */
@ConfigurationProperties("coinwin.trading.binance")
public record BinanceOrderCredentials(String apiKey, String secretKey) {

    /** 주문을 낼 준비가 됐는가. 둘 중 하나라도 비면 실계좌·테스트넷으로 뜰 수 없다. */
    public boolean isComplete() {
        return apiKey != null && !apiKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    @Override
    public String toString() {
        return "BinanceOrderCredentials[설정됨=%s]".formatted(isComplete());
    }
}
