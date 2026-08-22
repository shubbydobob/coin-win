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
          <NavLink to="/plan" className={({ isActive }) => (isActive ? "font-medium" : "text-slate-500")}>
            계획
          </NavLink>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
