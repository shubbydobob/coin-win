package com.coinwin.account.adapter.out.binance;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 바이낸스 개인 엔드포인트 자격 증명.
 *
 * <p><b>환경변수에서만 온다.</b> {@code application.yml} 에 값을 적지 않고 기본값도 두지
 * 않는다 — 기본값이 있으면 "설정하지 않았다" 와 "빈 값을 설정했다" 가 구분되지 않는다.
 *
 * <p>{@code BinanceProperties}(공개 엔드포인트 설정)와 <b>따로 둔다.</b> 한 record 에 섞으면
 * 캔들을 읽는 쪽이 시크릿을 들고 다니게 되고, 그 객체가 로그에 찍히는 자리가 늘어난다.
 *
 * <p><b>{@code toString} 을 쓰지 않는다.</b> record 의 기본 {@code toString} 은 시크릿을
 * 그대로 찍는다. Spring 이 설정 진단(actuator, 시작 로그, 예외 메시지)에서 프로퍼티 객체를
 * 출력하는 경로가 여럿이므로 여기서 막는다.
 *
 * @param apiKey {@code X-MBX-APIKEY} 헤더에 실린다
 * @param secretKey HMAC-SHA256 서명 키. 절대 밖으로 나가지 않는다
 */
@ConfigurationProperties("coinwin.account.binance")
public record BinanceCredentials(String apiKey, String secretKey) {

    /** 둘 다 있어야 개인 엔드포인트를 부를 수 있다. */
    public boolean isComplete() {
        return apiKey != null && !apiKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    @Override
    public String toString() {
        return "BinanceCredentials[설정됨=%s]".formatted(isComplete());
    }
}
