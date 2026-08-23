import { NavLink, Outlet } from "react-router";

/**
 * 앱 껍데기. 화면은 `routes.tsx` 가 끼운다.
 *
 * **탭이 셋이다.** 여섯이던 것을 "무엇을 묻는가" 로 갈랐다 — 지금 무슨 일이 벌어지나(지금),
 * 얼마나 걸고 무엇을 했나(매매), 이 규칙이 통하나(검증). 여섯일 때는 같은 집계가 두 탭에,
 * 같은 세 지표가 두 탭에 있었다.
 *
 * **헤더가 붙어 있다.** 스크롤이 길어지는 화면(호가·공지 목록)에서 탭이 화면 밖으로 나가면
 * 다른 탭으로 가려고 매번 맨 위로 올라가야 한다.
 *
 * 폭을 넓혔다(`max-w-5xl` → `max-w-6xl`). 호가와 계산 결과가 두 단으로 놓이는 화면이 생겼고,
 * 좁으면 그 둘이 세로로 쌓여 한눈에 안 들어온다.
 */
export function App() {
  return (
    <div className="min-h-screen bg-bg">
      <header className="sticky top-0 z-10 border-b border-line bg-bg/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-6 px-6 py-3">
          <div className="flex items-baseline gap-2">
            {/* 포인트색은 이 점 하나와 주 버튼에만 쓴다. 강조가 흔해지면 강조가 아니다. */}
            <span className="size-2 rounded-full bg-accent" aria-hidden="true" />
            <h1 className="text-base font-semibold tracking-tight">CoinWin</h1>
            <span className="hidden text-xs text-ink-3 sm:inline">비트코인 선물 매매 보조</span>
          </div>

          <nav className="flex gap-1 text-sm">
            {[
              { to: "/", label: "지금" },
              { to: "/trade", label: "매매" },
              { to: "/verify", label: "검증" },
            ].map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === "/"}
                className={({ isActive }) =>
                  `rounded-md px-3 py-1.5 transition-colors ${
                    isActive
                      ? "bg-surface-2 font-medium text-ink"
                      : "text-ink-3 hover:bg-surface hover:text-ink-2"
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-6 py-6">
        <Outlet />
      </main>
    </div>
  );
}
