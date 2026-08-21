# Phase 7 — Spring AI 명세

> 구현 전에 확정한 **경계와 검증 방법**이다. ADR 005 가 "AI 는 판단하지 않는다" 로 범위를
> 고정했고, 이 문서는 그 세 항목을 코드에서 무엇으로 만들지와 **각각을 어떻게 검증할지**를
> 적는다.
>
> 확정 경로: 인터뷰 2라운드 (2026-08-22). 미확정 항목은 § 11 에 남겨 두었다.

---

## 1. 한 문장

**AI 는 옮겨 적고, 문장으로 바꾸고, 찾아 준다. 계산하지 않고 추천하지 않는다.**

세 기능 전부에서 **숫자의 출처는 AI 가 아니다.** 파싱은 사용자가 쓴 수를 옮기고, 요약은
백테스트가 낸 수를 인용하며, 질의 응답은 저장된 거래를 근거로 든다. 이 문장이 아래 모든
검증 하네스의 근거다.

---

## 2. 확정된 선택

| 논점 | 결정 | 근거 |
|---|---|---|
| 범위 | 세 항목 전부 (파싱 · 요약 · RAG) | 인터뷰 |
| 제공자 | OpenAI (`spring-ai-starter-model-openai`) | 채팅·임베딩이 한 제공자로 끝난다 |
| Spring AI | **2.0.x** (2.0.1, 2026-08-21) | 2.0 이 Boot 4.0/4.1 · Framework 7 라인이다. 1.1.x 는 Boot 3.5 용 |
| 테스트 | 스텁 어댑터 + `liveAi` 태그 분리 | Phase 4 `crossCheck` 와 같은 형태 |
| 파싱 결과 | **초안만 반환.** 저장·계산 안 함 | AI 응답이 아무것도 자동 실행하지 않는다 (ADR 005) |
| 색인 | 청산 시 자동 + 수동 재색인 | 문서 형식·모델이 바뀌면 재색인 없이는 과거 기록이 구버전으로 남는다 |
| 질의 응답 | 답변 + **근거 거래 ID** | 사용자가 원본을 대조할 수 있어야 한다 |
| 모델 | `gpt-5.6-luna` (설정으로 교체 가능) | 1인 사용량. $0.20 / $1.20 per 1M |
| 키 부재 | 앱은 뜨고 `/api/ai/**` 만 503 | AI 와 무관한 작업이 키를 요구하지 않는다 |

---

## 3. 모듈 배치

새 모듈 `ai` 를 만든다. **포트/어댑터**다.

```
com.coinwin.ai
├── domain/                       # Spring · Jackson · Spring AI 의존 없음
│   ├── PlanDraft, DraftedEntry, MissingField, IncompletePlanException
│   ├── SummaryFacts, BacktestNarrative, FabricatedNumberException
│   └── JournalAnswer, TradeDocument, RetrievedTrade, UnknownCitationException
├── application/
│   ├── port/in/                  # DraftPlanUseCase, SummarizeBacktestUseCase,
│   │                             #   AskJournalUseCase, IndexTradesUseCase
│   ├── port/out/                 # ExtractPlanPort, WriteSummaryPort, AnswerQuestionPort,
│   │                             #   IndexTradesPort, SearchTradesPort
│   └── service/                  # PlanDraftService, BacktestSummaryService,
│                                 #   JournalQaService, TradeIndexingService
└── adapter/
    ├── in/web/                   # AiController (+ 요청·응답 DTO)
    └── out/
        ├── openai/               # OpenAiExtractPlanAdapter, OpenAiWriteSummaryAdapter,
        │                         #   OpenAiAnswerQuestionAdapter (+ LLM 응답 DTO)
        ├── pgvector/             # PgVectorTradeIndexAdapter (색인·검색 둘 다 구현)
        └── memory/               # InMemoryTradeIndexAdapter
```

**포트를 두는 이유가 테스트 더블이 아니다.** `ChatClient` · `Document` · `VectorStore` 같은
Spring AI 타입이 `application` 과 `domain` 으로 새지 않게 하는 것이다. 포트가 없으면
서비스가 `ChatClient` 를 직접 들고, 그 순간 "AI 없이 도는 테스트" 라는 것이 성립하지 않는다.
`SearchTradesPort` 는 어댑터가 실제로 둘이다 (pgvector / 인메모리) — `journal` 과 같은 형태다.

**LLM 스텁은 `main` 이 아니라 테스트 소스에 둔다.** 인메모리 캔들·거래 어댑터와 다르다.
지어낸 계획을 돌려주는 어댑터가 운영에 존재하면 키가 빠졌을 때 조용히 가짜를 내놓는다.
키가 없으면 빈이 없고, 빈이 없으면 503 이다 (§ 8).

### 모듈 간 의존 (architecture.md 표에 추가한다)

```
ai → backtest.application, backtest.domain,
     journal.application.port.in, journal.domain,
     position.domain, indicator.domain, common
```

역방향은 없다. **`journal` 은 `ai` 를 모른다** — 자동 색인은 `journal` 이 발행하는 이벤트를
`ai` 가 듣는 형태로 뒤집는다 (§ 6.2).

---

## 4. 자연어 → 계획 초안

### 4.1 엔드포인트

```
POST /api/ai/plan-draft
{ "text": "6만2천에 절반, 6만에 절반 롱. 손절 5만8천, 익절 6만8천, 10배. 총 0.1개." }
```

응답 필드명은 **기존 `TradePlanRequest` 와 같게 맞춘다.** 사용자가 확인한 뒤 그대로
`/api/journal/trades` 나 `/api/positions/analysis` 에 붙여 넣을 수 있어야 한다.

```json
{
  "direction": "LONG",
  "entries": [ { "price": 62000, "allocation": 50 }, { "price": 60000, "allocation": 50 } ],
  "stopLoss": 58000,
  "takeProfit": 68000,
  "leverage": 10,
  "totalQuantity": 0.1
}
```

### 4.2 AI 는 산술을 하지 않는다

**문장에 있는 수만 옮긴다.** `allocation` 은 문장의 비율("절반" → 50)이고, 회차별 수량
`0.05` 는 응답에 없다. 총수량 × 비중은 자바가 계산한다 — 이미 `EntryLadder` 가 하는 일이다.

한국어 수 표현("6만2천" → `62000`)은 산술이 아니라 **옮겨 적기**이므로 허용하고 테스트한다.
반면 "지금가 근처" · "저항 위쪽" 처럼 수로 환원할 수 없는 표현은 값을 만들지 않는다.

### 4.3 추측해서 채우지 않는다 — 이 기능의 유일한 어려운 규칙

LLM 응답 스키마의 모든 필드는 **nullable** 이다. `PlanDraft` 가 도메인에서 검사한다.

```java
// ai/domain
public record PlanDraft(Direction direction, List<DraftedEntry> entries,
                        Price stopLoss, Price takeProfit, Integer leverage,
                        Quantity totalQuantity) {
    public static PlanDraft ofNullable(...);      // 누락 필드를 모아 예외
    public List<MissingField> missing();
}
```

누락이 하나라도 있으면 `IncompletePlanException(List<MissingField>)` → **422 + 무엇이
없는지 목록**. 되묻기 위한 응답이다. `DomainExceptionHandler` 가 `DomainException` 하나만
알고 있으므로 새 핸들러를 추가하지 않는다.

> 손절가를 지어낸 계획은 잘못된 포지션 사이즈로 이어지고, 그 오류는 계산 계층 전체를
> 조용히 오염시킨다. **비는 것보다 틀린 것이 나쁘다.**

프롬프트: `resources/prompts/plan-draft.st`. `temperature: 0`,
`.entity(Class, spec -> spec.useProviderStructuredOutput().validateSchema())`.

### 4.4 검증 하네스

스텁 어댑터에 고정 응답을 물린 **케이스 표**로 검증한다. 최소 12건:

| # | 입력 | 기대 |
|---|---|---|
| 1 | 전 항목이 다 있는 문장 | 완전한 초안 |
| 2 | 손절 언급 없음 | 422 · `missing = [stopLoss]` |
| 3 | 방향 언급 없음 | 422 · `missing = [direction]` |
| 4 | "6만2천" 만 단위 | `62000` |
| 5 | "절반씩 두 번" | `allocation` 50 / 50 |
| 6 | 비중 합이 100 이 아님 | 422 (기존 `EntryLadder` 규칙 재사용) |
| 7 | 분할 없이 한 번에 | `entries` 1건 · `allocation` 100 |
| 8 | 숏 문장 | `SHORT` |
| 9 | 레버리지 없음 | 422 · `missing = [leverage]` |
| 10 | "지금가 근처에서" | 422 · 가격을 만들지 않는다 |
| 11 | 매매와 무관한 문장 | 422 · 전 필드 누락 |
| 12 | 모델이 마크다운 펜스로 감싼 JSON | 파싱 성공 (어댑터가 벗긴다) |

같은 12건 중 대표 3건을 `liveAi` 로 실제 모델에도 돌린다.

---

## 5. 백테스트 결과 요약

### 5.1 엔드포인트

```
POST /api/ai/backtest-summary     # 본문은 기존 RunBacktestRequest 와 같다
```

`ai.application` 이 `backtest.application.BacktestService` 를 호출해 결과를 얻고, 수치를
`SummaryFacts` 로 추린 뒤 문장을 받는다. 프롬프트에는 **숫자만 넘어가고 캔들은 넘어가지
않는다** — 토큰과 비용의 문제이기도 하지만, 요약이 원본 수치 밖으로 나갈 여지를 없애는 것이
더 중요하다.

### 5.2 지어낸 숫자를 잡는다

ADR 005 는 "요약은 원본 수치와 대조할 수 있다" 를 근거로 요약을 허용했다. 그 대조를
**코드로 만든다.**

```java
// ai/domain
public record BacktestNarrative(String text, SummaryFacts facts) {
    // 생성자에서 검사한다. 실패하면 FabricatedNumberException.
}
```

규칙: **소수점을 포함하거나 1000 이상인 수는 전부 `facts` 에 있어야 한다.**

- 가격·손익·자산은 1000 이상, 승률·손익비·낙폭은 소수 — 지어낼 수 있는 값은 전부 걸린다.
- 1000 미만 정수("거래 24건", "두 배")는 문장 구성에 필요하고 가격으로 오인될 수 없다.
- 대조 기준은 표시 형식이 아니라 값이다. `62,000` · `62000` · `62000.00` 은 같은 수로 본다.

이 검사는 테스트에서만이 아니라 **운영 경로에서도 돈다.** 검증할 수 있어서 허용한 기능인데
검증을 테스트에만 두면 실제 응답은 검증되지 않은 채 나간다.

프롬프트: `resources/prompts/backtest-summary.st`.

### 5.3 검증 하네스

- `facts` 에 없는 수를 넣은 스텁 응답 → `FabricatedNumberException`
- 형식만 다른 같은 수(`62,000`) → 통과
- 1000 미만 정수만 있는 문장 → 통과
- Phase 6 의 감도표 조합 하나를 `liveAi` 로 요약시켜 사람이 한 번 읽는다

---

## 6. 매매 기록 질의 (RAG)

### 6.1 문서 하나 = 청산된 거래 하나

`ClosedTrade` 를 한국어 한 문단으로 렌더링한다. 렌더링은 `ai/domain/TradeDocument` 가
한다 — 문자열 조립도 도메인 규칙이고, 어댑터마다 다른 문장을 만들면 색인이 갈라진다.

메타데이터(필터·근거용):

| 키 | 출처 |
|---|---|
| `tradeId` | `ClosedTrade.id` |
| `direction` · `leverage` | `plan` |
| `followedPlan` | `ClosedTrade.followedPlan()` |
| `realizedPnl` · `costOfDeviation` | 계산값 |
| `exitReason` | `closure.exit().reason()` |
| `openedAt` · `closedAt` · `holdingMinutes` | 시각 |
| `ichimokuPosition` · `bollingerPosition` | `MarketContext` |
| **`afterLoss`** | **직전 거래가 손실이었는가** |
| **`minutesSincePreviousTrade`** | `timeSincePreviousTrade` |

> **마지막 두 개가 이 절의 핵심이다.** ADR 005 가 예로 든 "손실 직후 진입한 거래의 결과" 는
> **의미 검색으로 찾을 수 없다.** 유사도는 문장이 서로 얼마나 닮았는지만 보고, "직후" 는
> 순서에서만 나온다. 그래서 **색인 시점에 파생 사실을 미리 계산해 메타데이터로 박는다.**
> 색인은 항상 시간순 전체 목록 위에서 이루어진다 — 거래 하나만 보고는 `afterLoss` 를 알 수 없다.

### 6.2 색인 시점

- **자동:** `journal` 이 청산 시 `TradeClosedEvent` 를 발행하고 `ai` 가 듣는다.
  `@TransactionalEventListener(AFTER_COMMIT)` + 비동기. **청산 API 를 OpenAI 장애에 묶지
  않는다.** 색인 실패는 로그로 남기고 재색인으로 복구한다.
- **수동:** `POST /api/ai/reindex` — 전체를 다시 만든다.

**기록이 진실의 원천이고 인덱스는 파생이다.** 인덱스가 비어도 `journal` 은 온전하고,
언제든 다시 만들 수 있다. 이 관계가 성립하는 한 색인 실패는 장애가 아니다.

### 6.3 질의

```
POST /api/ai/journal-query
{ "question": "손실 직후에 들어간 거래는 결과가 어땠나?", "topK": 8 }
```

```json
{
  "answer": "...",
  "citedTradeIds": ["..."],
  "retrieved": [ { "tradeId": "...", "score": 0.83, "summary": "..." } ]
}
```

규칙 셋:

1. **검색된 거래만 근거로 삼는다.** 인용된 ID 가 검색 결과 밖이면
   `UnknownCitationException` → 502. 도메인이 검사한다 (`JournalAnswer` 생성자).
2. **모르면 모른다고 답한다.** 검색 결과가 비면 모델을 부르지 않고 "해당하는 기록이 없다" 를
   돌려준다. 없는 것에 대해 문장을 만들 기회 자체를 주지 않는다.
3. **앞으로의 매매를 묻는 질문에는 답하지 않는다.** 시스템 프롬프트에 명시하고
   `liveAi` 테스트로 확인한다. ADR 005 의 금지 항목이 프롬프트에서 살아 있는지는
   프롬프트를 읽어서가 아니라 물어봐서 안다.

프롬프트: `resources/prompts/journal-answer.st`.

---

## 7. 인프라

### 7.1 pgvector

- **이미지 교체:** `postgres:18-alpine` → `pgvector/pgvector:pg18`.
  `gradle/libs.versions.toml` 의 `postgresImage` 한 곳을 고치고 `compose.yaml` 을 맞춘다.
  두 곳이 갈리면 로컬과 통합 테스트가 다른 DB 를 쓴다 (compose.yaml 주석의 그 이유).
- **스키마는 Flyway 만 만든다.** `V3__vector_store.sql`.
  `spring.ai.vectorstore.pgvector.initialize-schema: false` (2.0 기본값이지만 명시한다).

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content   text,
    metadata  json,
    embedding vector(1536)
);
CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);
```

`uuid-ossp` 는 켜지 않는다 — pg18 의 `gen_random_uuid()` 로 충분하다. 확장은 적을수록 좋다.

### 7.2 설정

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat.options:
        model: ${COINWIN_AI_CHAT_MODEL:gpt-5.6-luna}
        temperature: 0
      embedding.options:
        model: ${COINWIN_AI_EMBEDDING_MODEL:text-embedding-3-small}
    vectorstore.pgvector:
      initialize-schema: false
      dimensions: 1536
      index-type: HNSW
      distance-type: COSINE_DISTANCE
```

**차원 1536 을 설정과 마이그레이션 양쪽에 고정한다.** 임베딩 모델을 바꾸면 마이그레이션과
전체 재색인이 함께 필요하다 — 차원이 다른 벡터가 같은 테이블에 섞이면 검색이 조용히 망가진다.

---

## 8. 키가 없을 때

- OpenAI 어댑터 빈은 **키가 비어 있지 않을 때만** 올라온다.
- 인바운드 서비스는 어댑터를 `Optional` 로 받고, 없으면
  `ExternalDataUnavailableException("AI 기능이 설정되지 않았다")` 를 던진다 —
  기존 핸들러가 **503** 으로 옮긴다. 새 예외도 새 핸들러도 만들지 않는다.
- 포지션·백테스트·기록은 그대로 동작한다.
- **키 없이 `.\gradlew.bat check` 가 통과해야 한다.** 이것이 § 10 의 첫 완료 조건이다.

---

## 9. 규칙과 테스트

### 9.1 ArchUnit

- 규칙 1 — `ai.domain` 에 Spring · JPA · Jackson 금지.
  **결과:** LLM 응답 DTO(`OpenAiPlanResponse` 등)는 `adapter.out.openai` 에 산다.
  Jackson 이 읽는 타입은 어댑터의 관심사다. 도메인 `PlanDraft` 로 매핑해서 넘긴다.
- 규칙 4 — `ai.application → ai.adapter` 금지. **규칙 4 는 모듈 이름을 손으로 열거하므로
  `ai` 를 추가하고 위반 픽스처 `archfixture/r4a` 를 함께 넣는다.** 픽스처가 없으면 규칙이
  `ai` 에 대해 아무것도 매칭하지 않아도 알 수 없다 (Phase 5 의 `r4j` 와 같은 이유).
- 규칙 6 — `ai.adapter.out` 구현체는 포트를 구현한다. 자동 적용.
- `allowEmptyShould` 는 0 을 유지한다. `ArchitectureRulesTest` 가 매 빌드 확인한다.

### 9.2 테스트

- **기본 `test`:** 전부 스텁으로 돈다. 네트워크도 키도 DB 도 없다.
- **`liveAi` 태그:** 실제 OpenAI 를 부른다. `.\gradlew.bat liveAi` 로 사람이 돌린다.
  `crossCheck` 와 같은 형태 — `test` 에서 태그로 제외하고 `outputs.upToDateWhen { false }`.
- **계약 스위트:** `SearchTradesPort` 하나의 스위트를 인메모리 · pgvector 두 어댑터에
  돌린다 (`LoadCandlesPort` 선례).
- **통합:** Testcontainers `pgvector/pgvector:pg18` 로 색인 → 검색 왕복.
- **커버리지:** `ai/domain` 90%.

---

## 10. 완료 조건

1. `OPENAI_API_KEY` 없이 `.\gradlew.bat check` 통과.
2. § 4.4 의 12개 파싱 케이스 통과. 누락 필드는 422 로 **무엇이 없는지** 돌려준다.
3. 원본에 없는 수가 들어간 요약이 **예외로 거부된다**는 것을 테스트가 보인다.
4. "손실 직후에 들어간 거래" 질의가 근거 거래 ID 와 함께 답변된다 — 스텁으로 항상,
   실제 모델로 한 번(`liveAi`).
5. 실제 매매 1건이 청산 → 자동 색인 → 질의에 잡히는 것이 통합 테스트로 확인된다.
6. `ai` 밖의 어떤 모듈도 `ai` 를 참조하지 않는다.

---

## 11. 구현하며 달라진 것

명세가 틀린 곳이 넷 있었다. 전부 "이 문장이 코드에서 무엇이 되는가" 를 물었을 때 드러났다.

**① 총수량은 초안에 있으면 안 된다.** § 4.1 은 `totalQuantity` 를 응답에 넣고 있었다.
그런데 이 프로젝트에서 수량은 입력이 아니라 **손절가가 결정하는 출력**이다
(`PositionPlan.totalQuantity(RiskBudget)`). 초안에 수량 칸을 두면 모델이 채운 숫자가 리스크
사이징을 통째로 건너뛰고 계획으로 들어간다. 다섯 칸만 남겼고 `PlanField` 에도 없다.

**② 요약 엔드포인트는 `/api/ai` 아래에 둘 수 없다.** § 5.1 은 `ai` 가 백테스트를 돌리게
했는데, 그러면 백테스트 쪽이 요약을 부르는 순간 `ai ↔ backtest` 순환이 되고 ArchUnit 규칙 3
이 빌드를 세운다. `SummaryFacts` 를 백테스트와 무관한 사실 묶음으로 정의하고 방향을
`backtest → ai` 한쪽으로만 냈다. 엔드포인트는 `POST /api/backtests/narrative` 다.
**제약이 더 나은 모양을 만들었다** — 요약이 백테스트 전용이 아니게 됐다.

**③ 12개 파싱 케이스는 한 스위트가 아니다.** § 4.4 의 표는 도메인 규칙과 모델 행동을 섞어
놓았다. "6만2천 → 62000" 은 **스텁으로 증명할 수 없다.** 갈랐다 — 기본 `test` 는
"모델이 이렇게 답하면 우리는 이렇게 한다" 만 보고, "모델이 이 문장에 이렇게 답한다" 는
`liveAi` 가 본다. 마크다운 펜스(케이스 12)는 가짜 `ChatModel` 을 물린 어댑터 테스트가
실제로 실행해 확인했다.

**④ 빠진 칸 목록은 `ProblemDetail` 의 별도 필드로 내리지 않는다.** § 4.3 은 그렇게 적었는데
conventions.md 가 "HTTP 매핑은 `@RestControllerAdvice` 한 곳에서만" 이라고 못박고 있다.
모듈별 어드바이스를 추가하는 대신 예외 메시지가 빠진 항목을 전부 나열한다. 예외 객체는
`missing()` 으로 목록을 들고 다니므로 나중에 필요해지면 그때 꺼낼 수 있다.

### 배선에서 드러난 것 둘

**스타터를 붙이는 것만으로 앱이 기동하지 않는다.** OpenAI 자동 구성은 키가 없어도 음성·이미지
모델 빈을 만들려 하고 SDK 가 빈 생성 단계에서 던진다. `SpringAiEnabledOnlyWithApiKey` 가
그 자리다. 처음에는 `addFirst` 로 넣었는데 그러면 **우리 기본값이 명시 설정까지 덮어써서**
통합 테스트가 가짜 임베딩을 주고 벡터 스토어를 켤 방법이 없어진다. `addLast` 다 —
이 값들은 기본값이지 강제가 아니다.

**`spring.flyway.enabled: true` 가 Phase 3 이후로 아무 일도 하지 않고 있었다.** Boot 4 는
자동 구성을 기술별 모듈로 쪼갰고 `flyway-core` 만으로는 배선이 붙지 않는다. 통합 테스트가
전부 Flyway 를 손으로 돌려서(`Flyway.configure()...migrate()`) **앱 기동 경로가 한 번도
검증된 적이 없었다.** Phase 7 의 통합 테스트가 컨텍스트를 실제로 띄우면서 잡혔다.
`spring-boot-starter-flyway` 로 바꿨다.

---

## 12. 미확정

- **비용 상한.** 1인 사용량이면 월 몇 센트 수준이라 지금은 두지 않는다. 재색인이 전체
  거래를 다시 임베딩한다는 것만 기록해 둔다.
- **임베딩 모델 교체 절차.** 마이그레이션 + 전체 재색인이라는 것까지는 정했고, 순서를
  자동화할지는 실제로 바꿀 때 정한다.
- **자동 색인이 동기다.** 청산 응답이 임베딩 한 번만큼 느려진다. 1인 사용자의 청산 빈도에서
  비동기의 복잡도가 값을 하지 않는다고 봤다. 이 전제가 깨지면 여기부터 다시 본다.
- **"약 6만" 같은 한국어 단위 표기는 요약 대조를 빠져나간다.** 토큰이 `6` 하나라 검사 대상이
  아니다. 프롬프트가 수를 그대로 쓰게 하는 것으로 막고 있으며, 규칙이 아니라 지침이다.
