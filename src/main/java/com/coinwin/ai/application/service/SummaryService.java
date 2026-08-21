package com.coinwin.ai.application.service;

import com.coinwin.ai.application.AiPorts;
import com.coinwin.ai.application.port.in.SummarizeUseCase;
import com.coinwin.ai.application.port.out.WriteSummaryPort;
import com.coinwin.ai.domain.Narrative;
import com.coinwin.ai.domain.SummaryFacts;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 사실을 문장으로 바꾼다. 조율만 한다 — 쓰는 것은 어댑터가, 대조는 {@link Narrative} 가 한다.
 */
@Service
public class SummaryService implements SummarizeUseCase {

    private final Optional<WriteSummaryPort> writeSummary;

    public SummaryService(Optional<WriteSummaryPort> writeSummary) {
        this.writeSummary = writeSummary;
    }

    @Override
    public Narrative summarize(SummaryFacts facts) {
        return new Narrative(AiPorts.configured(writeSummary).write(facts), facts);
    }
}
