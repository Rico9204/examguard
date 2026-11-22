package examguard.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 시연·테스트용 가짜 궤적.
 * 실제 임계값은 팀원과 친구들의 진짜 궤적을 모아서 다시 정해야 한다.
 */
public final class TrajectorySimulator {
    private static final int TICK = 12; // ms

    private TrajectorySimulator() {}

    /** 한 줄씩 읽는 사람: 멈춤(고정) → 조금 이동 → 가끔 되돌아감 → 줄 끝에서 다음 줄 처음으로 */
    public static List<Sample> reading(int w, int h, int radius, long start, int question, long seed) {
        Random rnd = new Random(seed);
        List<Sample> out = new ArrayList<>();
        long t = start;
        double x;
        int lineGap = 30;
        int rereads = 0;
        for (double lineY = 70; lineY < h - 40; lineY += lineGap) {
            x = 40 + rnd.nextInt(20);
            double y = lineY;
            while (x < w - 60) {
                int fixation = 180 + rnd.nextInt(300);
                for (int k = 0; k < fixation; k += TICK) {
                    if (rnd.nextDouble() < 0.12) {
                        out.add(sample(x + rnd.nextInt(3) - 1, y + rnd.nextInt(3) - 1, t, question, radius, w, h));
                    }
                    t += TICK;
                }
                double dx = rnd.nextDouble() < 0.12 ? -(30 + rnd.nextInt(50)) : 30 + rnd.nextInt(60);
                int steps = (120 + rnd.nextInt(220)) / TICK;
                for (int s = 0; s < steps; s++) {
                    x += dx / steps;
                    y += rnd.nextGaussian() * 0.8;
                    out.add(sample(x, y, t, question, radius, w, h));
                    t += TICK;
                }
            }
            double fromX = x;
            for (int s = 1; s <= 8; s++) {
                out.add(sample(fromX - (fromX - 40) * s / 8.0, lineY + lineGap * s / 8.0, t, question, radius, w, h));
                t += TICK;
            }
            if (rereads < 2 && lineY > 100 && rnd.nextDouble() < 0.15) {
                lineY -= lineGap; // 앞 줄을 다시 읽음
                rereads++;
            }
            t += 200 + rnd.nextInt(400);
        }
        return out;
    }

    /** 녹화하려는 사람: 지그재그로 빠르게 화면 전체를 훑는다 */
    public static List<Sample> sweep(int w, int h, int radius, long start, int question) {
        Random rnd = new Random(start);
        List<Sample> out = new ArrayList<>();
        long t = start;
        double speed = 700; // px/s
        double step = speed * TICK / 1000.0;
        boolean leftToRight = true;
        double rowGap = radius * 1.4;
        for (double y = radius * 0.7; y < h + radius * 0.3; y += rowGap) {
            double x0 = leftToRight ? 20 : w - 20;
            double x1 = leftToRight ? w - 20 : 20;
            double dir = Math.signum(x1 - x0);
            for (double x = x0; dir > 0 ? x < x1 : x > x1; x += dir * step) {
                out.add(sample(x, y + rnd.nextGaussian(), t, question, radius, w, h));
                t += TICK;
            }
            for (double d = 0; d < rowGap; d += step) { // 다음 줄로 내려감
                out.add(sample(x1, y + d, t, question, radius, w, h));
                t += TICK;
            }
            leftToRight = !leftToRight;
        }
        return out;
    }

    /** 편의 제공 모드에서 방향키로 읽기: → 키를 띄엄띄엄 누르며 한 줄씩, 줄 끝에서 ↓ 두 번 + ← 길게 */
    public static List<Sample> keyReading(int w, int h, int radius, long start, int question, long seed) {
        Random rnd = new Random(seed);
        List<Sample> out = new ArrayList<>();
        long t = start;
        int step = 16;
        int y = 70;
        while (y < h - 40) {
            for (int x = 40; x < w - 60; x += step) {
                out.add(new Sample(x, y, t, question, radius, Sample.Source.KEY));
                t += 250 + rnd.nextInt(500); // 한 번 누르고 읽고 또 누름
            }
            for (int k = 0; k < 2; k++) { y += step; out.add(new Sample(w - 60, y, t, question, radius, Sample.Source.KEY)); t += 300; }
            for (int x = w - 60; x > 40; x -= step) { // ← 키를 누르고 있음 (자동 반복 33ms)
                out.add(new Sample(x, y, t, question, radius, Sample.Source.KEY));
                t += 33;
            }
            t += 400;
        }
        return out;
    }

    /** 방향키로 녹화하려는 사람: 키를 누르고 있어서 자동 반복으로 화면 전체를 지그재그로 훑는다 */
    public static List<Sample> keySweep(int w, int h, int radius, long start, int question) {
        List<Sample> out = new ArrayList<>();
        long t = start;
        int step = 16;
        boolean right = true;
        for (int y = radius / 2; y < h; y += (int) (radius * 1.4)) {
            if (right) for (int x = 20; x < w - 20; x += step) { out.add(new Sample(x, y, t, question, radius, Sample.Source.KEY)); t += 33; }
            else for (int x = w - 20; x > 20; x -= step) { out.add(new Sample(x, y, t, question, radius, Sample.Source.KEY)); t += 33; }
            right = !right;
        }
        return out;
    }

    private static Sample sample(double x, double y, long t, int q, int r, int w, int h) {
        int ix = (int) Math.max(0, Math.min(w - 1, Math.round(x)));
        int iy = (int) Math.max(0, Math.min(h - 1, Math.round(y)));
        return new Sample(ix, iy, t, q, r, Sample.Source.MOUSE);
    }
}
