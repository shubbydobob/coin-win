package com.coinwin.projection.api;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.projection.domain.CompoundTarget;
import com.coinwin.projection.domain.TradingCost;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 목표 복리 계산 조건.
 *
 * <p>승률도 손익비도 받지 않는다. 이 엔드포인트가 묻는 것은 "이렇게 하면 어떻게 되나" 가
 * 아니라 <b>"이 결과를 원하면 무엇이 필요한가"</b> 이고, 그 답을 정하는 것은 목표·기간·비용뿐이다.
 *
 * <p>수수료율에 기본값을 두지 않는다. 거래소가 등급과 프로모션에 따라 바꾸는 값이므로
 * 서버가 들고 있으면 바뀐 날 이쪽만 옛 숫자를 말한다 — 화면에 떠 있는 값이 그대로 실려 온다.
 */
@Schema(description = "목표 복리 계산 조건", example = ProjectionApiExamples.COMPOUND_REQUEST)
public record CompoundTargetRequest(

        @Schema(description = "시작 자산 (USDT)", example = "800")
        BigDecimal startingCapital,

        @Schema(description = "월 목표 수익률. 명목이 아니라 자산(증거금) 기준이다 — 5 는 "
                + "800 이 840 이 된다는 뜻이지 명목의 5% 가 아니다. 비용을 낸 뒤에 남는 "
                + "수익이며, 레버리지를 올려도 이 값은 달라지지 않는다", example = "5")
        BigDecimal monthlyTarget,

        @Schema(description = "기간 (개월)", example = "12")
        Integer months,

        @Schema(description = "레버리지 배수. 거래당 투입 비율과 곱해져 명목을 정한다",
                example = "10")
        BigDecimal leverage,

        @Schema(description = "거래당 투입 비율 (%). 자산의 몇 %를 증거금으로 넣는가. "
                + "명목 = 자산 × 이 값 × 레버리지 — 100 이면 매 거래에 전액을 넣는다는 뜻이고, "
                + "그것이 실제 매매와 가장 크게 갈리는 전제다", example = "20")
        BigDecimal marginUsage,

        @Schema(description = "거래 한 쪽의 수수료율 (%). 바이낸스 USDⓈ-M 무기한의 일반 사용자는 "
                + "테이커 0.05 · 메이커 0.02 다", example = "0.05")
        BigDecimal feeRate,

        @Schema(description = "거래 한 쪽의 슬리피지 (%). 호가를 밀고 들어간 만큼", example = "0.02")
        BigDecimal slippage,

        @Schema(description = "월 거래 수. <b>진입과 청산 한 쌍이 1건</b>이다 — 20 이면 "
                + "주문은 40번이고 수수료도 40번 낸다. 목표를 이 횟수로 쪼갠다", example = "20")
        Integer tradesPerMonth) {

    CompoundTarget toTarget() {
        return new CompoundTarget(
                Money.of(startingCapital),
                Percentage.of(monthlyTarget),
                DomainValues.required(months, "기간(개월)"),
                new TradingCost(
                        Percentage.of(feeRate),
                        Percentage.of(slippage),
                        DomainValues.required(leverage, "레버리지"),
                        Percentage.of(marginUsage),
                        DomainValues.required(tradesPerMonth, "월 거래 수")));
    }
}
