package com.coinwin.account.adapter.in.web;

import com.coinwin.account.application.port.in.ReconcilePositionsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 거래소 계정과 기록을 맞춰 보는 엔드포인트.
 *
 * <p><b>읽기만 한다.</b> 주문을 내는 엔드포인트는 여기에 없고 앞으로도 없다 —
 * {@code CLAUDE.md} 의 금지 항목이고 키도 읽기 전용으로 발급해야 한다({@code scope.md}).
 *
 * <p><b>불일치를 자동으로 고치지 않는다.</b> 기록을 거래소에 맞춰 수정하는 엔드포인트를 두면
 * "적기를 빠뜨렸다" 는 사실 자체가 사라진다. 알려 주기만 하고 고치는 것은 사람이 기록
 * 화면에서 한다.
 */
@RestController
@RequestMapping("/api/account")
@Tag(name = "거래소 계정", description = "기록과 거래소를 대조한다. 읽기 전용이며 주문을 내지 않는다")
public class AccountController {

    private final ReconcilePositionsUseCase reconcile;

    public AccountController(ReconcilePositionsUseCase reconcile) {
        this.reconcile = reconcile;
    }

    @Operation(
            summary = "기록과 거래소 포지션 대조",
            description = """
                    기록의 미청산 거래와 거래소의 실제 포지션을 방향으로 맞춰 본다.

                    거래소 값으로 기록을 덮어쓰지 않는다. 기록은 내가 무엇을 하려 했는지를 알고
                    거래소는 지금 무엇이 열려 있는지를 안다 — 어느 쪽도 다른 쪽을 대체하지 못한다.

                    가장 잡고 싶은 것은 RECORDED_ONLY 다. 손절이 체결됐는데 청산을 적지 않으면
                    그 상태가 되고, 그동안 집계는 그 거래를 없는 것으로 센다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "방향별 대조 결과"),
        @ApiResponse(responseCode = "503",
                description = "거래소 계정이 연결되지 않았거나 거래소를 읽지 못했다. "
                        + "COINWIN_ACCOUNT_BINANCE_API_KEY 와 SECRET_KEY 가 필요하다")
    })
    @GetMapping("/positions")
    public PositionReconciliationResponse positions() {
        return PositionReconciliationResponse.from(reconcile.reconcile());
    }
}
