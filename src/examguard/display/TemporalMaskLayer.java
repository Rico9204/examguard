package examguard.display;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

/**
 * 시간차 픽셀 마스킹 (실험 기능).
 *
 * 원 안의 각 픽셀을 두 장의 서로 보완하는 서브프레임으로 쪼개 번갈아 그린다.
 *   짝수 프레임: R (무작위),  홀수 프레임: 2T - R   (T = 원래 밝기)
 * 두 장의 평균은 정확히 T라서 사람 눈(시간 적분)에는 글자가 보이지만,
 * 짧은 노출로 찍은 사진 한 장에는 T를 중심으로 한 무작위 값만 남는다.
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
    private static final int GRAIN = 2;
    // ponytail: 고정 대비 0.16 (글자≈108, 배경≈148). 실제 사람 가독성 실험으로 다시 맞출 값.
    private static final double CONTRAST = 0.16;

    private int width;
    private int height;
    private int gridW;
    private int[] target;      // 압축된 목표 밝기 T
    private float[] noise;     // 서브프레임 쌍마다 새로 뽑는 0~1 난수 (알갱이 단위)
    private long noisePair = -1;
    private final Random rnd = new Random();
    private BufferedImage frameImage;
    private int[] framePixels;

    @Override
    public String getName() {
        return "부분 표시 + 시간차 픽셀 마스킹 (실험)";
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
        noise = new float[gridW * (height / GRAIN + 1)];
        noisePair = -1;
        frameImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        framePixels = ((DataBufferInt) frameImage.getRaster().getDataBuffer()).getData();
    }

    @Override
    protected void drawRevealed(Graphics2D g, RenderContext ctx, Rectangle area) {
        if (target == null) {
            super.drawRevealed(g, ctx, area);
            return;
        }
        long pair = ctx.getFrame() / 2;
        if (pair != noisePair) { // 한 쌍(짝수·홀수 프레임)은 같은 난수를 써야 평균이 T가 된다
            for (int i = 0; i < noise.length; i++) noise[i] = rnd.nextFloat();
            noisePair = pair;
        }
        boolean odd = (ctx.getFrame() & 1) == 1;
        Rectangle r = area.intersection(new Rectangle(0, 0, width, height));
        for (int y = r.y; y < r.y + r.height; y++) {
            int row = (y / GRAIN) * gridW;
            for (int x = r.x; x < r.x + r.width; x++) {
                int i = y * width + x;
                int v = subframe(target[i], noise[row + x / GRAIN], odd);
                framePixels[i] = (v << 16) | (v << 8) | v;
            }
        }
        g.drawImage(frameImage, 0, 0, null);
    }

    /** 목표 밝기 t를 서브프레임 값으로. 짝수·홀수 프레임 값의 합은 항상 2t (평균 = t). */
    static int subframe(int t, float u, boolean odd) {
        int lo = Math.max(0, 2 * t - 255), hi = Math.min(255, 2 * t);
        int a = lo + Math.round(u * (hi - lo));
        return odd ? 2 * t - a : a;
    }
}
