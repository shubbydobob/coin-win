package com.coinwin.market.adapter.out.binance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 호가를 거래소가 밀어 주게 한다.
 *
 * <p><b>묻지 않고 받는다.</b> REST 로 3초마다 물으면 화면에 뜨는 호가는 최대 3초 전 것이고 그
 * 요청마다 거래소 가중치를 쓴다. 부분 호가 스트림은 100밀리초마다 20단을 보내 주고, 우리 서버는
 * 메모리에 든 마지막 것을 그대로 낸다 — <b>화면이 얼마나 자주 묻든 거래소 한도와 무관해진다.</b>
 *
 * <p><b>잃는 것이 없다.</b> 거래소가 부분 호가로 주는 단수가 정확히 5·10·20 이고 그것이
 * {@link com.coinwin.market.domain.OrderBookDepth} 가 허용하는 값 전부다.
 *
 * <p><b>감시가 하는 일은 "다시 잇기" 하나다.</b> 끊김은 {@code onClose} 로 알 수 있지만 소리
 * 없이 멎는 것은 그렇지 않다 — 같은 호스트의 {@code @ticker} 스트림이 구독을 받아 주고
 * {@code LIST_SUBSCRIPTIONS} 에도 나오면서 <b>한 건도 보내지 않는 것을 확인했다</b>
 * (2026-08-25). 그래서 이 감시는 연결 상태가 아니라 <b>값이 흐르고 있는가</b>를 본다. 끊긴
 * 경우와 멎은 경우에 같은 답을 내므로 두 갈래가 필요 없다.
 *
 * <p><b>스트림이 없어도 기능은 그대로 산다.</b> 못 이으면 어댑터가 REST 로 물러선다. 그래서
 * 실패가 화면을 죽이지 않고, 대신 조용히 사라지지 않도록 다시 잇는 것을 매번 기록한다.
 */
@Component
@ConditionalOnProperty(name = "coinwin.market.binance.stream.enabled", havingValue = "true")
class BinanceDepthStream implements InitializingBean, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(BinanceDepthStream.class);

    private final StreamedOrderBook streamed;

    private final BinanceStreamProperties properties;

    private final Clock clock;

    private final JsonMapper json = new JsonMapper();

    private final HttpClient http;

    private final ScheduledExecutorService watchdog =
            Executors.newSingleThreadScheduledExecutor(BinanceDepthStream::daemon);

    private volatile WebSocket socket;

    BinanceDepthStream(
            StreamedOrderBook streamed, BinanceStreamProperties properties, Clock clock) {
        this.streamed = streamed;
        this.properties = properties;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    /**
     * 감시를 건다. 첫 접속도 이 감시가 한다 — 기동을 손잡기만큼 붙잡아 둘 이유가 없고,
     * 거래소가 지금 응답하지 않는다고 앱이 못 뜨는 것은 더 나쁘다.
     */
    @Override
    public void afterPropertiesSet() {
        long period = properties.reconnectDelay().toMillis();
        watchdog.scheduleWithFixedDelay(this::ensureFlowing, 0, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        watchdog.shutdownNow();
        abort();
        http.close();
    }

    /** 흐르고 있으면 아무것도 하지 않는다. 아니면 끊고 다시 잇는다. */
    private void ensureFlowing() {
        if (streamed.isFlowing()) {
            return;
        }
        abort();
        connect();
    }

    private void connect() {
        URI uri = properties.streamUri();
        try {
            socket = http.newWebSocketBuilder()
                    .buildAsync(uri, new BinanceDepthListener(this::accept))
                    .join();
            LOG.info("호가 스트림에 접속했다: {}", uri);
        } catch (RuntimeException e) {
            LOG.warn("호가 스트림에 접속하지 못했다. {}초 뒤 다시 시도한다 — 그동안 호가는 REST 로 읽는다: {}",
                    properties.reconnectDelay().toSeconds(), uri, e);
        }
    }

    /**
     * 한 메시지를 호가로 옮겨 담는다.
     *
     * <p><b>망가진 메시지 하나가 스트림을 죽이지 않는다.</b> 여기서 던지면 자바 웹소켓이
     * 연결을 끝내고, 그러면 잘못된 호가 한 건 때문에 흐름 전체가 멎는다. 남은 것은 그대로 두고
     * 다음 메시지를 받는다 — 계속 망가지면 값이 낡아 감시가 다시 잇는다.
     */
    private void accept(String message) {
        try {
            BinanceDepth depth = json.readValue(message, BinanceDepth.class);
            streamed.push(depth.toOrderBook(depth.streamedSymbol(), clock.instant()));
        } catch (RuntimeException e) {
            LOG.warn("호가 스트림 메시지를 읽지 못했다. 이 건만 버린다.", e);
        }
    }

    private void abort() {
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.abort();
        }
    }

    private static Thread daemon(Runnable task) {
        Thread thread = new Thread(task, "binance-depth-stream");
        thread.setDaemon(true);
        return thread;
    }
}
