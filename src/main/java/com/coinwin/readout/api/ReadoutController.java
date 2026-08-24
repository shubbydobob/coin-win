package com.coinwin.readout.api;

import com.coinwin.market.domain.Symbol;
import com.coinwin.readout.application.ReadoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지표 판독.
 *
 * <p><b>이 엔드포인트가 생긴 이유는 계산기가 화면에 한 번도 닿은 적이 없었기 때문이다.</b>
 * 일목과 볼린저는 Phase 4 에서 트레이딩뷰 Pine 소스 원문까지 대조해 확정했고 대는 Phase 6 에서
 * 7년 15,110봉으로 검증했는데, 그 둘은 백테스트 안에서만 돌았다 — 매일 화면을 보며 판단하는
 * 사람에게는 없는 것과 같았다.
 *
 * <p><b>추천하지 않는다.</b> 돌려주는 것은 위치와 값까지다. "구름 위이고 지지대에서 0.4% 위"
 * 는 사실이고 "그러니 롱" 은 예측이며, 그 예측은 {@code docs/adr/021} 이 반증했다.
 * 방향은 사람이 정하고 크기는 계획 화면이 낸다.
 */
@Tag(name = "지표 판독", description = "여러 주기에서 지금 가격이 지표상 어디에 서 있나")
@RestController
@RequestMapping("/api/readout")
public class ReadoutController {

    private final ReadoutService readout;

    public ReadoutController(ReadoutService readout) {
        this.readout = readout;
    }

    @Operation(
            summary = "다중 주기 판독",
            description = """
                    15분 · 1시간 · 4시간에서 지금 가격이 일목 구름과 볼린저 밴드의 어디에 있고
                    가장 가까운 지지·저항이 어디인가.

                    **셋을 한 응답으로 낸다.** 따로 부르면 세 응답이 서로 다른 순간의 사실이
                    되는데 화면은 그것을 나란히 놓는다 — 주기가 다른 것과 시점이 다른 것은
                    전혀 다른 문제다.

                    **무엇을 하라고 말하지 않는다.** 여기 있는 것은 전부 관측이다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "15분 · 1시간 · 4시간 순서의 판독 셋"),
        @ApiResponse(responseCode = "400", description = "종목 표기가 올바르지 않다"),
        @ApiResponse(responseCode = "422", description = "지표를 낼 만큼 봉이 모이지 않았다"),
        @ApiResponse(responseCode = "503", description = "거래소에 닿지 못했다")
    })
    @GetMapping("/{symbol}")
    public List<TimeframeReadoutResponse> read(@PathVariable String symbol) {
        return readout.readAll(Symbol.of(symbol)).stream()
                .map(TimeframeReadoutResponse::from)
                .toList();
    }
}
