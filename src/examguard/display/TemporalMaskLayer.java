package examguard.display;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

/**
 * 시간차 픽셀 마스킹 (실험 기능).
 *
 * 원 안의 각 픽셀을 N장의 서브프레임으로 쪼개 차례로 그린다. 한 주기(N프레임)의 평균은 원래 밝기 T와 같아서
 * 사람 눈(시간 적분)에는 글자가 보이지만, 짧은 노출로 찍은 한 장에는 T를 중심으로 한 무작위 값만 남는다.
 *   N=2: 짝수 프레임 R(무작위), 홀수 프레임 2T-R
 *   N=3·5: T + d·(u_k - 평균 u). d는 모든 값이 0~255 안에 들어가는 최댓값
 *
 * 장수(N)를 늘리면 (카메라 시뮬레이션 결과, AI 안경 영상 기준):
 *  - 30fps 노출 한 번(화면 2프레임)이 한 주기를 다 덮지 못해 "깨끗하게 찍히는 프레임"이 사라진다
 *    (N=2: 0.95/0.25 → N=3·5: 어느 타이밍이든 0.3~0.4)
 *  - 하지만 녹화 프레임 여러 장을 평균하면 여전히 복원된다 (5장 평균 N=3 0.87, N=5 0.95)
 *  - 60Hz에서 한 주기가 길어져(N=3 20Hz, N=5 12Hz) 깜빡임이 보이고 광과민성 위험이 커진다
 *
 * R이 0~255 안에 들어가려면 T를 회색 쪽으로 압축해야 한다(CONTRAST).
 * 대비를 낮출수록 한 장의 노이즈가 글자를 더 잘 가리지만, 사람도 읽기 어려워진다.
 *
 * 한계 (MotionNoiseLayer와 같은 계열):
 *  - 동영상 여러 프레임을 평균하면 글자가 그대로 복원된다 (사람 눈이 하는 일과 같으므로 막을 수 없음).
 *  - Swing Timer는 모니터 주사율에 맞춰 그리지 못한다. 프레임이 밀리면 깜빡임이 보이고
 *    한 서브프레임이 오래 남아 효과가 약해진다. 제대로 하려면 BufferStrategy 전체화면 + 120Hz 이상.
 *  - 깜빡임 때문에 광과민성 위험이 있다.
 */
public class TemporalMaskLayer extends SpotlightLayer {
    public static final int[] SUBFRAME_CHOICES = {2, 3, 5};
    private static final int GRAIN = 2;
    // ponytail: 고정 대비 0.16 (글자≈108, 배경≈148). 실제 사람 가독성 실험으로 다시 맞출 값.
    private static final double CONTRAST = 0.16;

    private volatile int subframes = 2;
    private int width;
    private int height;
    private int gridW;
    private int gridSize;
    private int[] target;      // 압축된 목표 밝기 T
    private float[][] noise;   // [서브프레임][알갱이] 0~1 난수, 주기마다 새로 뽑음
    private long noiseCycle = -1;
    private final Random rnd = new Random();
    private BufferedImage frameImage;
    private int[] framePixels;

    @Override
    public String getName() {
        return "부분 표시 + 시간차 픽셀 마스킹 (실험)";
    }

    /** 한 주기의 서브프레임 수 (2, 3, 5) */
    public void setSubframes(int n) {
        subframes = n;
    }

    public int getSubframes() {
        return subframes;
    }

    @Override
    public boolean isAnimated() {
        return true;
    }

    @Override
    public boolean prefersLargeText() {
        return true; // 대비가 낮아서 크고 굵게
    }

    @Override
    public void prepare(BufferedImage sharp) {
        width = sharp.getWidth();
        height = sharp.getHeight();
        int[] rgb = sharp.getRGB(0, 0, width, height, null, 0, width);
        target = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            int r = (rgb[i] >> 16) & 0xFF, g = (rgb[i] >> 8) & 0xFF, b = rgb[i] & 0xFF;
            int lum = (r * 3 + g * 6 + b) / 10;
            target[i] = (int) Math.round(128 + (lum - 128) * CONTRAST);
        }
        gridW = width / GRAIN + 1;
        gridSize = gridW * (height / GRAIN + 1);
        noise = null;
        frameImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        framePixels = ((DataBufferInt) frameImage.getRaster().getDataBuffer()).getData();
    }

    @Override
    protected void drawRevealed(Graphics2D g, RenderContext ctx, Rectangle area) {
        if (target == null) {
            super.drawRevealed(g, ctx, area);
            return;
        }
        int n = subframes;
        long cycle = ctx.getFrame() / n;
        if (noise == null || noise.length != n) { // 장수를 바꾸면 새로 만든다
            noise = new float[n][gridSize];
            noiseCycle = -1;
        }
        if (cycle != noiseCycle) { // 한 주기 안의 서브프레임은 같은 난수를 써야 평균이 T가 된다
            for (float[] row : noise) for (int i = 0; i < row.length; i++) row[i] = rnd.nextFloat();
            noiseCycle = cycle;
        }
        int k = (int) (ctx.getFrame() % n);
        Rectangle r = area.intersection(new Rectangle(0, 0, width, height));
        for (int y = r.y; y < r.y + r.height; y++) {
            int row = (y / GRAIN) * gridW;
            for (int x = r.x; x < r.x + r.width; x++) {
                int i = y * width + x, gi = row + x / GRAIN;
                int v = n == 2 ? subframe(target[i], noise[0][gi], k == 1) : subframeN(target[i], noise, gi, k);
                framePixels[i] = (v << 16) | (v << 8) | v;
            }
        }
        g.drawImage(frameImage, 0, 0, null);
    }

    /** N=2: 목표 밝기 t를 서브프레임 값으로. 짝수·홀수 프레임 값의 합은 항상 2t (평균 = t). */
    static int subframe(int t, float u, boolean odd) {
        int lo = Math.max(0, 2 * t - 255), hi = Math.min(255, 2 * t);
        int a = lo + Math.round(u * (hi - lo));
        return odd ? 2 * t - a : a;
    }

    /**
     * N≥3: k번째 서브프레임 값 = t + d·(u_k - 평균 u). 평균에서 벗어난 정도를 그대로 쓰므로 N장 평균은 t
     * (반올림 때문에 최대 0.5 차이). d는 가장 밝은·어두운 서브프레임이 0~255에 딱 닿는 최댓값.
     * (N=2에 이 식을 쓰면 늘 경계값만 나와 무작위성이 사라지므로 N=2는 subframe()을 쓴다)
     */
    static int subframeN(int t, float[][] u, int gi, int k) {
        double mean = 0, max = -1, min = 2;
        for (float[] row : u) {
            mean += row[gi];
            max = Math.max(max, row[gi]);
            min = Math.min(min, row[gi]);
        }
        mean /= u.length;
        double d = Math.min((255 - t) / Math.max(1e-6, max - mean), t / Math.max(1e-6, mean - min));
        return (int) Math.max(0, Math.min(255, Math.round(t + d * (u[k][gi] - mean))));
    }
}
