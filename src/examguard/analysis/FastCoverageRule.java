package examguard.analysis;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 짧은 시간에 화면의 너무 많은 부분을 드러냈는가?
 * 화면을 24px 칸으로 나누고, 스포트라이트 원 안에 들어온 칸을 1초 단위로 기록한 뒤
 * 연속 5초 동안 드러난 칸의 비율이 가장 큰 값을 본다.
 *
 * 기준선: 한 줄을 끝까지 읽으면 "원 지름 × 화면 너비" 띠만큼은 자연스럽게 드러난다.
 * 그래서 반경에 따라 정상 범위를 다르게 잡는다 (반경이 크면 기준선이 올라감).
 * 반경이 화면 높이의 40%를 넘으면 원 하나가 화면 대부분을 덮어 이 규칙으로는 판단할 수 없다.
 */
public class FastCoverageRule implements ScanRule {
    private static final int CELL = 24;
    private static final int WINDOW_SEC = 5;
    private static final double MARGIN = 0.10;  // 한 줄 띠 + 여유(앞 줄 다시 읽기 등)
    private static final double SCAN_SPAN = 0.30; // 기준선보다 이만큼 더 노출하면 만점

    @Override
    public String getName() {
        return "빠른 화면 노출";
    }

    @Override
    public String getDescription() {
        return "5초 안에 화면의 몇 %를 드러냈는지";
    }

    /** 마우스든 방향키든 스크롤이든, 드러난 영역은 드러난 영역이다. */
    @Override
    public boolean usesAllInputs() {
        return true;
    }

    @Override
    public RuleResult evaluate(List<Sample> samples, int width, int height) {
        if (samples.isEmpty()) return new RuleResult(getName(), 0, "데이터 없음");

        int cols = (width + CELL - 1) / CELL;
        int rows = (height + CELL - 1) / CELL;
        int totalCells = cols * rows;
        long t0 = samples.get(0).getTime();
        double radiusSum = 0;

        TreeMap<Long, BitSet> perSecond = new TreeMap<>();
        for (Sample p : samples) {
            long key = (p.getTime() - t0) / 1000;
            BitSet cells = perSecond.computeIfAbsent(key, k -> new BitSet(totalCells));
            int r = p.getRadius();
            radiusSum += r;
            int x = Math.max(0, Math.min(width - 1, p.getX()));
            int y = Math.max(0, Math.min(height - 1, p.getY()));
            int c0 = Math.max(0, (x - r) / CELL), c1 = Math.min(cols - 1, (x + r) / CELL);
            int r0 = Math.max(0, (y - r) / CELL), r1 = Math.min(rows - 1, (y + r) / CELL);
            for (int row = r0; row <= r1; row++) {
                for (int col = c0; col <= c1; col++) {
                    double cx = col * CELL + CELL / 2.0, cy = row * CELL + CELL / 2.0;
                    if (Math.hypot(cx - x, cy - y) <= r) cells.set(row * cols + col);
                }
            }
        }

        double best = 0;
        for (Long start : perSecond.keySet()) {
            BitSet union = new BitSet(totalCells);
            for (Map.Entry<Long, BitSet> e : perSecond.subMap(start, true, start + WINDOW_SEC - 1, true).entrySet()) {
                union.or(e.getValue());
            }
            best = Math.max(best, union.cardinality() / (double) totalCells);
        }

        double avgRadius = radiusSum / samples.size();
        if (avgRadius > height * 0.4) {
            return new RuleResult(getName(), 0,
                    String.format("반경(%.0fpx)이 너무 커서 판단 불가 · 최대 %.0f%% 노출", avgRadius, best * 100));
        }
        double baseline = Math.min(0.9, 2 * avgRadius / height) + MARGIN;
        double score = (best - baseline) / SCAN_SPAN;
        return new RuleResult(getName(), score,
                String.format("%d초 동안 최대 %.0f%% 노출 (정상 기준 %.0f%%)", WINDOW_SEC, best * 100, baseline * 100));
    }
}
