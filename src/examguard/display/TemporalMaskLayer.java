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
 * 기본 N=3 (진짜 60Hz에서 20Hz). 근거 (2026-11-23, AI 안경 영상 33ms 노출, 노출 시작 120가지):
 *  - N=2는 30fps 영상 프레임의 50%가 깨끗하게 찍힌다(상관 0.93). N=3은 0%(최대 0.47, 감마 수정 후).
 *  - 예전 Swing Timer는 실제로 약 33Hz라 N=2가 약 17Hz로 느려져 있었고(깨끗 28%), "3·5장은 못 쓸 만큼 피곤"은
 *    11Hz·7Hz 화면에서의 관찰이었다. 진짜 20Hz의 피로는 아직 사람으로 측정하지 않았다.
 *  - 녹화 프레임 여러 장을 평균하면 여전히 복원된다 (5장 평균 N=3 0.83, N=5 0.84) — 화면으로는 못 막음
 *  - 20Hz는 광과민성 민감 대역(약 15~25Hz) 안이다. 원 밖은 정보 없는 회색 막대로 두고(ZeroInfoSpotlightLayer),
 *    시험 창에 탈출 키(F2)와 눈 휴식이 있다. 한 줄 높이 네모 창(깜빡이는 면적 56% 감소)도 시험했지만
 *    위아래 줄 맥락이 안 보여 원형으로 되돌렸다 (2026-11-23). 면적을 줄이려면 반지름을 줄인다.
 *
 * R이 0~255 안에 들어가려면 T를 회색 쪽으로 압축해야 한다(CONTRAST).
 * 대비를 낮출수록 한 장의 노이즈가 글자를 더 잘 가리지만, 사람도 읽기 어려워진다.
 *
 * 한계 (MotionNoiseLayer와 같은 계열):
 *  - 동영상 여러 프레임을 평균하면 글자가 그대로 복원된다 (사람 눈이 하는 일과 같으므로 막을 수 없음).
 *  - ExamPanel 렌더 스레드는 60Hz에 맞추지만 vsync는 보장하지 않는다 (가끔 짧은 위상 미끄러짐).
 *  - 120/144Hz 모니터는 깜빡임을 줄이지만 33ms 노출 영상에 대한 방어도 함께 사라진다 (40Hz 이상은 카메라도 섞음).
 */
public class TemporalMaskLayer extends ZeroInfoSpotlightLayer {
    public static final int[] SUBFRAME_CHOICES = {2, 3, 5};
    private static final int GRAIN = 2;
    // ponytail: 고정 대비 0.16 (글자≈108, 배경≈148). 실제 사람 가독성 실험으로 다시 맞출 값.
    private static final double CONTRAST = 0.16;
    private volatile int subframes = 3;
    private int width;
    private int height;
    private int gridW;
    private int gridSize;
    private double[] target;   // 압축된 목표 밝기 T의 빛의 세기 (0~1)
    private float[][] noise;   // [서브프레임][알갱이] 0~1 난수, 주기마다 새로 뽑음
    private long noiseCycle = -1;
    private final Random rnd = new Random();
    private BufferedImage frameImage;
    private int[] framePixels;
    private double[] pixelNoise = new double[0]; // 한 픽셀의 서브프레임별 난수 (매 픽셀 새로 만들지 않으려고 재사용)

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
        super.prepare(sharp); // 글줄 감지·회색 막대 배경
        width = sharp.getWidth();
        height = sharp.getHeight();
        int[] rgb = sharp.getRGB(0, 0, width, height, null, 0, width);
        target = new double[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            int r = (rgb[i] >> 16) & 0xFF, g = (rgb[i] >> 8) & 0xFF, b = rgb[i] & 0xFF;
            int lum = (r * 3 + g * 6 + b) / 10;
            target[i] = Gamma.toLinear((int) Math.round(128 + (lum - 128) * CONTRAST)); // 빛의 세기로 저장
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
        if (pixelNoise.length != n) pixelNoise = new double[n];
        double[] uu = pixelNoise;
        Rectangle r = area.intersection(new Rectangle(0, 0, width, height));
        for (int y = r.y; y < r.y + r.height; y++) {
            int row = (y / GRAIN) * gridW;
            for (int x = r.x; x < r.x + r.width; x++) {
                int i = y * width + x, gi = row + x / GRAIN;
                for (int j = 0; j < n; j++) uu[j] = noise[j][gi];
                int v = n == 2 ? subframe(target[i], uu[0], k == 1) : subframeN(target[i], uu, k);
                framePixels[i] = (v << 16) | (v << 8) | v;
            }
        }
        g.drawImage(frameImage, 0, 0, null);
    }

    /*
     * 쌍(주기)은 "빛의 세기" 기준으로 평균이 목표가 되게 만든다 (2026-11-23 수정).
     * 사람 눈은 빛을 평균하는데 예전에는 픽셀 값(감마 적용)으로 평균을 맞춰, 사람 눈에도 노이즈가 남았다
     * (감마 2.2 기준: 2장 표준편차 12.8·최대 55, 전체가 +15 밝게 뜸 — 글자·배경 차 약 36과 비교하면 큼).
     * t = 목표 빛의 세기(0~1), 반환값 = 화면 픽셀 값. 마지막 서브프레임이 앞 장들의 반올림 오차까지 메운다.
     */

    /** N=2: 짝수 프레임 a, 홀수 프레임 2t−a (빛 기준). */
    static int subframe(double t, double u, boolean odd) {
        double lo = Math.max(0, 2 * t - 1), hi = Math.min(1, 2 * t);
        int a = Gamma.toValue(lo + u * (hi - lo));
        return odd ? Gamma.toValue(2 * t - Gamma.toLinear(a)) : a;
    }

    /**
     * N≥3: k번째 서브프레임 빛 = t + d·(u_k - 평균 u). d는 가장 밝은·어두운 장이 0~1에 딱 닿는 최댓값.
     * (N=2에 이 식을 쓰면 늘 경계값만 나와 무작위성이 사라지므로 N=2는 subframe()을 쓴다)
     */
    static int subframeN(double t, double[] u, int k) {
        int n = u.length;
        double mean = 0, max = -1, min = 2;
        for (double v : u) {
            mean += v;
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        mean /= n;
        double d = Math.min((1 - t) / Math.max(1e-6, max - mean), t / Math.max(1e-6, mean - min));
        if (k < n - 1) return Gamma.toValue(t + d * (u[k] - mean));
        double shown = 0; // 앞 장들이 실제로 낸 빛 (반올림 후)
        for (int j = 0; j < n - 1; j++) shown += Gamma.toLinear(Gamma.toValue(t + d * (u[j] - mean)));
        return Gamma.toValue(n * t - shown);
    }
}
