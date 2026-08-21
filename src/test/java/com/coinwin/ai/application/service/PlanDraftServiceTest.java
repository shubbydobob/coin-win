package com.coinwin.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.ai.application.port.out.ExtractPlanPort;
import com.coinwin.ai.domain.DraftedEntry;
import com.coinwin.ai.domain.DraftedFields;
import com.coinwin.ai.domain.IncompletePlanException;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.PositionPlan;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 스텁 포트로 도는 서비스 테스트. <b>네트워크도 키도 없다.</b>
 *
 * <p>여기서 검증하는 것은 "모델이 이렇게 답하면 우리는 이렇게 한다" 이지 "모델이 이 문장에
 * 이렇게 답한다" 가 아니다. 뒤쪽은 스텁으로 증명할 수 없고 {@code liveAi} 가 맡는다.
 */
class PlanDraftServiceTest {

    private static final String SENTENCE = "6만2천에 절반, 6만에 절반 롱. 손절 5만8천, 익절 6만8천, 10배";

    private static DraftedFields complete() {
        return new DraftedFields(Direction.LONG,
                List.of(new DraftedEntry(Price.of("62000"), Percentage.of("50")),
                        new DraftedEntry(Price.of("60000"), Percentage.of("50"))),
                Price.of("58000"), Price.of("68000"), 10);
    }

    private static PlanDraftService serviceReturning(DraftedFields fields) {
        return new PlanDraftService(Optional.of(sentence -> fields));
    }

    @Test
    void 모델이_읽어_온_칸이_다_차_있으면_계획이_된다() {
        PositionPlan plan = serviceReturning(complete()).draftFrom(SENTENCE);

        assertThat(plan.direction()).isEqualTo(Direction.LONG);
        assertThat(plan.entries().size()).isEqualTo(2);
    }

    @Test
    void 모델이_칸을_비워_두면_추측하지_않고_거절한다() {
        DraftedFields noStop = new DraftedFields(Direction.LONG, complete().entries(),
                null, Price.of("68000"), 10);

        assertThatThrownBy(() -> serviceReturning(noStop).draftFrom(SENTENCE))
                .isInstanceOf(IncompletePlanException.class);
    }

    /** 문장이 비어 있으면 모델을 부르지 않는다. 빈 입력에 토큰을 쓸 이유가 없다. */
    @Test
    void 빈_문장은_모델을_부르기_전에_거절한다() {
        PlanDraftService service = new PlanDraftService(Optional.of(sentence -> {
            throw new AssertionError("모델을 불렀다");
        }));

        assertThatThrownBy(() -> service.draftFrom("   "))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 키가_없어_어댑터가_없으면_설정되지_않았다고_알린다() {
        PlanDraftService service = new PlanDraftService(Optional.<ExtractPlanPort>empty());

        assertThatThrownBy(() -> service.draftFrom(SENTENCE))
                .isInstanceOf(ExternalDataUnavailableException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }
}
