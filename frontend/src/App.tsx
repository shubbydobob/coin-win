import { NavLink, Outlet } from "react-router";

/**
 * 앱 껍데기. 화면은 `routes.tsx` 가 끼운다.
 */
export function App() {
  return (
    <div className="mx-auto max-w-5xl p-8">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold">CoinWin</h1>
        <p className="mt-1 text-sm text-slate-600">비트코인 선물 매매 보조 도구</p>
        <nav className="mt-4 flex gap-4 text-sm">
          {[
            { to: "/", label: "현황" },
            { to: "/plan", label: "계획" },
            { to: "/journal", label: "기록" },
            { to: "/backtest", label: "백테스트" },
            { to: "/projection", label: "복리" },
          ].map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) => (isActive ? "font-medium" : "text-slate-500")}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
