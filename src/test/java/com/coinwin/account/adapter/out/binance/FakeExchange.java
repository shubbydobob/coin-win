package com.coinwin.account.adapter.out.binance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * 응답을 <b>미리 줄 세워 두는</b> 가짜 거래소.
 *
 * <p>{@code market} 쪽 {@code FakeBinanceServer} 를 쓰지 않는 이유는 필요한 것이 다르기
 * 때문이다. 그쪽은 {@code startTime}/{@code limit} 에 <b>반응하는 행동</b>이 필요해서 그렇게
 * 만들어졌고, 여기서 필요한 것은 <b>상태 코드</b>와 <b>호출마다 다른 응답</b>이다 — 시계
 * 보정이 몇 번 물었는지, 실패하면 어떻게 되는지를 봐야 한다.
 *
 * <p>받은 요청 경로를 남긴다. "서명 없는 공개 엔드포인트를 불렀는가" 를 확인할 자리다.
 */
final class FakeExchange implements AutoCloseable {

    private final HttpServer server;
    private final Deque<Response> queued = new ArrayDeque<>();
    private final List<String> requestedPaths = new ArrayList<>();

    FakeExchange() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("페이크 거래소를 열지 못했다", e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    /** 다음 호출이 받을 응답. 줄 세운 순서대로 하나씩 나간다. */
    void enqueue(int status, String body) {
        queued.add(new Response(status, body));
    }

    void enqueueServerTime(long epochMillis) {
        enqueue(200, "{\"serverTime\":%d}".formatted(epochMillis));
    }

    int requestCount() {
        return requestedPaths.size();
    }

    List<String> requestedPaths() {
        return List.copyOf(requestedPaths);
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

    private void handle(HttpExchange exchange) throws IOException {
        requestedPaths.add(exchange.getRequestURI().getPath());
        Response response = queued.isEmpty()
                ? new Response(500, "{\"msg\":\"줄 세운 응답이 없다\"}")
                : queued.poll();
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private record Response(int status, String body) {
    }
}
