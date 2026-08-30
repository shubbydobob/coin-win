import type {
  IPrimitivePaneRenderer,
  IPrimitivePaneView,
  ISeriesPrimitive,
  PrimitivePaneViewZOrder,
  SeriesAttachedParameter,
  Time,
  UTCTimestamp,
} from "lightweight-charts";

/**
 * 일목 구름대를 **선 둘이 아니라 칠해진 띠**로 그린다.
 *
 * 선행스팬 A 와 B 사이가 구름이고, **어느 쪽이 위인가가 곧 구름의 방향**이다. 선으로만 그리면
 * 그 사실을 읽으려고 두 선을 눈으로 좇아야 하고, 둘이 여러 번 교차하면 어디서 뒤집혔는지가
 * 사라진다. 칠하면 색이 바뀌는 지점이 그대로 전환점이다.
 *
 * **이것은 "색을 가진 무리는 이동평균 하나뿐" 이라는 규칙을 어기는 것이 아니다.** 그 규칙은
 * *선*의 규칙이고 구름은 배경이다 — 알파를 낮게 두어 캔들 뒤에 깔리고(`zOrder: "bottom"`),
 * 어떤 선과도 굵기나 색으로 경쟁하지 않는다.
 *
 * 라이브러리에 "두 계열 사이를 칠하는" 기능이 없어서 캔버스에 직접 그린다. 좌표는 차트에
 * 물어본다(`timeToCoordinate` · `priceToCoordinate`) — 우리가 계산하면 축이 움직일 때마다
 * 어긋난다.
 *
 * **화면 밖의 점은 좌표가 `null` 이라 그 구간은 그리지 않는다.** 그래서 양 끝에서 최대 한 봉
 * 폭만큼 구름이 짧아 보일 수 있다. 값이 틀리는 것이 아니라 가장자리가 잘리는 것이다.
 */
export type 구름점 = { time: UTCTimestamp; a: number; b: number };

/** 선행스팬 A 가 위(양운). 캔들 초록과 같은 계열이되 배경이므로 알파가 낮다. */
const 양운 = "rgba(14, 203, 129, 0.13)";

/** 선행스팬 B 가 위(음운). */
const 음운 = "rgba(246, 70, 93, 0.13)";

type 화면점 = { x: number; ya: number; yb: number };

export class IchimokuCloud implements ISeriesPrimitive<Time> {
  private 점들: 구름점[] = [];

  private 붙은것: SeriesAttachedParameter<Time> | null = null;

  private readonly 뷰: IPrimitivePaneView[] = [new 구름뷰(this)];

  attached(param: SeriesAttachedParameter<Time>): void {
    this.붙은것 = param;
  }

  detached(): void {
    this.붙은것 = null;
  }

  paneViews(): readonly IPrimitivePaneView[] {
    return this.뷰;
  }

  updateAllViews(): void {
    // 뷰가 그릴 때마다 좌표를 다시 물으므로 여기서 캐시할 것이 없다.
  }

  setData(점들: 구름점[]): void {
    this.점들 = 점들;
    this.붙은것?.requestUpdate();
  }

  get 데이터(): 구름점[] {
    return this.점들;
  }

  get 부모(): SeriesAttachedParameter<Time> | null {
    return this.붙은것;
  }
}

class 구름뷰 implements IPrimitivePaneView {
  constructor(private readonly 구름: IchimokuCloud) {}

  zOrder(): PrimitivePaneViewZOrder {
    return "bottom";
  }

  renderer(): IPrimitivePaneRenderer | null {
    return this.구름.데이터.length > 1 ? new 구름렌더러(this.구름) : null;
  }
}

class 구름렌더러 implements IPrimitivePaneRenderer {
  constructor(private readonly 구름: IchimokuCloud) {}

  draw(target: Parameters<IPrimitivePaneRenderer["draw"]>[0]): void {
    const 부모 = this.구름.부모;
    if (!부모) {
      return;
    }
    const 시간축 = 부모.chart.timeScale();
    const 계열 = 부모.series;

    target.useMediaCoordinateSpace(({ context }) => {
      const 화면 = this.구름.데이터.map((점): 화면점 | null => {
        const x = 시간축.timeToCoordinate(점.time);
        const ya = 계열.priceToCoordinate(점.a);
        const yb = 계열.priceToCoordinate(점.b);
        return x === null || ya === null || yb === null ? null : { x, ya, yb };
      });

      for (let i = 0; i + 1 < 화면.length; i += 1) {
        const 앞 = 화면[i];
        const 뒤 = 화면[i + 1];
        if (앞 && 뒤) {
          칠하기(context, 앞, 뒤);
        }
      }
    });
  }
}

/**
 * 두 점 사이를 칠한다. **뒤집히는 구간은 교차점에서 자른다.**
 *
 * 자르지 않고 사각형 하나로 칠하면 나비넥타이 모양이 되어 한쪽이 반대 색으로 채워진다 —
 * 전환점이 정확히 어디인가가 이 그림의 요점이므로 거기서 뭉개면 안 된다.
 *
 * 화면 좌표는 **아래로 갈수록 크다.** 그래서 `yb − ya > 0` 이 "A 가 위" 곧 양운이다.
 */
function 칠하기(context: CanvasRenderingContext2D, 앞: 화면점, 뒤: 화면점): void {
  const 앞차 = 앞.yb - 앞.ya;
  const 뒤차 = 뒤.yb - 뒤.ya;
  const 뒤집힘 = 앞차 !== 0 && 뒤차 !== 0 && 앞차 > 0 !== 뒤차 > 0;

  if (!뒤집힘) {
    context.fillStyle = 앞차 + 뒤차 >= 0 ? 양운 : 음운;
    다각형(context, [
      [앞.x, 앞.ya],
      [뒤.x, 뒤.ya],
      [뒤.x, 뒤.yb],
      [앞.x, 앞.yb],
    ]);
    return;
  }

  const 비율 = 앞차 / (앞차 - 뒤차);
  const 교차x = 앞.x + 비율 * (뒤.x - 앞.x);
  const 교차y = 앞.ya + 비율 * (뒤.ya - 앞.ya);

  context.fillStyle = 앞차 > 0 ? 양운 : 음운;
  다각형(context, [
    [앞.x, 앞.ya],
    [교차x, 교차y],
    [앞.x, 앞.yb],
  ]);
  context.fillStyle = 뒤차 > 0 ? 양운 : 음운;
  다각형(context, [
    [교차x, 교차y],
    [뒤.x, 뒤.ya],
    [뒤.x, 뒤.yb],
  ]);
}

function 다각형(context: CanvasRenderingContext2D, 꼭짓점: [number, number][]): void {
  context.beginPath();
  꼭짓점.forEach(([x, y], i) => (i === 0 ? context.moveTo(x, y) : context.lineTo(x, y)));
  context.closePath();
  context.fill();
}
