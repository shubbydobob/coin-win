package com.coinwin.account.adapter.out.binance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.springframework.web.client.RestClient;

/**
 * 같은 질문에 <b>몇 번이든 같은 답</b>을 주는 가짜 거래소.
 *
 * <p>{@code FakeExchange} 는 응답을 줄 세워 하나씩 내보낸다 — 시계 보정이 몇 번 물었는지를
 * 보려고 그렇게 만들어졌다. 계약 스위트는 그 반대가 필요하다. 테스트마다 어댑터에 여러 번
 * 묻는데, 줄 세운 응답이 떨어지면 <b>매핑이 아니라 줄 길이를 검사하게 된다.</b>
 *
 * <p>질의의 {@code symbol} 로 답을 고른다. 거래소가 서버에서 거르는 것과 같다.
 */
final class FakeOpenOrderExchange implements AutoCloseable {

    private final HttpServer server;

    FakeOpenOrderExchange(String btcOrdersJson) {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("페이크 거래소를 열지 못했다", e);
        }
        server.createContext("/fapi/v1/time", exchange ->
                respond(exchange, "{\"serverTime\":%d}".formatted(System.currentTimeMillis())));
        server.createContext("/fapi/v1/openOrders", exchange -> respond(exchange,
                query(exchange).contains("symbol=BTCUSDT") ? btcOrdersJson : "[]"));
        server.start();
    }

    RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static String query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getQuery();
        return raw == null ? "" : raw;
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
