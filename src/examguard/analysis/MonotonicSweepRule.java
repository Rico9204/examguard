package examguard.analysis;

import java.util.List;

/**
 * 짧은 시간에 한 방향으로 쭉 쓸어내리는가?
 * 4초 안에 화면 높이의 절반 이상을 세로로 움직인 구간을 찾고,
 * 그 구간의 세로 이동이 얼마나 한 방향(위→아래 또는 아래→위)이었는지 본다.
 * 읽는 사람은 한 줄을 읽는 데 몇 초가 걸려서 이런 구간이 거의 없다.
 */
public class MonotonicSweepRule implements ScanRule {
    private static final long WINDOW_MS = 4000;
    private static final long GAP_MS = 250;
    private static final double MIN_TRAVEL_RATIO = 0.5; // 화면 높이 대비
    private static final double RATIO_NORMAL = 0.6;
    private static final double RATIO_SCAN = 0.95;

    @Override
    public String getName() {
        return "한 방향 쓸어내리기";
    }

    @Override
    public String getDescription() {
        return "짧은 시간에 위에서 아래로 쭉 훑는지";
    }

    /** 방향키(↓)를 누르고 있거나 휠로 쭉 내려도 같은 행동이다. */
    @Override
    public boolean usesAllInputs() {
        return true;
    }

    @Override
    public RuleResult evaluate(List<Sample> samples, int width, int height) {
        int n = samples.size();
        if (n < 2) return new RuleResult(getName(), 0, "데이터 없음");

        double[] net = new double[n];   // 세로 이동 누적 (부호 포함)
        double[] total = new double[n]; // 세로 이동 누적 (절댓값)
        for (int i = 1; i < n; i++) {
            Sample a = samples.get(i - 1), b = samples.get(i);
            double dy = (b.getTime() - a.getTime() > GAP_MS) ? 0 : b.getY() - a.getY();
            net[i] = net[i - 1] + dy;
            total[i] = total[i - 1] + Math.abs(dy);
        }

        double best = -1;
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (j < i) j = i;
            while (j + 1 < n && samples.get(j + 1).getTime() - samples.get(i).getTime() <= WINDOW_MS) j++;
            double travel = total[j] - total[i];
            if (travel >= height * MIN_TRAVEL_RATIO) {
                best = Math.max(best, Math.abs(net[j] - net[i]) / travel);
            }
        }
        if (best < 0) {
            return new RuleResult(getName(), 0, "4초 안에 화면 절반 이상 세로 이동한 구간 없음");
        }
        double score = (best - RATIO_NORMAL) / (RATIO_SCAN - RATIO_NORMAL);
        return new RuleResult(getName(), score,
                String.format("빠른 세로 이동 구간의 한 방향 비율 최대 %.0f%%", best * 100));
    }
}
