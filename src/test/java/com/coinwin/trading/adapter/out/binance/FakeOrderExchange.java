package com.coinwin.trading.adapter.out.binance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.client.RestClient;

/**
 * 주문을 받아 주는 가짜 거래소.
 *
 * <p>받은 질의 문자열을 그대로 남긴다 — <b>무엇을 보냈는지가 이 어댑터에서 가장 중요한
 * 사실</b>이기 때문이다. 손절을 낸다고 하고 시장가를 보내면 돈이 잘못 움직인다.
 *
 * <p>트리거 주문은 {@code NEW} 와 {@code avgPrice: "0"} 으로, 시장가는 {@code FILLED} 와
 * 실제 체결가로 답한다 — 거래소가 그렇게 답한다.
 */
final class FakeOrderExchange implements AutoCloseable {

    private final HttpServer server;
    private final AtomicLong orderId = new AtomicLong(1000);
    private final List<String> requests = new CopyOnWriteArrayList<>();

    FakeOrderExchange() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("가짜 거래소를 열지 못했다", e);
        }
        server.createContext("/fapi/v1/time", exchange ->
                respond(exchange, "{\"serverTime\":%d}".formatted(System.currentTimeMillis())));
        server.createContext("/fapi/v1/order", this::order);
        server.start();
    }

    RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
    }

    /** 이 어댑터가 실제로 보낸 질의 문자열들. */
    List<String> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void order(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        requests.add(query == null ? "" : query);
        if ("DELETE".equals(exchange.getRequestMethod())) {
            respond(exchange, "{\"orderId\":1,\"status\":\"CANCELED\"}");
            return;
        }
        boolean trigger = query != null && query.contains("stopPrice=");
        respond(exchange, """
                {"orderId":%d,"status":"%s","avgPrice":"%s"}"""
                .formatted(orderId.incrementAndGet(),
                        trigger ? "NEW" : "FILLED",
                        trigger ? "0" : "78015.60"));
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
