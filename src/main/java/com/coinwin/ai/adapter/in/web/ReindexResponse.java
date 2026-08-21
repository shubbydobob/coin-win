package com.coinwin.ai.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 다시 만든 색인의 크기.
 *
 * @param indexed 색인된 문서 수. 청산된 거래 수와 같다
 */
@Schema(description = "재색인 결과", example = AiApiExamples.REINDEX_RESPONSE)
public record ReindexResponse(

        @Schema(description = "색인된 문서 수. 청산된 거래 하나가 문서 하나다", example = "37")
        int indexed) {
}
