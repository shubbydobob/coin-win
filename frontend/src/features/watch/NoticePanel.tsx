import { instant } from "../../format";
import type { components } from "../../api/schema";

type Notices = components["schemas"]["NoticeListResponse"];

/**
 * 거래소가 최근에 낸 공지.
 *
 * **매크로 뉴스가 아니다.** 상장·상장폐지·점검 같은 거래소 자체 소식이고, 재무부 발표 같은
 * 것은 여기 걸리지 않는다 — 그쪽은 예정 이벤트가 담당한다. 그 구분을 화면에도 적는 이유는,
 * 적지 않으면 사람이 이 목록을 "뉴스" 로 읽고 여기 없는 것을 없는 일로 여기기 때문이다.
 *
 * **분류하지 않는다.** 제목을 거래소가 쓴 그대로 두고 호재·악재를 붙이지 않는다.
 */
export function NoticePanel({ notices }: { notices: Notices }) {
  return (
    <section aria-label="거래소 공지" className="rounded-lg border border-line bg-surface p-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-medium text-ink">거래소 공지</h2>
        <span className="text-xs text-ink-2">받은 시각 {instant(notices.at)}</span>
      </div>
      <p className="mt-0.5 text-xs leading-snug text-ink-3">
        상장·상장폐지·점검 같은 <b>거래소 자체 소식</b>이다. 매크로 뉴스는 여기 오지 않는다 —
        그쪽은 위의 예정 이벤트가 담당한다.
      </p>

      {notices.notices.length === 0 ? (
        <p className="mt-2 text-sm text-ink-2">최근 공지가 없다</p>
      ) : (
        <ul className="mt-3 space-y-1">
          {notices.notices.map((notice) => (
            <li key={notice.url} className="flex items-baseline gap-3 text-sm">
              <span className="w-32 shrink-0 tabular-nums text-ink-2">
                {instant(notice.at)}
              </span>
              <a
                href={notice.url}
                target="_blank"
                rel="noreferrer"
                className="flex-1 text-ink underline decoration-slate-300 underline-offset-2"
              >
                {notice.title}
              </a>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
