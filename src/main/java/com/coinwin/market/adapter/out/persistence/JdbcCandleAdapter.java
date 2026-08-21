package com.coinwin.market.adapter.out.persistence;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.application.port.out.SaveCandlesPort;
import com.coinwin.market.application.port.out.StoredCandles;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 캔들을 PostgreSQL 에 담는 어댑터.
 *
 * <p>Hibernate 가 아니라 {@code JdbcTemplate} 을 쓰는 근거는 {@code docs/adr/011} 이다.
 * 요지는 중복 방지를 <b>DB 가 직접</b> 하게 만드는 것이다. JPA 로 저장하면 행마다 select 후
 * insert/update 가 나가고, "중복 없음" 이 Hibernate 의 merge 동작에 대한 신뢰가 된다.
 * {@code ON CONFLICT} 는 기본키가 한 문장 안에서 보장한다.
 *
 * <p>충돌 시 <b>덮어쓴다.</b> 거래소는 아직 닫히지 않은 캔들을 먼저 주고 나중에 정정해서 다시
 * 주므로, 먼저 받은 값을 지키면 틀린 종가가 영구히 남는다.
 */
@Repository
@StoredCandles
public class JdbcCandleAdapter implements LoadCandlesPort, SaveCandlesPort {

    private static final String SELECT_RANGE = """
            SELECT open_time, open_price, high_price, low_price, close_price, volume
              FROM candle
             WHERE symbol = ? AND candle_interval = ?
               AND open_time >= ? AND open_time < ?
             ORDER BY open_time
            """;

    private static final String UPSERT = """
            INSERT INTO candle (symbol, candle_interval, open_time,
                                open_price, high_price, low_price, close_price, volume)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT candle_pk DO UPDATE
                    SET open_price  = EXCLUDED.open_price,
                        high_price  = EXCLUDED.high_price,
                        low_price   = EXCLUDED.low_price,
                        close_price = EXCLUDED.close_price,
                        volume      = EXCLUDED.volume
            """;

    private final JdbcTemplate jdbc;

    public JdbcCandleAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CandleSeries load(CandleQuery query) {
        TimeRange range = query.range();
        return new CandleSeries(jdbc.query(SELECT_RANGE, JdbcCandleAdapter::readCandle,
                query.symbol().value(), query.interval().code(),
                utc(range.from()), utc(range.to())));
    }

    /**
     * {@inheritDoc}
     *
     * <p>새로 저장된 수는 {@code 요청 수 - 이미 있던 수} 다. {@code ON CONFLICT} 자체는
     * 신규와 갱신을 구분해 세어 주지 않기 때문에 저장 전에 한 번 센다. 사용자가 한 명뿐이라
     * 두 문장 사이에 다른 쓰기가 끼어들 여지는 없다.
     */
    @Override
    public int save(Symbol symbol, CandleInterval interval, CandleSeries candles) {
        if (candles.isEmpty()) {
            return 0;
        }
        int alreadyStored = countStored(symbol, interval, candles);
        jdbc.batchUpdate(UPSERT, upsertArguments(symbol, interval, candles));
        return candles.size() - alreadyStored;
    }

    private int countStored(Symbol symbol, CandleInterval interval, CandleSeries candles) {
        List<Object> arguments = new ArrayList<>(List.of(symbol.value(), interval.code()));
        candles.candles().forEach(candle -> arguments.add(utc(candle.openTime())));
        Integer found = jdbc.queryForObject(countSql(candles.size()), Integer.class,
                arguments.toArray());
        return found == null ? 0 : found;
    }

    /** 자리표시자 개수만 캔들 수에 따라 달라진다. 값은 전부 바인딩된다. */
    private static String countSql(int candleCount) {
        return "SELECT count(*) FROM candle WHERE symbol = ? AND candle_interval = ?"
                + " AND open_time IN (" + String.join(",", Collections.nCopies(candleCount, "?"))
                + ")";
    }

    private static List<Object[]> upsertArguments(
            Symbol symbol, CandleInterval interval, CandleSeries candles) {
        return candles.candles().stream()
                .map(candle -> new Object[] {
                        symbol.value(), interval.code(), utc(candle.openTime()),
                        candle.open().value(), candle.high().value(), candle.low().value(),
                        candle.close().value(), candle.volume().value()})
                .toList();
    }

    private static Candle readCandle(ResultSet row, int rowNumber) throws SQLException {
        return new Candle(
                row.getObject("open_time", OffsetDateTime.class).toInstant(),
                Price.of(row.getBigDecimal("open_price")),
                Price.of(row.getBigDecimal("high_price")),
                Price.of(row.getBigDecimal("low_price")),
                Price.of(row.getBigDecimal("close_price")),
                Quantity.of(row.getBigDecimal("volume")));
    }

    /** {@code TIMESTAMPTZ} 에 넘길 값. {@code Timestamp} 는 시간대를 잃어 하루가 밀린다. */
    private static OffsetDateTime utc(Instant moment) {
        return OffsetDateTime.ofInstant(moment, ZoneOffset.UTC);
    }
}
