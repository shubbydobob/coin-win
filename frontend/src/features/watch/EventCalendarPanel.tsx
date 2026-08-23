import { dday, instant } from "../../format";
import type { components } from "../../api/schema";

type Calendar = components["schemas"]["EventCalendarResponse"];

type Event = components["schemas"]["ScheduledEventResponse"];

/**
 * 무엇이 예정돼 있나.
 *
 * **이 블록이 이 화면의 존재 이유에 가장 가깝다.** 재무부 자금조달계획에서 나온 바이백 확대
 * 소식이 급등을 만들었고, 그 급등에서 원금의 69% 가 사라졌다. 그 인과는 자동으로 도출되지
 * 않지만 **발표가 언제인지는 미리 공표된다.**
 *
 * **경고는 규모에 대한 것이다.** "그날은 명목을 줄여라" 이지 "오를 것이다" 가 아니다.
 * 방향은 이 프로젝트가 말하지 않는다.
 *
 * `stale` 이면 목록보다 그 경고를 먼저 보여 준다 — **낡은 일정표의 증상은 오류가 아니라
 * 침묵**이라, 비어 보이는 것과 "다가오는 큰 일이 없다" 가 구별되지 않는다.
 */
export function EventCalendarPanel({ calendar }: { calendar: Calendar }) {
  return (
    <section aria-label="예정 이벤트" className="rounded-lg border border-line bg-surface p-3">
      <h2 className="text-sm font-medium text-ink">예정 이벤트</h2>
      <p className="mt-0.5 text-xs leading-snug text-ink-3">
        발표가 무엇을 뜻할지는 미리 알 수 없지만 <b>언제인지는 알 수 있다.</b> 경고는 규모에
        대한 것이다 — 그날은 명목을 줄이라는 뜻이지 방향에 대한 말이 아니다.
      </p>

      {calendar.stale && (
        <p className="mt-2 rounded border border-warn/50 bg-warn/10 p-2 text-xs text-warn">
          일정표가 낡았다 — 이 목록을 믿으면 안 된다.{" "}
          <code>src/main/resources/watch/scheduled-events.json</code> 의 <code>_source</code> 를
          보고 갱신한다.
        </p>
      )}

      {calendar.events.length === 0 ? (
        <p className="mt-2 text-sm text-ink-2">예정된 이벤트가 없다</p>
      ) : (
        <ul className="mt-3 space-y-1.5">
          {calendar.events.map((event) => (
            <li key={`${event.kind}-${event.at}`}>
              <Row event={event} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function Row({ event }: { event: Event }) {
  return (
    <div
      className={`flex items-baseline gap-3 rounded p-1.5 text-sm ${
        event.warning ? "bg-warn/10" : ""
      }`}
    >
      {/* D-day 로 읽는다. "19일 0시간 13분" 은 사람이 앞의 숫자만 떼어 다시 세게 만든다. */}
      <span
        className={`w-20 shrink-0 text-right tabular-nums ${
          event.warning ? "font-semibold text-warn" : "text-ink-2"
        }`}
      >
        {dday(event.until)}
      </span>
      <span className="w-32 shrink-0 tabular-nums text-ink-2">{instant(event.at)}</span>
      <span className="w-12 shrink-0 text-xs text-ink-3">{event.kind}</span>
      <span className="flex-1">{event.title}</span>
      {event.warning && (
        <span className="shrink-0 text-xs text-warn">명목을 줄인다</span>
      )}
    </div>
  );
}
