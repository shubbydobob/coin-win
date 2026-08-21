package com.coinwin.ai.application.service;

import com.coinwin.ai.application.AiPorts;
import com.coinwin.ai.application.port.in.DraftPlanUseCase;
import com.coinwin.ai.application.port.out.ExtractPlanPort;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.position.domain.PositionPlan;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 문장을 계획 초안으로 바꾼다. 조율만 한다 — 읽는 것은 어댑터가, 판단은 도메인이 한다.
 */
@Service
public class PlanDraftService implements DraftPlanUseCase {

    private final Optional<ExtractPlanPort> extractPlan;

    public PlanDraftService(Optional<ExtractPlanPort> extractPlan) {
        this.extractPlan = extractPlan;
    }

    @Override
    public PositionPlan draftFrom(String sentence) {
        return AiPorts.configured(extractPlan)
                .extractFrom(requireText(sentence))
                .complete();
    }

    private static String requireText(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            throw new InvalidValueException("계획을 읽어낼 문장은(는) 비어 있을 수 없다");
        }
        return sentence.strip();
    }
}
