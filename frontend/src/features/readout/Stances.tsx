import { SideChip, type ChipSide } from "../../shared/SideChip";
import type { components } from "../../api/schema";

type Series = components["schemas"]["IndicatorSeriesResponse"];

/**
 * 지표마다 <b>지금 어느 쪽에 서 있는가.</b>
 *
 * <b>세되 우열은 내지 않는다.</b> 감시 화면의 「붐비는 쪽」과 같은 태도다 — 다섯 중 셋이
 * 롱 쪽이라는 것은 사실이고, 그래서 롱이 유리하다는 것은 사실이 아니다. 이 저장소는 그 부류의
 * 전제를 7년 15,110봉에서 반증했다(`docs/adr/021`).
 *
 * <b>판정은 서버가 한다.</b> 화면이 세 이동평균을 비교해 "정배열" 이라고 적으면 그 규칙이
 * 화면에 생기고, 그러면 같은 규칙이 백테스트나 연구 쪽과 갈라진다.
 *
 * <b>말할 수 없는 것은 세지 않는다.</b> 봉이 모자란 지표는 딱지 없이 이유만 적는다 —
 * 중립으로 세면 "가운데 있다" 는 없는 사실이 생긴다.
 */
export function Stances({ stances }: { stances: Series["stances"] }) {
  return (
    <section aria-label="지표가 선 자리" className="mt-2 rounded bg-surface-2 px-2 py-1.5">
      <div className="flex items-center gap-2 text-xs">
        <span className="text-ink-3">지표가 선 자리</span>
        <span className="ml-auto text-[10px] text-ink-4">
          선 자리일 뿐 방향이 아니다 — 7년에서 오히려 반대로 나왔다
        </span>
      </div>

      {Object.entries(FAMILY_WORD).map(([family, 이름]) => (
        <StanceFamily
          key={family}
          이름={이름}
          stances={stances.filter((stance) => stance.family === family)}
        />
      ))}

      <p className="mt-1.5 text-[10px] leading-snug text-ink-4">
        <b>두 무리를 따로 센다.</b> 종가가 밴드 상단 위인 것은 되돌림에게 과열이고 추세에게
        돌파인데 <b>둘 다 「위」로 적힌다</b> — 다섯을 한 번에 세면 같은 사실이 두 번 세어지거나
        반대 사실이 상쇄된다.
      </p>

      {/*
        **정의를 화면 안에 둔다.** 접혀 있어도 이 화면 밖으로 나가지 않는 것이 요점이다 —
        다른 문서로 옮기면 찾아보지 않는 사람에게는 없는 것과 같다.
      */}
      <details className="group mt-1.5">
        <summary className="cursor-pointer list-none text-[11px] text-ink-4 hover:text-ink-3">
          지표가 무엇을 재는가
          <span aria-hidden="true" className="group-open:hidden">{" ▸"}</span>
          <span aria-hidden="true" className="hidden group-open:inline">{" ▾"}</span>
        </summary>
        <dl className="mt-1.5 space-y-1.5 text-[11px] leading-snug text-ink-4">
          {Object.entries(정의).map(([이름, 뜻]) => (
            <div key={이름}>
              <dt className="inline font-medium text-ink-3">{이름} — </dt>
              <dd className="inline">{뜻}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-2 text-[11px] leading-snug text-ink-4">
          <b>딱지가 유리한 쪽을 가리키지 않는다 — 재 봤다.</b> 4시간봉 7년(15,110봉)에서 각
          딱지 다음의 수익률을 세었더니 <b>롱 딱지 쪽이 기준선보다 나빴다.</b> 24시간 뒤 승률이
          기준선 51.68% 인데 일목 롱 49.87%(−1.81%p) · RSI 롱 50.30%(−1.38%p) ·
          MACD 롱 50.40%(−1.28%p) 이고, 숏 딱지가 그만큼 높다. <b>다섯 지표에서 방향이 같다.</b>
        </p>
        <p className="mt-1.5 text-[11px] leading-snug text-ink-4">
          그렇다고 <b>반대로 하면 된다는 뜻도 아니다.</b> 차이가 가장 큰 자리도 승률 +1.9%p ·
          중앙값 0.17% 인데 <b>왕복 수수료·슬리피지가 0.14% 안팎</b>이라 남는 것이 거의 없고,
          다중검정 보정도 국면 분리도 하지 않은 수다. 그래서 이 화면은 <b>어느 쪽에 서 있는가
          까지만 말한다.</b> 재는 명령은 <code>gradlew crossCheck --tests "*StanceCrossCheck*"</code>.
        </p>
      </details>
    </section>
  );
}

/**
 * 한 무리가 선 자리. <b>세는 것은 무리 안에서만 뜻이 있다.</b>
 *
 * 추세 무리와 되돌림 무리는 **같은 사실에 반대 뜻을 붙인다** — 종가가 밴드 상단 위인 것은
 * 되돌림에게 과열이고 추세에게 돌파인데 둘 다 「위」로 적힌다. 그래서 다섯을 한 번에 세면
 * 같은 사실이 두 번 세어지거나 반대 사실이 상쇄된다. 근거는 `docs/spec/indicator-usage.md` § 2.
 *
 * <b>어느 무리가 옳은지는 말하지 않는다.</b> 그것은 아직 재 본 적이 없고, 연구가 찾은 것은
 * 2020~2023 과 2024~2026 에서 **관계의 부호가 뒤집혔다**는 사실까지다.
 *
 * <b>무리를 합친 수를 만들지 않는다.</b> 합치는 순간 이 갈라 놓음이 아무 일도 하지 않는다.
 */
function StanceFamily({
  이름,
  stances,
}: {
  이름: string;
  stances: Series["stances"];
}) {
  const 위 = stances.filter((stance) => stance.stance === "LONG").length;
  const 아래 = stances.filter((stance) => stance.stance === "SHORT").length;

  return (
    <section aria-label={`${이름} 무리`} className="mt-1.5">
      <div className="flex items-center gap-2 text-xs">
        <span className="w-16 shrink-0 text-ink-3">{이름}</span>
        {/*
          **센 수에 무리 이름을 붙인다.** 같은 「위」 딱지가 화면에 둘 있으므로 이름이 없으면
          읽는 쪽에서 둘이 구별되지 않는다 — 눈으로는 줄이 갈라 주지만 소리로는 그렇지 않다.
        */}
        <SideChip side="LONG" word={STANCE_WORD.LONG}>
          <span aria-label={`${이름} 위`}>{위}</span>
        </SideChip>
        <SideChip side="SHORT" word={STANCE_WORD.SHORT}>
          <span aria-label={`${이름} 아래`}>{아래}</span>
        </SideChip>
      </div>
      <dl className="mt-1 divide-y divide-line-soft">
        {stances.map((stance) => (
          <div key={stance.indicator} className="flex items-baseline gap-2 py-1 text-xs">
            {/*
              **마우스 툴팁은 덤이다.** 이 저장소는 뜻을 툴팁에 숨기지 않기로 했다
              (`shared/Term`) — 올리지 않는 사람에게는 없는 것과 같기 때문이다. 그래서 같은
              정의를 아래 「지표가 무엇을 재는가」에 펼칠 수 있게 두고, 툴팁은 이미 아는
              사람이 빠르게 확인하는 용도로만 붙인다.
            */}
            <dt className="w-16 shrink-0 text-ink-3" title={정의[stance.indicator]}>
              {stance.indicator}
            </dt>
            <dd className="flex flex-wrap items-baseline gap-1.5">
              {STANCE_CHIP[stance.stance] && (
                <SideChip
                  side={STANCE_CHIP[stance.stance]!}
                  word={STANCE_WORD[STANCE_CHIP[stance.stance]!]}
                />
              )}
              <span className={stance.stance === "UNKNOWN" ? "text-ink-4" : "text-ink-2"}>
                {stance.statement}
              </span>
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/**
 * 무리에 적는 말. **순서가 화면 순서다.**
 *
 * 이름을 서버가 보내지 않는 이유는 이것이 **번역이지 판단이 아니기** 때문이다 — 서버가
 * 보내는 것은 `TREND` / `REVERSION` 이라는 사실이고, 그것을 무엇이라 부를지는 화면의 몫이다.
 * `STANCE_WORD` 가 「위 / 아래」를 정하는 것과 같은 자리다.
 */
const FAMILY_WORD: Record<string, string> = {
  TREND: "추세",
  REVERSION: "되돌림",
};

/**
 * 지표가 무엇을 재는가. <b>정의만 적고 판단하지 않는다.</b>
 *
 * "RSI 가 70 이면 과열이니 조심하라" 같은 문장은 이 화면이 하지 않기로 한 일이다. 적는 것은
 * 서버가 그 수를 어떻게 세는가까지이고, 그 계산은 전부 트레이딩뷰 원문과 대조해 확정했다.
 */
const 정의: Record<string, string> = {
  일목: "9봉·26봉 중간값으로 전환선과 기준선을 만들고, 그 둘의 평균과 52봉 중간값을 25봉 앞으로 밀어 구름을 그린다. 변위 26 이 실제로는 25봉을 미는 것까지 트레이딩뷰 원문으로 확정했다.",
  볼린저: "20봉 단순이동평균에 표준편차의 2배를 더하고 뺀 띠. 표준편차는 모집단 기준이다. 폭이 좁아지면 최근 움직임이 작았다는 뜻이고, 그 다음이 무엇인지는 말하지 않는다.",
  이동평균: "종가의 단순 평균. 10·20·50·200·300 을 그린다. 20 은 볼린저 중심선과 같은 값이다. 짧은 것이 긴 것 위에 놓이면 최근 가격이 예전보다 높다는 뜻이고, 그것이 계속된다는 뜻은 아니다.",
  RSI: "오른 폭의 평균 ÷ 내린 폭의 평균을 0~100 으로 옮긴 것. 14봉이고 평활은 와일더 방식(RMA)이다 — EMA 로 짜면 값이 조금씩 다르면서 그럴듯해 보인다.",
  MACD: "12봉 EMA 에서 26봉 EMA 를 뺀 값과, 그것의 9봉 EMA(시그널). 막대는 둘의 차다. 가격이 아니라 가격의 차라 음수가 될 수 있다.",
};

/**
 * 딱지에 적는 말. **`롱 / 숏` 이 아니라 `위 / 아래` 다.**
 *
 * 다섯 지표가 재는 것은 전부 **무엇의 위인가 아래인가**다 — 구름 위, 밴드 위, 50 위,
 * 시그널 위, 짧은 이동평균이 위. 그것은 **자리에 대한 사실**이고 거기까지가 이 화면이 아는
 * 것이다.
 *
 * **`롱` 이라는 글자는 사람에게 "이쪽으로 가라" 로 읽힌다.** 그런데 4시간봉 7년(15,110봉)에서
 * 재 보니 그 자리들이 가리키는 방향은 **오히려 반대**였다 — 구름 위에서 하루 뒤 오를 확률이
 * 49.9%, 구름 아래에서 53.6% 로 기준선(51.7%)을 사이에 두고 갈렸다. 차이가 작아 매매에 쓸
 * 수는 없지만(왕복 비용 0.14% 를 못 넘는다) **적어도 `롱` 이라고 부를 근거는 없다.**
 *
 * 상태를 지우지는 않았다. 자리를 아는 것은 모르는 것보다 낫고, 무엇보다 그 자리가 **손절을
 * 어디에 둘지**의 재료다. 바꾼 것은 이름뿐이다.
 *
 * 색은 그대로 둔다 — `SideChip` 이 초록·빨강을 쓰는 것은 이 저장소가 상승을 초록으로 쓰기
 * 때문이지 좋다는 뜻이 아니고, 그 설명은 그쪽 주석에 이미 있다.
 */
const STANCE_WORD: Record<ChipSide, string> = {
  LONG: "위",
  SHORT: "아래",
  NEUTRAL: "가운데",
};

/** 중립과 말할 수 없음에는 딱지가 없다 — 둘 다 "어느 쪽" 이 아니기 때문이다. */
const STANCE_CHIP: Record<string, ChipSide | undefined> = {
  LONG: "LONG",
  SHORT: "SHORT",
};
