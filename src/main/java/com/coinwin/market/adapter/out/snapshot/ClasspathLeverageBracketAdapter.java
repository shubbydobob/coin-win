package com.coinwin.market.adapter.out.snapshot;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.application.port.out.LoadLeverageBracketsPort;
import com.coinwin.market.domain.LeverageBrackets;
import com.coinwin.market.domain.Symbol;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커밋된 스냅샷 파일에서 레버리지 구간표를 읽는다.
 *
 * <p><b>왜 거래소를 부르지 않는가.</b> {@code /fapi/v1/leverageBracket} 은 공개 엔드포인트가
 * 아니다. 서명 없이 부르면 401 이 돌아온다. API 키를 쓰지 않는다는 {@code scope.md} 의 전제를
 * 지키는 한 자동 갱신 경로가 성립하지 않으므로, 갱신은 이 파일을 교체하는 일이다.
 *
 * <p>BTCUSDT 구간표는 몇 년에 한 번 바뀐다. 그리고 파일이 손상되거나 잘못 편집되면
 * {@link LeverageBrackets} 의 연속성 검사가 불러오는 시점에 잡는다 — 조용히 틀린 청산가가
 * 나가는 경로가 없다.
 */
@Component
public class ClasspathLeverageBracketAdapter implements LoadLeverageBracketsPort {

    private static final String LOCATION_FORMAT = "market/%s-leverage-bracket.json";

    private final JsonMapper json = new JsonMapper();

    @Override
    public LeverageBrackets bracketsFor(Symbol symbol) {
        String location = LOCATION_FORMAT.formatted(symbol.value().toLowerCase(Locale.ROOT));
        try (InputStream snapshot = getClass().getClassLoader().getResourceAsStream(location)) {
            if (snapshot == null) {
                throw new ExternalDataUnavailableException(
                        "레버리지 구간표 스냅샷이 없다: " + location, null);
            }
            return json.readValue(snapshot, LeverageBracketSnapshot.class).toDomain();
        } catch (IOException e) {
            throw new ExternalDataUnavailableException(
                    "레버리지 구간표 스냅샷을 읽지 못했다: " + location, e);
        }
    }
}
