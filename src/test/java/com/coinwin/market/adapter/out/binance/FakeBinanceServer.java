package com.coinwin.market.adapter.out.binance;

import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 진짜처럼 행동하는 가짜 바이낸스. 계약 테스트가 거래소 어댑터를 돌리기 위한 것이다.
 *
 * <p>WireMock 같은 라이브러리를 쓰지 않은 이유는, 여기서 필요한 것이 "고정 응답" 이 아니라
 * <b>질의에 반응하는 행동</b>이기 때문이다. {@code startTime}/{@code endTime}/{@code limit} 을
 * 실제로 해석해야 페이지 이어받기와 반열림 구간 변환이 검사된다. 고정 응답을 돌려주는
 * 스텁이었다면 어댑터가 1밀리초를 빼든 말든 테스트가 통과한다.
 *
 * <p>바이낸스 규약을 두 가지 그대로 흉내 낸다.
 *
 * <ul>
 *   <li>{@code startTime} 과 {@code endTime} 은 <b>둘 다 포함</b>이다.
 *   <li>결과는 시간 오름차순이고 {@code limit} 개에서 잘린다.
 * </ul>
 */
final class FakeBinanceServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<CandleInterval, NavigableMap<Instant, Candle>> klines = new HashMap<>();
    private final Map<String, String> cannedBodies = new HashMap<>();

    FakeBinanceServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("페이크 거래소를 열지 못했다", e);
        }
        server.createContext("/fapi/v1/klines", this::handleKlines);
        server.createContext("/", this::handleCanned);
        server.start();
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void registerKlines(CandleInterval interval, CandleSeries candles) {
        NavigableMap<Instant, Candle> byOpenTime =
                klines.computeIfAbsent(interval, key -> new TreeMap<>());
        candles.candles().forEach(candle -> byOpenTime.put(candle.openTime(), candle));
    }

    /** 캔들이 아닌 엔드포인트는 고정 응답으로 충분하다. 질의에 반응할 것이 없기 때문이다. */
    void respondWith(String path, String jsonBody) {
        cannedBodies.put(path, jsonBody);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleKlines(HttpExchange exchange) throws IOException {
        Map<String, String> params = queryParameters(exchange);
        NavigableMap<Instant, Candle> byOpenTime = klines.getOrDefault(
                CandleInterval.ofCode(params.get("interval")), new TreeMap<>());
        List<Candle> selected = byOpenTime.subMap(
                        Instant.ofEpochMilli(Long.parseLong(params.get("startTime"))), true,
                        Instant.ofEpochMilli(Long.parseLong(params.get("endTime"))), true)
                .values().stream()
                .limit(Long.parseLong(params.get("limit")))
                .toList();
        respond(exchange, klinesJson(selected));
    }

    private void handleCanned(HttpExchange exchange) throws IOException {
        String body = cannedBodies.get(exchange.getRequestURI().getPath());
        if (body == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        respond(exchange, body);
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** 자리 6개까지만 채운다. 어댑터가 그 뒤를 읽으면 그것이 곧 버그다. */
    private static String klinesJson(List<Candle> candles) {
        return candles.stream()
                .map(candle -> "[%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,\"0\",0,\"0\",\"0\",\"0\"]"
                        .formatted(candle.openTime().toEpochMilli(),
                                candle.open().value(), candle.high().value(),
                                candle.low().value(), candle.close().value(),
                                candle.volume().value(), candle.openTime().toEpochMilli() + 1))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static Map<String, String> queryParameters(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]), pair -> decode(pair[1])));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
