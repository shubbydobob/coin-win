# 아키텍처

## 결정: 부분 헥사고날

전면 헥사고날을 채택하지 않는다. **구현체가 둘 이상 존재하거나 존재할 예정인 경우에만** 포트를 정의한다.

| 모듈 | 방식 | 근거 |
|---|---|---|
| `market` | 포트/어댑터 | 캔들 소스가 둘 이상 (실시간 API / 저장된 과거 데이터 / 테스트) |
| `journal` | 포트/어댑터 | DB 없이 도메인 테스트를 돌리기 위한 인메모리 어댑터 필요 |
| `position` | 계층형 | 외부 의존 없음. 순수 계산 |
| `indicator` | 계층형 | 외부 의존 없음. 순수 함수 |
| `projection` | 계층형 | 외부 의존 없음 |
| `backtest` | 계층형 | `market` 포트를 소비하는 쪽. 자체 포트 불필요 |
| `ai` | 포트/어댑터 | LLM·벡터스토어를 `application` 밖에 묶어 두기 위해서 |
| `account` | 포트/어댑터 | 거래소 포지션 소스가 둘 (서명 호출 / 인메모리). 서명 키를 `application` 밖에 가둔다 |
| `watch` | 포트/어댑터 | 공지 소스가 둘(바이낸스 / 인메모리). 일정은 스냅샷 하나뿐인데도 포트를 갖는다 — 아래 |

`backtest`가 백테스트 시에는 과거 캔들 어댑터를, 실사용 시에는 실시간 어댑터를 같은 포트로 소비한다. 이 지점이 없었다면 전부 계층형으로 충분했다.

**구현체가 하나뿐인 인터페이스는 만들지 않는다.**

`ai`는 이 원칙의 경계에 있다. 검색 포트는 어댑터가 실제로 둘(pgvector / 인메모리)이라
`journal`과 같지만, LLM 포트는 운영 구현이 OpenAI 하나뿐이다. 그럼에도 포트를 두는 이유는
**테스트 더블이 아니라 격리**다 — 포트가 없으면 서비스가 `ChatClient`를 직접 들고, 그 순간
"AI 없이 도는 테스트"라는 것이 성립하지 않는다. 키가 없을 때 앱이 그대로 뜨는 것도 같은
경계 덕분이다.

## 패키지 구조

```
com.coinwin
├── common/
│   ├── domain/                  # Money, Price, Quantity, Percentage, Won, ExchangeRate
│   └── config/
│
├── market/                      # ◆ 포트/어댑터
│   ├── domain/
│   ├── application/
│   │   ├── port/in/             # LoadMarketDataUseCase
│   │   ├── port/out/            # LoadCandlesPort, SaveCandlesPort,
│   │   │                        #   LoadMarketMetricsPort, LoadExchangeRatePort
│   │   └── service/
│   └── adapter/
│       ├── in/web/
│       └── out/
│           ├── binance/         # BinanceCandleAdapter, BinanceMarketMetricsAdapter
│           ├── upbit/           # UpbitExchangeRateAdapter — 원/USDT. 바이낸스에 원화 시장이 없다
│           ├── persistence/     # JdbcCandleAdapter
│           ├── snapshot/        # ClasspathLeverageBracketAdapter
│           └── memory/          # InMemoryCandleAdapter
│
├── journal/                     # ◆ 포트/어댑터
│   ├── domain/
│   ├── application/
│   │   ├── port/in/             # RecordTradeUseCase, QueryJournalUseCase
│   │   ├── port/out/            # LoadTradesPort, SaveTradePort
│   │   └── service/             # TradeJournalService
│   └── adapter/
│       ├── in/web/              # TradeJournalController
│       └── out/
│           ├── persistence/     # JpaTradeAdapter (+ 엔티티·매퍼·QueryDSL 조건)
│           └── memory/          # InMemoryTradeAdapter
│
├── position/                    # 계층형
│   ├── domain/ application/ api/
├── indicator/
│   ├── domain/ application/
├── backtest/
│   ├── domain/                  # 대·전략·엔진. 포트도 캔들 조회도 모른다
│   ├── application/             # BacktestService — @StoredCandles 포트 소비
│   └── api/
├── projection/
│   ├── domain/ api/
│
├── account/                     # ◆ 포트/어댑터
│   ├── domain/                  # ExchangePosition, PositionMatch, PositionReconciliation
│   ├── application/
│   │   ├── port/in/             # ReconcilePositionsUseCase
│   │   ├── port/out/            # LoadExchangePositionsPort
│   │   └── service/             # PositionReconciliationService
│   └── adapter/
│       ├── in/web/              # AccountController
│       └── out/
│           ├── binance/         # BinancePositionAdapter (HMAC 서명), BinanceSigner
│           └── memory/          # InMemoryExchangePositionAdapter
│
└── ai/                          # ◆ 포트/어댑터
    ├── config/                  # SpringAiEnabledOnlyWithApiKey — 계층 밖. 기동 시점 스위치
    ├── domain/                  # DraftedFields, Narrative, JournalAnswer, TradeDocument
    ├── application/
    │   ├── port/in/             # DraftPlanUseCase, SummarizeUseCase,
    │   │                        #   AskJournalUseCase, IndexTradesUseCase
    │   ├── port/out/            # ExtractPlanPort, WriteSummaryPort, AnswerQuestionPort,
    │   │                        #   IndexTradesPort, SearchTradesPort
    │   └── service/             # PlanDraftService, SummaryService,
    │                            #   JournalQaService, TradeIndexingService
    └── adapter/
        ├── in/web/              # AiController
        ├── in/event/            # TradeClosedIndexListener — 청산 시 자동 색인
        └── out/
            ├── openai/          # 계획 추출 · 요약 · 답변 (+ 모델 응답 DTO)
            ├── pgvector/        # PgVectorTradeIndexAdapter
            └── memory/          # InMemoryTradeIndexAdapter
```

## 의존 방향

**포트/어댑터 모듈**

```
adapter.in ──→ application.port.in
                      │
                      ▼
              application.service ──→ domain
                      │
                      ▼
              application.port.out  ←── adapter.out
                                        (어댑터가 포트를 구현)
```

**계층형 모듈**

```
api → application → domain
```

`api`와 `adapter`는 같은 바깥 층이다. 둘 다 아무에게도 참조되지 않고, 둘 다 `application`과
`domain`을 참조할 수 있다. ArchUnit 규칙 2 가 이 형태로 정의돼 있다 —
`adapter.in`만 `api` 층에 얹어 두면 `adapter.out`이 어느 층에도 속하지 않게 되고,
아웃바운드 어댑터가 포트를 구현하는 것(헥사고날의 정의 그 자체)이 전부 위반으로 잡힌다.

**모듈 간**

```
common              ← 모든 모듈 (역방향 금지)
projection.api      → market.application.port.in, common.domain
backtest            → indicator, position,
                      market.application.port.out, market.domain,
                      journal.domain, projection.domain
indicator           → market.domain
journal             → position, indicator.domain
position.application → market.application.port.in, market.domain
ai                  → position.domain, indicator.domain,
                      journal.application.port.in, journal.domain
backtest.api        → ai.application.port.in, ai.domain
account             → journal.application.port.in, journal.domain,
                      position.domain, market.domain
그 외 모듈 간 직접 참조 금지
```

`indicator`와 `position`은 서로를 모른다. 조합은 `backtest`와 각 모듈 `application`에서만.

**`market.domain` 이 세 모듈에 열려 있는 이유**는 `Candle` 때문이다. `market`이 생산하고
`indicator`·`backtest`가 소비하는 공통 어휘이며, 포트의 반환 타입이므로 포트를 쓰는 쪽은
어차피 이 타입을 본다. `common`으로 올리는 선택지도 있었으나 그러면 `common`이 반올림 정책을
가진 값 객체 모음에서 공용 모델 전반으로 넓어진다. 근거는 `docs/adr/013`.

**`position.application → market.application.port.in`** 은 구간별 유지증거금 때문이다.
청산가는 거래소 레버리지 구간표에 의존하는데, `position/domain`이 그 구현을 들고 있으면
도메인에 `position → market` 의존이 생긴다. 인터페이스는 `position/domain`에 두고 구현만
`position/application`에서 `market`의 인바운드 포트를 소비한다. 근거는 `docs/adr/008`.
아웃바운드가 아니라 인바운드를 쓰는 이유는, "구간표를 어디서 얻는가"가 `market`의 정책이기
때문이다.

**`projection.api → market.application.port.in`** 은 원/USDT 환율 하나 때문이다. 복리
계산기가 결과를 원화로도 보여 주는데, 환율은 거래소에서 오므로 `projection` 이 스스로 얻을
수 없다. **아웃바운드가 아니라 인바운드를 쓰는 이유는 "환율을 어디서 얻는가" 가 `market` 의
정책이기 때문이다** — `position.application → market.application.port.in` 과 같은 판단이다.
`projection` 은 여전히 계층형이고 자기 포트를 갖지 않는다.

**환율은 값을 못 얻어도 계산을 세우지 않는다.** 포트가 `Optional` 을 돌려주고 응답의 원화
묶음이 통째로 빈다. 다른 아웃바운드 포트가 못 읽으면 던지는 것과 다른데, 원화는 **곁들임**
이라 이것 때문에 복리 계산 전체가 503 이 될 이유가 없기 때문이다. 대신 옛 환율이나 0 원으로
채우지 않는다 — 비어 있는 것과 알 수 없는 것을 가르는 `account` 의 규칙과 같다.

**`ExchangeRate` · `Won` 은 `common/domain` 에 있다.** `Money` 와 `Won` 사이의 단위 변환이고,
어느 거래소에서 얻었는가는 어댑터의 사정이라 그 타입은 거래소를 모른다. `common` 이 넓어지는
것을 경계한 `docs/adr/013` 의 기준으로 봐도 이 둘은 **반올림 정책을 가진 값 객체**이지 공용
모델이 아니다.

**`journal → indicator.domain`** 은 `BandPosition` 하나 때문이다. 진입 시점에 가격이 구름과
밴드의 어느 쪽에 있었는지를 기록하는데, 그 세 값은 Phase 4 에서 이미 확정돼 있다. `journal`에
같은 뜻의 enum 을 또 두면 "경계는 구간에 포함된다"는 규칙이 한쪽만 바뀌는 순간 두 모듈이
다른 답을 낸다. `common`으로 올리지 않은 이유는 `Candle`과 같다 — `common`이 반올림 정책을
가진 값 객체 모음에서 공용 모델 전반으로 넓어진다. 근거는 `docs/adr/017`.
**`journal`은 지표를 계산하지 않는다.** 계산기도 `IchimokuValue`도 참조하지 않고 판정 결과만
적는다. 그 이상을 끌어오게 되면 이 의존을 다시 봐야 한다.

**`backtest.api → ai`, 그리고 `ai`는 `backtest`를 모른다.** 이 방향은 선택이 아니라 순환이
강제한 것이다. 처음에는 `ai`가 백테스트를 돌려 결과를 요약하게 두려 했는데, 그러면
백테스트 쪽이 요약을 부르는 순간 `ai ↔ backtest`가 되고 ArchUnit 규칙 3이 빌드를 세운다.
그래서 `SummaryFacts`를 **백테스트와 무관한 "숫자 딸린 사실 묶음"**으로 정의하고 사실을
만드는 일을 부르는 쪽에 남겼다. 제약이 더 나은 모양을 만들었다 — 요약이 백테스트 전용이
아니게 됐고, 나중에 어느 모듈이든 자기 수치를 문장으로 바꿀 수 있다. 요약 엔드포인트가
`/api/ai`가 아니라 `/api/backtests/narrative`인 것도 같은 이유다.

**`account → journal`, 그리고 `journal`은 `account`를 모른다.** `ai → journal` 과 같은
모양이고 이유도 같다 — 기록이 대조를 부르면 `journal ↔ account` 순환이 되고 ArchUnit 규칙 3이
빌드를 세운다. **기록은 진실의 원천이고 대조는 그것을 읽는 쪽이다.**

아웃바운드가 아니라 **인바운드** 포트를 쓰는 이유는 "미청산 거래가 무엇인가" 가 `journal` 의
정책이기 때문이다. 저장소를 직접 읽으면 그 정책이 `account` 로 샌다 —
`position.application → market.application.port.in` 과 같은 판단이다.

`market` 에 두지 않은 이유는 **서명 때문이다.** `market` 은 모두에게 같은 사실(캔들·펀딩비·OI)
이고 키가 필요 없다. 포지션은 내 계좌의 사실이고 키가 필요하다. 한 모듈에 섞으면 "이 어댑터는
키가 있어야 도는가" 가 클래스마다 달라진다. 근거는 `docs/spec/phase9-exchange-positions.md`.

**`account`는 주문을 내지 않는다.** 읽기 전용 엔드포인트만 부르고, 키도 읽기 전용으로
발급해야 한다(`scope.md`). 이 경계가 무너지면 `scope.md` 의 "자동 매매 / 주문 실행 API 연동"
금지가 코드에서 성립하지 않는다.

**`journal`은 `ai`를 모른다.** 청산 시 자동 색인이 필요하지만 서비스가 색인 유스케이스를
직접 부르면 또 순환이다. `journal.application`이 `TradeClosedEvent`를 발행하고 `ai`가
듣는다 — 듣는 쪽만 발행하는 쪽을 안다. 듣는 이가 없어도 아무 일도 일어나지 않는 것이
정상이다. **인덱스는 파생이고 진실의 원천은 언제나 매매 기록이다.**

**`ai → journal`** 은 문서를 만들기 위해서다. `TradeDocument.over(List<ClosedTrade>)`가
목록을 받는 이유가 이 절의 요점이다 — "직전 거래는 손실이었다"는 거래 하나만 봐서는 알 수
없고 시간순 전체 위에서만 계산된다. `indicator.domain`은 `BandPosition` 하나 때문이며
`journal`이 같은 이유로 갖는 의존과 같다(`docs/adr/017`).

**`ai → position.domain`** 은 계획 초안이 곧 `PositionPlan` 이기 때문이다. 파싱 결과를 따로
정의하면 "롱의 손절가는 최저 진입가보다 낮다" 같은 규칙이 두 곳에 생기고, 그러면 초안이
통과했는데 같은 값이 계획 API 에서 거부되는 일이 생긴다. **`ai` 는 계획 규칙을 갖지 않는다** —
읽어낸 칸이 다 찼는지만 보고 나머지는 Phase 1 에 맡긴다. 그 경계가 무너지면(초안 전용 규칙이
생기면) 이 의존을 다시 봐야 한다.

**`backtest → journal.domain, projection.domain`** 은 어휘를 나누기 위해서다. 백테스트가 낸
거래와 실제로 한 매매가 둘 다 `ClosedTrade` 이므로 `JournalSummary` 를 양쪽에 그대로 씌울 수
있다 — **검증한 전략과 실제 기록을 같은 기준으로 비교할 수 있다는 뜻이다.** 따로 정의하면
"0 원은 승리가 아니다"(`TradeTally`), "반사실에서 펀딩비를 빼지 않는다"(`ClosedTrade`),
"최대낙폭은 직전 고점 대비"(`EquityCurve`) 같은 규칙이 두 곳에서 갈라지고, 그러면 두 수치를
나란히 놓는 것 자체가 무의미해진다. `journal.domain` 은 프레임워크 의존이 없으므로 이 의존이
백테스트를 DB 나 Spring 에 묶지 않는다. 근거는 `docs/adr/018`.
**방향은 한쪽뿐이다** — `journal` 은 `backtest` 를 모른다. `MarketContext` 의 지지·저항을
`PriceZone` 으로 구조화하는 것은 새 방향을 만드는 별개의 결정이므로 하지 않았다.

**`watch` 는 아무 모듈도 참조하지 않는다.** 예정 이벤트와 거래소 공지는 다른 모듈을 몰라도
성립한다. 이것이 의도된 제약인 이유는, 캘린더가 `position` 을 알게 되는 순간 "이벤트가 가까우면
명목을 자동으로 줄인다" 로 미끄러지기 때문이다. 그것은 `scope.md` 가 금지한 자동 판단이다.
경고는 화면에 띄우고 줄이는 것은 사람이 한다.

**`watch` 의 일정 어댑터는 구현체가 하나뿐인데도 포트를 갖는다.** 설계 초안은 "구현체가
하나뿐인 인터페이스는 만들지 않는다" 는 위 원칙을 따라 서비스가 어댑터를 직접 들게 했는데
**규칙 2 와 6 이 그것을 거부했다.** 규칙이 옳다 — 서비스가 어댑터를 직접 들면 "스냅샷에서
읽는다" 가 응용 계층의 사실이 되고 출처가 바뀔 때 서비스가 함께 바뀐다.
`ClasspathLeverageBracketAdapter` 도 같은 이유로 이미 `LoadLeverageBracketsPort` 를 구현하고
있었다. **위 원칙은 "포트를 새로 만들 이유가 되는가" 를 묻는 것이지 "만들면 안 된다" 가
아니다** — 계층 규칙이 요구하면 그쪽이 이긴다.

**모듈 간 의존은 ArchUnit이 강제하지 않는다.** 아래 6개 규칙 중 어느 것도 모듈 경계를 보지
않는다 — 규칙 3(순환 참조)이 최악의 경우만 막는다. 이 표는 문서와 리뷰가 지킨다.
Phase 6 에서 `backtest`가 다섯 모듈을 조합하게 됐고, 그럼에도 규칙으로 세우지 않았다 —
규칙 5 가 `backtest → market.adapter` 라는 **가장 위험한 한 방향**을 이미 막고 있고, 나머지는
전부 도메인 → 도메인이라 잘못 걸어도 프레임워크 오염이 아니라 응집도 문제에 그친다.

## ArchUnit 강제 규칙

아래 6개는 테스트로 강제된다. 위반 시 빌드 실패.

1. `domain` 패키지의 Spring / JPA / Jackson import 금지
2. 계층 의존 방향 (`(api|adapter) → application → domain`)
3. 패키지 순환 참조 0건
4. `market.application` / `journal.application` / `ai.application` / `account.application` / `watch.application` → `adapter` 참조 금지
5. `backtest` → `market.adapter` 참조 금지 (포트만 허용)
6. `adapter.out` 구현체는 반드시 `application.port.out` 인터페이스를 구현

4번과 5번이 없으면 헥사고날이 이름만 남고 계층형으로 무너진다. `account` 에서는 더 날카롭다 —
규칙 4가 깨지면 **서명 키가 `application` 으로 샌다.**

규칙 4는 모듈 이름을 손으로 열거하므로 모듈마다 위반 픽스처가 필요하다:
`r4`(market) · `r4j`(journal) · `r4a`(ai) · `r4acc`(account) · `r4w`(watch).

`r4w` 는 상상해서 만든 것이 아니다. `watch` 를 만들면서 실제로 그렇게 짰고 규칙 2·6 이 먼저
잡았다. 규칙 4 에까지 넣은 것은 공지 어댑터가 붙으면서 같은 실수를 다시 할 자리가 생겼기
때문이다.

**`allowEmptyShould` 는 이제 하나도 없다.** 대상 패키지가 아직 없는 규칙을 통과시키던 임시
플래그였고, 마지막 하나(규칙 5)가 Phase 6 에서 빠졌다. 여섯 규칙이 전부 실제 클래스를 센다.
플래그가 되돌아오는 것은 사람의 기억이 아니라 테스트가 막는다 —
`ArchitectureRulesTest.어떤_규칙도_빈_매칭을_허용하지_않는다` 가 `ArchitectureRules` 소스에
`allowEmptyShould(` 가 없는지 매 빌드 검사한다.

## 포트 명명 규약

- 인바운드: `~UseCase` (예: `LoadMarketDataUseCase`)
- 아웃바운드: `~Port` (예: `LoadCandlesPort`)
- 어댑터: `{기술}{대상}Adapter` (예: `BinanceCandleAdapter`)

**포트는 좁게 정의한다.** `MarketPort` 하나에 메서드 10개를 몰지 않고 역할별로 분리한다. 인터페이스 분리 원칙을 지켜야 테스트 더블이 단순해진다.
