package examguard.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 시험 중 마우스 궤적을 모은다. 감독 대시보드가 주기적으로 snapshot()을 읽어 간다. */
public class TrajectoryRecorder {
    private final List<Sample> samples = new ArrayList<>();
    private volatile int areaWidth;
    private volatile int areaHeight;

    public synchronized void record(Sample s) {
        samples.add(s);
    }

    public synchronized void addAll(Collection<Sample> list) {
        samples.addAll(list);
    }

    public synchronized List<Sample> snapshot() {
        return new ArrayList<>(samples);
    }

    public synchronized void clear() {
        samples.clear();
    }

    public synchronized long lastTime() {
        return samples.isEmpty() ? System.currentTimeMillis() : samples.get(samples.size() - 1).getTime();
    }

    /** 문제 화면 크기 (화면 노출 비율 계산에 쓴다) */
    public void setArea(int width, int height) {
        this.areaWidth = width;
        this.areaHeight = height;
    }

    public int getAreaWidth() { return areaWidth; }
    public int getAreaHeight() { return areaHeight; }
}
