package com.coinwin.watch.adapter.out.binance;

import com.coinwin.watch.application.port.out.LoadNoticesPort;
import com.coinwin.watch.application.port.out.NoticePortContract;
import org.junit.jupiter.api.Tag;
import org.springframework.web.client.RestClient;

/**
 * 바이낸스 공지 어댑터가 인메모리와 같은 계약을 지키는가.
 *
 * <p>{@code crosscheck} 로 분리한 이유가 다른 어댑터보다 강하다 — <b>이 엔드포인트는 문서화된
 * 공개 API 가 아니다.</b> 바이낸스 웹사이트가 쓰는 것이라 예고 없이 바뀔 수 있고, {@code check}
 * 에 넣으면 남의 사정으로 빌드가 빨개진다.
 */
@Tag("crosscheck")
class BinanceNoticeAdapterContractTest extends NoticePortContract {

    @Override
    protected LoadNoticesPort port() {
        return new BinanceNoticeAdapter(RestClient.builder()
                .baseUrl("https://www.binance.com")
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; CoinWin/1.0)")
                .build());
    }
}
