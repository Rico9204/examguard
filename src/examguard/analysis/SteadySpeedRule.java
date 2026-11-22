package examguard.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * 속도가 너무 일정한 구간이 있는가?
 * 글을 읽을 때 마우스는 멈칫하고 빨라지고 되돌아가서 속도가 들쭉날쭉하다.
 * 녹화하려고 훑을 때는 비슷한 속도로 쭉 움직인다.
 *
 * 0.1초 단위 이동 속도를 구하고, 3초 구간마다 속도 변동계수(표준편차/평균)를 계산한다.
 * 시험 전체 평균을 쓰면 오래 읽다가 잠깐 훑은 경우가 묻히므로, 가장 일정했던 구간을 본다.
 * 3초 중 1.5초 이상 계속 움직인 구간만 계산한다 (읽을 때는 대부분 멈춰 있어서 거의 해당 없음).
 */
public class SteadySpeedRule implements ScanRule {
    private static final long CHUNK_MS = 100;
    private static final long GAP_MS = 250;
    private static final long WINDOW_MS = 3000;
    private static final double MIN_SPEED = 40;   // px/s 미만은 "멈춤"으로 보고 제외
    private static final int MIN_CHUNKS = 15;     // 3초 중 1.5초 이상 이동
    // 시뮬레이션 기준: 읽기의 가장 일정한 구간 0.27~0.35, 훑기 0.01~0.08.
    // 실제 사람 데이터로 다시 맞춰야 한다 (4주차 실험).
    private static final double CV_NORMAL = 0.45; // 이 이상이면 0점
    private static final double CV_SCAN = 0.15;   // 이 이하면 만점

    @Override
    public String getName() {
        return "속도 일정함";
    }

    @Override
    public String getDescription() {
        return "이동 속도가 기계적으로 일정한 구간이 있는지";
    }

    @Override
    public RuleResult evaluate(List<Sample> samples, int width, int height) {
        List<long[]> chunks = new ArrayList<>(); // {끝 시각, 속도}
        double dist = 0;
        long elapsed = 0;
        for (int i = 1; i < samples.size(); i++) {
            Sample a = samples.get(i - 1), b = samples.get(i);
            long dt = b.getTime() - a.getTime();
            if (dt <= 0) continue;
            if (dt > GAP_MS) { dist = 0; elapsed = 0; continue; }
            dist += Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
            elapsed += dt;
            if (elapsed >= CHUNK_MS) {
                double v = dist / elapsed * 1000;
                if (v > MIN_SPEED) chunks.add(new long[]{b.getTime(), Math.round(v)});
                dist = 0;
                elapsed = 0;
            }
        }

        double bestCv = Double.MAX_VALUE;
        double bestMean = 0;
        int j = 0;
        for (int i = 0; i < chunks.size(); i++) {
            if (j < i) j = i;
            while (j + 1 < chunks.size() && chunks.get(j + 1)[0] - chunks.get(i)[0] <= WINDOW_MS) j++;
            int count = j - i + 1;
            if (count < MIN_CHUNKS) continue;
            double mean = 0;
            for (int k = i; k <= j; k++) mean += chunks.get(k)[1];
            mean /= count;
            double var = 0;
            for (int k = i; k <= j; k++) var += (chunks.get(k)[1] - mean) * (chunks.get(k)[1] - mean);
            double cv = Math.sqrt(var / count) / mean;
            if (cv < bestCv) {
                bestCv = cv;
                bestMean = mean;
            }
        }
        if (bestCv == Double.MAX_VALUE) {
            return new RuleResult(getName(), 0, "1.5초 이상 계속 움직인 구간 없음");
        }
        double score = (CV_NORMAL - bestCv) / (CV_NORMAL - CV_SCAN);
        return new RuleResult(getName(), score,
                String.format("가장 일정한 3초 구간의 속도 변동계수 %.2f (평균 %.0f px/s)", bestCv, bestMean));
    }
}
