/**
 * 라벨과 그 뜻.
 *
 * **뜻을 툴팁이 아니라 화면에 적는다.** 이 도구를 쓰는 사람은 한 명이고, 그 한 명이 보는
 * 목적은 "지금 어떤 상태인가" 를 **되짚지 않고** 읽는 것이다. 마우스를 올려야 나오는 설명은
 * 되짚는 것과 같은 비용이 들고, 그러면 대개 되짚지 않은 채로 넘어간다 —
 * `PositionReconciliation` 의 `ADVICE` 가 "불일치" 만 띄우지 않는 것과 같은 판단이다.
 *
 * **설명은 정의만 적고 판단하지 않는다.** "펀딩비가 높으니 조심하라" 같은 문장은 이 화면이
 * 하지 않기로 한 일이다(`scope.md` — AI 든 사람이 쓴 문장이든 매매 판단은 내지 않는다).
 * 적는 것은 서버가 그 수를 어떻게 세는가까지다.
 *
 * `<dt>` 를 내므로 부르는 쪽의 `<dl>` 격자에 그대로 들어간다. `<dd>` 는 부르는 쪽이 낸다 —
 * 값의 형식은 `format/` 이 정하고 이 컴포넌트는 이름만 다룬다.
 */
export function Term({ label, hint }: { label: string; hint: string }) {
  return (
    <dt className="text-slate-500">
      {label}
      <span className="mt-0.5 block text-xs leading-snug text-slate-400">{hint}</span>
    </dt>
  );
}
