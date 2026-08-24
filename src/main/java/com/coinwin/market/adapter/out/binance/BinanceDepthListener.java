package com.coinwin.market.adapter.out.binance;

import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 웹소켓이 주는 조각을 한 메시지로 모아 넘긴다.
 *
 * <p><b>한 메시지가 여러 번에 나뉘어 온다.</b> {@code onText} 는 조각마다 불리고 마지막
 * 조각에서만 {@code last} 가 참이다. 조각을 그대로 파서에 넣으면 잘린 JSON 이라 예외가 나고,
 * 20단짜리 호가는 1KB 가 넘어 실제로 나뉜다.
 *
 * <p><b>{@code request(1)} 을 매번 부른다.</b> 자바 웹소켓은 요구한 만큼만 준다 — 한 번
 * 빠뜨리면 그 자리에서 조용히 멎고, 연결은 살아 있으므로 {@code onClose} 도 오지 않는다.
 *
 * <p>이 클래스는 <b>모으는 일만 한다.</b> 끊김을 알아채고 다시 잇는 것은 {@code
 * BinanceDepthStream} 의 감시가 하며, 그쪽은 소리 없이 멎는 경우까지 함께 다룬다.
 */
final class BinanceDepthListener implements WebSocket.Listener {

    private static final Logger LOG = LoggerFactory.getLogger(BinanceDepthListener.class);

    private final StringBuilder message = new StringBuilder();

    private final Consumer<String> onMessage;

    BinanceDepthListener(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    @Override
    public void onOpen(WebSocket socket) {
        socket.request(1);
    }

    @Override
    public CompletionStage<Void> onText(WebSocket socket, CharSequence data, boolean last) {
        message.append(data);
        if (last) {
            onMessage.accept(message.toString());
            message.setLength(0);
        }
        socket.request(1);
        return null;
    }

    @Override
    public CompletionStage<Void> onClose(WebSocket socket, int code, String reason) {
        LOG.info("호가 스트림이 닫혔다({} {}). 다음 점검에서 다시 잇는다.", code, reason);
        return null;
    }

    @Override
    public void onError(WebSocket socket, Throwable error) {
        LOG.warn("호가 스트림이 끊겼다. 다음 점검에서 다시 잇는다.", error);
    }
}
