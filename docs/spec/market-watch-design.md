# 감시 화면 — 설계

- 날짜: 2026-08-23
- 상태: 설계 (구현 전)
- 명세: `docs/spec/market-watch.md`

이 문서는 **무엇을 만드는가**(명세)가 아니라 **어떤 모양으로 만드는가**를 정한다. 응답 필드는
전부 실제 호출로 받아 확인했다(2026-08-23). 추측한 필드는 없다.

---

## 1. 패키지

### 1.1 `market` 에 더하는 것

```
market/
├── domain/
│   ├── OrderBook.java          ◆ 새로 — 호가 20단 + 불균형
│   ├── PriceLevel.java         ◆ 새로 — 한 호가 (가격, 잔량)
│   ├── Ticker.java             ◆ 새로 — 현재가·24h 변동·고저·거래량
│   ├── MetricHistory.java      ◆ 새로 — 한 지표의 시계열 표본
│   └── Percentile.java         ◆ 새로 — 표본 안에서의 위치. 동점 처리를 소유한다
├── application/
│   ├── port/in/  LoadOrderBookUseCase, LoadOutliersUseCase          ◆ 새로
│   ├── port/out/ LoadOrderBookPort, LoadMetricHistoryPort           ◆ 새로
│   └── service/  OrderBookService, OutlierService                   ◆ 새로
└── adapter/
    ├── in/web/   MarketController (엔드포인트 둘 추가)
    └── out/
        ├── binance/  BinanceOrderBookAdapter, BinanceMetricHistoryAdapter ◆ 새로
        │             BinanceDepth, BinanceTicker, BinanceFundingRate,
        │             BinanceOpenInterestHist, BinanceLongShortHist        ◆ 응답 DTO
        └── memory/   InMemoryOrderBookAdapter, InMemoryMetricHistoryAdapter ◆ 새로
```

### 1.2 `watch` — 새 모듈

```
watch/
├── domain/
│   ├── ScheduledEvent.java     — 예정 이벤트
│   ├── EventKind.java          — FOMC / CPI / QRA / OTHER
│   ├── Importance.java         — HIGH / NORMAL
│   ├── EventCalendar.java      — 일정 묶음. "다가오는 것" 을 계산한다
│   └── Notice.java             — 거래소 공고 (제목·시각·링크)
├── application/
│   ├── port/in/   LoadCalendarUseCase, LoadNoticesUseCase
│   ├── port/out/  LoadNoticesPort
│   └── service/   CalendarService, NoticeService
└── adapter/
    ├── in/web/    WatchController
    └── out/
        ├── binance/   BinanceNoticeAdapter, BinanceCmsClientConfig, BinanceArticleList
        ├── snapshot/  ClasspathEventCalendarAdapter
        └── memory/    InMemoryNoticeAdapter
```

**캘린더에는 포트를 두지 않는다.** 구현체가 스냅샷 하나뿐이다 —
`architecture.md` 의 "구현체가 하나뿐인 인터페이스는 만들지 않는다" 그대로다.
`ClasspathEventCalendarAdapter` 는 `CalendarService` 가 직접 들고, 그것이
`ClasspathLeverageBracketAdapter` 와 같은 자리다.

**공고에는 포트를 둔다.** binance / 인메모리 둘이고, 특히 § 5.3 이 말한 대로 이 API 는
문서화된 것이 아니라 **테스트가 실제 호출에 의존하면 안 되기 때문**이다.

---

## 2. 도메인 타입

```java
// market/domain
public record PriceLevel(Price price, Quantity quantity) {}

public record OrderBook(Symbol symbol, List<PriceLevel> bids, List<PriceLevel> asks, Instant at) {
    public Price bestBid();
    public Price bestAsk();
    public Price spread();                 // bestAsk - bestBid
    public Percentage spreadPercent();     // spread / bestAsk
    public Quantity bidVolume();           // bids 잔량 합
    public Quantity askVolume();
    public BigDecimal imbalance();         // (bid - ask) / (bid + ask), 스케일 4
}

public record Ticker(Symbol symbol, Price last, Percentage change24h, Direction changeSign,
                     Price high24h, Price low24h, Quantity volume24h, Instant at) {}

public record MetricHistory(List<BigDecimal> samples) {
    public Optional<Percentile> positionOf(BigDecimal current, int minimumSamples);
}

public record Percentile(BigDecimal value) {   // 0.0000 ~ 1.0000, 스케일 4
    public boolean isOutlier();                // 상위 5% 또는 하위 5%
    public Percentage topPercent();            // (1 - value) × 100
}
```

### 2.1 분위의 동점 처리 — **중간순위(midrank)**

실제 응답을 받아 보니 **펀딩비가 연속으로 정확히 같은 값이었다**(`0.00010000` 두 번). 동점은
드문 일이 아니라 기본값이다.

```
분위 = ( 현재값보다 작은 표본 수  +  0.5 × 현재값과 같은 표본 수 ) / 전체 표본 수
```

`<` 만 세면 같은 값이 90번 나온 뒤의 현재값이 **하위 0%** 가 되고, `<=` 로 세면 **상위 0%** 가
된다. 같은 입력에서 정반대 답이 나온다. 중간순위는 그 둘의 가운데를 준다.

**최소 표본**

| 지표 | 주기 | 요청 | 최소 |
|---|---|---|---|
| 펀딩비 | 8시간 | 90 (30일) | 30 |
| 미결제약정 | 5분 | 30 | 20 |
| 롱숏비율 | 5분 | 30 | 20 |

미만이면 `Optional.empty()`. **"표본 3개 중 상위 33%" 를 만들지 않는다.**

### 2.2 `watch/domain`

```java
public record ScheduledEvent(EventKind kind, Instant at, String title, Importance importance) {
    public Duration until(Instant now);
    public boolean needsWarning(Instant now);   // HIGH 이고 D-3 이내
}

public record EventCalendar(List<ScheduledEvent> events) {
    public List<ScheduledEvent> upcoming(Instant now, int limit);  // at >= now, 오름차순
    public boolean isStale(Instant now);        // 마지막 이벤트가 90일 안
}

public record Notice(String title, Instant at, String url) {}
```

`until` 과 `needsWarning` 이 `now` 를 **인자로 받는다.** 안에서 `Instant.now()` 를 부르면
테스트가 시계에 묶이고, `crossCheck` 가 `Instant.now()` 때문에 매일 다른 표를 내던 문제를
로드맵이 이미 적어 두었다.

---

## 3. 포트

```java
// market/application/port/out
public interface LoadOrderBookPort {
    OrderBook load(Symbol symbol, int depth);
    Ticker loadTicker(Symbol symbol);
}

public interface LoadMetricHistoryPort {
    MetricHistory fundingRates(Symbol symbol, int limit);
    MetricHistory openInterest(Symbol symbol, int limit);
    MetricHistory longShortRatios(Symbol symbol, int limit);
}

// watch/application/port/out
public interface LoadNoticesPort {
    List<Notice> recent(int limit);
}
```

**`LoadOrderBookPort` 가 호가와 티커를 함께 갖는 이유**는 둘이 같은 순간의 같은 질문("지금
얼마이고 어떻게 쌓여 있나")에 답하고, 화면이 언제나 함께 읽기 때문이다. `MarketMetrics` 가
셋을 한 시각에 묶은 것과 같은 판단이다.

**`LoadMetricHistoryPort` 를 `LoadMarketMetricsPort` 와 나눈 이유**는 현재값과 이력이 **다른
엔드포인트**이고 갱신 주기도 다르기 때문이다. 한 포트에 몰면 3초 폴링이 이력까지 매번 끌어온다.

---

## 4. REST API

| 메서드 | 경로 | 응답 | 주기 |
|---|---|---|---|
| GET | `/api/markets/{symbol}/orderbook` | `OrderBookResponse` | 3초 |
| GET | `/api/markets/{symbol}/outliers` | `MetricOutliersResponse` | 5분 |
| GET | `/api/watch/events` | `EventCalendarResponse` | 1회 |
| GET | `/api/watch/notices` | `NoticeListResponse` | 60초 |

```
OrderBookResponse
  symbol, at, last, change24hPercent, high24h, low24h, volume24h,
  bestBid, bestAsk, spread, spreadPercent, bidVolume, askVolume, imbalance,
  bids[{price, quantity}], asks[{price, quantity}]

MetricOutliersResponse
  at, entries[{ metric, current, topPercent?, outlier, sampleCount }]
     metric = FUNDING_RATE | OPEN_INTEREST | LONG_SHORT_RATIO
     topPercent 는 표본 부족이면 null    ← 유일하게 null 인 필드

EventCalendarResponse
  now, stale, events[{ kind, at, title, importance, warning }]

NoticeListResponse
  at, notices[{ title, at, url }]
```

**이름 충돌을 미리 피한다.** Phase 8 이 `TradeResponse` 두 개가 조용히 덮이는 것을 브라우저에서야
발견했다. `PriceLevelResponse`·`MetricOutlierResponse`·`ScheduledEventResponse`·`NoticeResponse` 는
기존에 없는 이름이고, `SchemaNameCollisionTest` 가 매 빌드 확인한다.

**`topPercent` 만 null 이다.** Phase 8 이 손으로 세게 만들어 둔 "null 이 오는 응답 필드" 전수
목록에 이 하나를 추가한다. 그 목록을 고칠 때 "이것은 정말 null 이 오는가" 를 한 번 묻게 된다.

---

## 5. 어댑터 — 실제 응답 필드

받아 본 것 그대로다.

```
GET /fapi/v1/depth?symbol=BTCUSDT&limit=20
  { lastUpdateId, E, T, bids: [["76567.40","24.778"], …], asks: [ … ] }
  → 문자열 쌍. 첫째가 가격, 둘째가 잔량. bids 는 내림차순, asks 는 오름차순으로 온다

GET /fapi/v1/ticker/24hr?symbol=BTCUSDT
  { lastPrice, priceChangePercent, highPrice, lowPrice, volume, closeTime, … }
  → priceChangePercent 는 "-1.009" 처럼 **이미 퍼센트**다. 100 을 곱하지 않는다

GET /fapi/v1/fundingRate?symbol=BTCUSDT&limit=90
  [{ fundingTime, fundingRate: "0.00010000", markPrice, rateType }]
  → fundingRate 는 **비율**이다. 퍼센트로 보려면 ×100 (FundingRate 가 이미 그 규칙을 갖는다)

GET /futures/data/openInterestHist?symbol=BTCUSDT&period=5m&limit=30
  [{ sumOpenInterest: "107293.541", sumOpenInterestValue, timestamp }]

GET /futures/data/globalLongShortAccountRatio?symbol=BTCUSDT&period=5m&limit=30
  [{ longShortRatio: "1.0080", longAccount, shortAccount, timestamp }]

POST(GET) https://www.binance.com/bapi/apex/v1/public/apex/cms/article/list/query
        ?type=1&catalogId=48&pageNo=1&pageSize=20
  { code:"000000", success:true, data:{ catalogs:[{ catalogName, total,
      articles:[{ id, code, title, releaseDate }] }] } }
  → releaseDate 는 epoch ms. 링크는 code 로 조립: /support/announcement/{code}
```

**시각은 전부 거래소가 준 값을 쓴다.** 우리 시계로 찍지 않는다 — `BinanceServerClock` 이
33초 어긋남을 잡았던 그 이유다.

### 5.1 두 번째 RestClient

```java
@Bean("binanceCmsRestClient")
RestClient binanceCmsRestClient(BinanceCmsProperties properties)  // https://www.binance.com
```

기존 `binanceRestClient`(fapi)와 이름으로 가른다. 절대 URL 을 쓰지 않는 이유는 테스트에서
호스트를 바꿔 끼우지 못하게 되기 때문이다.

**공고 어댑터의 실패는 다른 블록을 죽이지 않는다.** 문서화되지 않은 엔드포인트이므로 예고 없이
바뀔 수 있다. `WatchController` 가 공고와 캘린더를 **다른 엔드포인트로** 내는 이유가 그것이다 —
한 응답에 묶으면 공고가 죽을 때 캘린더도 못 본다.

---

## 6. ArchUnit — 손볼 곳이 있다

**규칙 4 는 모듈 이름을 손으로 열거한다**(`architecture.md`). `watch` 가 포트/어댑터 모듈이므로
목록에 넣어야 하고, **모듈마다 위반 픽스처가 필요하다.**

```
src/archFixture/java/archfixture/r4w/watch/adapter/out/binance/BinanceNoticeAdapter.java
src/archFixture/java/archfixture/r4w/watch/application/service/LeakyNoticeService.java
```

`r4`(market) · `r4j`(journal) · `r4a`(ai) · `r4acc`(account) 옆에 `r4w` 가 선다.
넣지 않으면 **규칙 4 가 `watch` 를 보지 않고, 그러면 서비스가 어댑터를 직접 들어도 빌드가
통과한다.**

규칙 1·2·3·6 은 패키지 모양으로 걸리므로 자동으로 적용된다.

---

## 7. 프론트

```
features/watch/
├── WatchScreen.tsx        — 네 블록 배치. 블록마다 독립 쿼리
├── OrderBookPanel.tsx     — 호가 20단 + 스프레드 + 불균형
├── TickerHeader.tsx       — 현재가·24h
├── OutlierPanel.tsx       — 분위 표시. 상위/하위 5% 는 색으로
├── EventCalendarPanel.tsx — D−n 과 경고
└── NoticePanel.tsx        — 제목·시각 목록
```

- 라우트 `/watch`, 탭 **감시**. `routes.tsx` 와 `App.tsx` 에 한 줄씩.
- 폴링은 `OverviewScreen` 의 `ACCOUNT_POLL_MS` 와 **같은 규칙**을 쓴다 — 오류에서 멈추고,
  탭이 숨으면 멈추고, 새로고침 버튼이 다시 켠다. 이미 있는 `SmallButton` 을 쓴다.
- 항목마다 뜻을 적는다. 이미 있는 `shared/Term` 을 쓴다.
- **숫자를 만들지 않는다.** 스프레드·불균형·분위·남은 시간 전부 서버 값이다(`docs/adr/020`).

---

## 8. 테스트

| 층 | 무엇 |
|---|---|
| 도메인 | 분위의 **동점 처리**(전부 같은 값 / 절반 동점 / 동점 없음), 표본 부족 → `empty`, 불균형 부호, 스프레드 |
| 도메인 | `needsWarning` 경계 — D−3 정각, D−3 직전/직후, `NORMAL` 은 경고 없음 |
| 도메인 | `EventCalendar.upcoming` 이 과거를 빼고 오름차순인가 |
| 계약 | `LoadOrderBookPort` · `LoadNoticesPort` 스위트를 binance / 인메모리 **양쪽**에서 |
| 어댑터 | 저장된 응답 표본(JSON)으로 매핑 검증. **실제 호출에 의존하지 않는다** |
| 스냅샷 | 캘린더가 낡았는지 — 위반 픽스처로 검사가 실제 발동하는지 확인 |
| 프론트 | 네 블록이 **서로 독립적으로 실패**한다. 폴링 3종(주기·수동·오류정지) |
| ArchUnit | `r4w` 픽스처가 규칙 4 를 발동시키는가 |

**실제 바이낸스를 때리는 테스트는 `crossCheck` 태그로 분리한다.** `check` 는 네트워크 없이
돌아야 한다 — Phase 7 의 `liveAi` 와 같은 자리다.

---

## 9. 작업 순서

커밋 단위로 나눈다. 각 단계가 끝나면 `check` 가 통과한다.

1. **명세·설계 문서** ← 지금 여기
2. `market/domain` — `Percentile`·`MetricHistory`·`OrderBook`·`Ticker` + 테스트
   (**동점 처리부터 실패하는 테스트로 시작한다**)
3. `market` 포트 둘 + 인메모리 어댑터 + 계약 테스트
4. 바이낸스 어댑터 둘 + 저장된 응답 표본 매핑 테스트
5. `market` 엔드포인트 둘 + OpenAPI 재생성
6. `watch/domain` + 캘린더 스냅샷 + 낡음 검사
7. `watch` 공고 포트·어댑터 + 두 번째 RestClient
8. `watch` 엔드포인트 둘 + `r4w` 픽스처 + ArchUnit 규칙 4 갱신
9. 프론트 네 블록 + 폴링 + 설명
10. `architecture.md`·`scope.md`·`roadmap.md` 갱신, 브라우저 실측

---

## 10. 이 설계가 아직 답하지 못한 것

**캘린더 데이터의 출처.** FOMC 는 연준이 1년 전에 공표하고 CPI 는 BLS 일정표가 있으며 재무부
QRA 는 분기다. **그 날짜를 내가 지어내면 안 된다** — 틀린 일정으로 "명목을 줄여라" 를 띄우는
것은 아무것도 안 하는 것보다 나쁘다. 실제 일정표를 받아서 채워야 한다.

**그리고 이 화면은 손실을 막지 않는다.** 명세 § 8 이 적은 그대로다. 막는 것은 계획 화면의
사이징이고, 이 설계 어디에도 그것을 대신하는 장치는 없다.
