# ADR 016 — 매매 기록은 JPA + QueryDSL 로 저장한다

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

[ADR 011](011-jdbc-over-jpa-for-candles.md) 은 캔들 저장에 JPA 를 쓰지 않기로 하면서
**"이 결정은 캔들에 한한다. Phase 5 의 `journal` 은 판단이 다를 수 있고, 그때 다시 본다"**
고 적어 두었다. 지금이 그 지점이다.

저장 대상이 캔들과 다르다.

```
trade                (id) PK — 계획 · 진입 맥락 · 청산
  trade_planned_entry  (trade_id, seq) — 분할 진입 계획
  trade_fill           (trade_id, seq) — 실제 체결
```

캔들은 스칼라 한 행이었다. 거래는 **순서 있는 자식 목록 둘을 거느린 덩어리**이고, 한 거래가
계획 → 체결 → 청산의 세 상태를 지나며 같은 행이 세 번 쓰인다. 조회도 하나가 아니다 —
기간·방향·청산 이유·계획 준수 여부가 임의로 조합된다(`roadmap.md` Phase 5 의 "동적 조회").

## 결정

**`journal` 은 JPA(Hibernate)로 저장하고 동적 조회만 QueryDSL 로 만든다.**

- 엔티티는 `TradeEntity` 하나 + `@ElementCollection` 둘. 자식 목록의 순서는 `@OrderColumn(seq)`.
- 도메인 ↔ 엔티티 변환은 `TradeEntityWriter` / `TradeEntityReader` 가 맡는다.
  ArchUnit 규칙 1 이 도메인에 `@Entity` 를 다는 것을 금지하므로 **엔티티는 별개 클래스**다.
- 단건 조회는 `EntityManager.find`. 조건 없는 질의에 DSL 을 쓰면 얻는 것이 없다.
- QueryDSL 은 원본 `com.querydsl` 이 아니라 유지되는 포크
  `io.github.openfeign.querydsl:7.6` 이다. 원본은 2024-11 이후 갱신이 없다.

## 근거

**ADR 011 의 논증이 여기서는 반대 방향을 가리킨다.** 그쪽 요지는 "중복 방지를 DB 가 한
문장으로 보장한다" 였고, 그것이 성립한 이유는 저장 단위가 한 행이었기 때문이다. 거래는
부모 한 행과 자식 N 행을 함께 쓰고, 상태가 바뀔 때 자식 목록이 통째로 달라진다.
`JdbcTemplate` 으로는 **세 테이블의 쓰기 순서와 고아 행 정리를 손으로** 짜야 한다. 그 코드가
바로 ORM 이 하는 일이고, 손으로 쓴 쪽이 더 나을 이유가 없다.

**동적 조회.** 조건 다섯 개가 있으면 켜고 끄는 조합이 32가지다. 문자열 SQL 로는 조건마다
분기가 생기고, 그 분기는 테스트되지 않은 채 늘어난다. QueryDSL 은 조건이 비면 `where` 절에
아무것도 붙이지 않는 것이 기본 동작이라 분기 자체가 없어진다.

**타입 안전.** `ClosedTradePredicates` 는 계획 준수 조건을 컬럼이 아니라 `ExitReason` enum 을
훑어서 만든다. 새 청산 이유가 생기면 SQL 이 자동으로 따라온다 — 문자열 SQL 이었다면 이
파일이 조용히 뒤처지고 인메모리 어댑터와 답이 갈렸을 것이다.

**호환은 확인하고 들어갔다.** Boot 4.1 은 Hibernate 7.4.5 / jakarta.persistence 3.2 를
가져온다. QueryDSL 7.6 이 그 위에서 Q 클래스를 생성하고 실제 질의를 실행하는지를
스파이크로 먼저 확인한 뒤 진행했다.

## 한계

- **엔티티·매퍼 한 벌이 통째로 늘었다.** 규칙 1 때문에 도메인에 애너테이션을 달 수 없으므로
  피할 수 없는 비용이다. 대신 저장 형식이 도메인 모양을 끌고 가지 못한다 — 실제로 도메인의
  세 타입이 여기서 한 테이블로 뭉개지고, 그 뭉개짐이 도메인에는 새어 들어가지 않는다.
- **진입 시각 정렬을 SQL 로 하지 않는다.** 진입 시각은 첫 체결의 시각이라 자식 테이블에 있고,
  SQL 로 정렬하려면 부모 테이블에 복사해 두어야 한다. 파생값의 두 번째 사본은 언젠가 원본과
  갈라지므로, 읽어 들인 뒤 자바에서 정렬한다. SQL 쪽 정렬(`exit_at, id`)은 그 자바 정렬이
  안정적으로 같은 답을 내게 하려는 것이다. 1인 사용자 규모에서 성립하는 선택이며, 기록이
  수만 건이 되면 다시 봐야 한다.
- **`ddl-auto` 라는 우회로가 생겼다.** ADR 011 은 Hibernate 가 없어서 그 문제가 없었다.
  `application.yml` 에 `ddl-auto: none` 을 명시해 막았고, 통합 테스트는 반대로 `validate` 로
  띄워 **엔티티와 Flyway 스키마가 어긋나면 깨지게** 했다.
- **DB 없이 도는 기본 `test` 가 한 번 더 위협받았다.** Hibernate 는 기동할 때 방언을 알아내려
  JDBC 메타데이터를 읽는데 그것이 곧 접속이다. `src/test/resources/application.yml` 에서
  방언을 직접 지정하고 `hibernate.boot.allow_jdbc_metadata_access: false` 로 껐다.

## 결과

- `JpaTradeAdapter` 가 `SaveTradePort` 와 `LoadTradesPort` 를 구현한다.
- `V2__trade.sql` 이 상태와 채워진 칸의 대응을 `CHECK` 제약으로 되풀이한다. 도메인이 세
  타입으로 표현하는 것을 관계형 스키마에서 지키는 방법이다.
- **인메모리 어댑터와 JPA 어댑터가 같은 계약 스위트(`TradeRepositoryContract`)를 통과한다.**
  Phase 5 완료 조건인 "서비스 테스트가 DB 없이 인메모리 어댑터만으로 전부 통과" 가 정당한
  근거가 그것이다.

## 관련

- [011 — 캔들은 JdbcTemplate](011-jdbc-over-jpa-for-candles.md) — 같은 질문에 반대 답을 낸 이유
- [002 — 부분 헥사고날](002-partial-hexagonal.md) — 어댑터가 둘이어야 포트가 값을 한다
- `.claude/docs/roadmap.md` Phase 5
