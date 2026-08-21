# ADR 011 — 캔들 저장에 JPA 대신 JdbcTemplate 을 쓴다

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

`architecture.md` 의 패키지 구조에는 `adapter/out/persistence/` 에 `JpaCandleAdapter` 가
적혀 있었다. Phase 3 에서 실제로 만들면서 이 선택을 다시 봤다.

캔들 테이블은 다음과 같다.

```
(symbol, candle_interval, open_time)  PK
open_price, high_price, low_price, close_price, volume
```

객체 그래프가 없다. 연관도, 지연 로딩도, 상속도 없다. 접근 패턴도 하나뿐이다 —
"이 종목·이 주기의 이 시간 구간" 을 시간순으로 읽고, 받아 온 묶음을 증분 저장한다.

Phase 3 의 완료 조건은 **"캔들 증분 저장에 중복 없음"** 이다.

## 결정

**캔들 저장은 `JdbcTemplate` 과 PostgreSQL `ON CONFLICT` 로 한다.** Hibernate 를 이 Phase 에
들이지 않는다. `architecture.md` 의 `JpaCandleAdapter` 는 `JdbcCandleAdapter` 로 바꾼다.

JPA 는 Phase 5(매매 기록)에서 들어온다. 그쪽은 QueryDSL 기반 동적 조회가 필요하고
(`roadmap.md` Phase 5), 그때가 ORM 이 값을 하는 자리다.

## 근거

**완료 조건을 무엇이 보장하는가.** JPA 로 저장하면 assigned ID 를 가진 엔티티마다
`select` 후 `insert`/`update` 가 나간다. 중복이 안 생기는 근거가 "Hibernate 의 merge 동작이
그렇게 돈다" 가 된다. `ON CONFLICT ON CONSTRAINT candle_pk` 는 **기본키가 한 문장 안에서**
보장한다. 애플리케이션이 검사를 빠뜨려도, 다른 경로로 SQL 이 들어와도 DB 가 거부한다.

증거의 질이 다르다. 전자는 ORM 설정에 대한 신뢰이고 후자는 스키마 제약이다.

**부수적으로 얻는 것.** 1500개 묶음을 저장할 때 select 가 1500번 나가지 않는다. 그리고
Hibernate 가 클래스패스에 없으므로, DB 없이 도는 기본 `test` 태스크에서 `EntityManagerFactory`
가 기동 시 커넥션을 잡으려 드는 문제를 애초에 만들지 않는다.

**YAGNI.** ADR 002 의 "구현체가 하나뿐인 인터페이스는 만들지 않는다" 와 같은 성격의 판단이다.
지금 필요한 것은 upsert 한 문장이지 영속성 컨텍스트가 아니다.

## 한계

- 이 결정은 **캔들에 한한다.** Phase 5 의 `journal` 은 판단이 다를 수 있고, 그때 다시 본다.
- SQL 이 PostgreSQL 전용이 된다(`ON CONFLICT`, `TIMESTAMPTZ`). 이미 `testing.md` 가
  "H2 금지, Testcontainers 로 실제 PostgreSQL" 을 요구하므로 새로 생기는 제약은 아니다.
- 두 문장을 쓴다. 새로 저장된 수를 세기 위해 저장 전에 한 번 센다. 사용자가 한 명이라
  그 사이에 다른 쓰기가 끼어들 여지가 없다는 전제 위에 있다. 이 전제가 깨지면
  `RETURNING (xmax = 0)` 으로 한 문장에 합쳐야 한다.

## 결과

- `JdbcCandleAdapter` 가 `LoadCandlesPort` 와 `SaveCandlesPort` 를 구현한다.
- 스키마는 Flyway 만 만든다. Hibernate 가 없으므로 `ddl-auto` 라는 우회로 자체가 없다.
- `V1__candle.sql` 이 기본키 외에 캔들 불변식(`high >= low` 등)을 `CHECK` 로 되풀이한다.
  도메인과 중복이 아니라 이중 방어다 — 어댑터를 거치지 않는 경로가 언젠가 생긴다.

## 관련

- [002 — 부분 헥사고날](002-partial-hexagonal.md) — 같은 성격의 YAGNI 판단
- `.claude/docs/architecture.md` — `JpaCandleAdapter` 표기를 이 결정에 맞춰 고쳤다
- `roadmap.md` Phase 5 — JPA / QueryDSL 이 실제로 들어오는 지점
