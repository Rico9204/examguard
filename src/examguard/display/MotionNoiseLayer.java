package examguard.display;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

/**
 * 움직임 노이즈 표시 (시분할 표시의 개선형, 실험 기능).
 *
 * 원 안의 모든 픽셀은 매 프레임 무작위 점(노이즈)으로만 그려진다.
 * - 배경 부분의 노이즈는 멈춰 있고
 * - 글자 부분의 노이즈는 아래로 계속 흐른다.
 * 사람 눈은 "같이 움직이는 점들"을 묶어서 글자 모양으로 인식하지만,
 * 사진 한 장에는 그냥 무작위 점만 찍힌다.
 *
 * 처음 아이디어(글자를 두 조각으로 나눠 번갈아 표시)는 각 조각에 글자 윤곽이 남아
 * 한 장으로도 읽힐 수 있어서, 프레임 하나만 봐서는 정보가 전혀 없는 이 방식으로 바꿨다.
 * 한계: 동영상을 찍어 프레임끼리 비교하면 글자를 복원할 수 있다.
 *
 * 구현 메모: 프로토타입은 Swing Timer(약 60fps)로 그린다. 모니터 주사율에 정확히 맞추려면
 * Canvas + BufferStrategy 렌더 루프로 바꾼다.
 */
public class MotionNoiseLayer extends SpotlightLayer {
    private static final int GRAIN = 2;          // 노이즈 알갱이 크기(px)
    private static final int SPEED = 1;          // 프레임당 이동 칸 수
    private static final int DARK = 0x3C3C3C;    // 대비를 일부러 낮춤 (눈부심·깜빡임 완화)
    private static final int LIGHT = 0xD4D4D4;

    private int width;
    private int height;
    private int gridW;
    private int gridH;
    private boolean[] mask;
    private int[] textNoise;
    private int[] backgroundNoise;
    private BufferedImage frameImage;
    private int[] framePixels;

    @Override
    public String getName() {
        return "부분 표시 + 움직임 노이즈 (실험)";
    }

    @Override
    public boolean isAnimated() {
        return true;
    }

    @Override
    public boolean prefersLargeText() {
        return true; // 가는 획은 노이즈로 알아보기 어려워서 크고 굵게
    }

    @Override
    public void prepare(BufferedImage sharp) {
        width = sharp.getWidth();
        height = sharp.getHeight();

        boolean[] raw = new boolean[width * height];
        int[] rgb = sharp.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < rgb.length; i++) {
            int r = (rgb[i] >> 16) & 0xFF, g = (rgb[i] >> 8) & 0xFF, b = rgb[i] & 0xFF;
            raw[i] = (r * 3 + g * 6 + b) / 10 < 150;
        }
        // 획을 1px 두껍게 (노이즈 알갱이가 획 안에 충분히 들어가도록)
        mask = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean on = false;
                for (int dy = -1; dy <= 1 && !on; dy++) {
                    for (int dx = -1; dx <= 1 && !on; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < width && ny < height && raw[ny * width + nx]) on = true;
                    }
                }
                mask[y * width + x] = on;
            }
        }

        gridW = width / GRAIN + 1;
        gridH = height / GRAIN + 1;
        Random rnd = new Random();
        textNoise = new int[gridW * gridH];
        backgroundNoise = new int[gridW * gridH];
        for (int i = 0; i < textNoise.length; i++) {
            textNoise[i] = rnd.nextBoolean() ? DARK : LIGHT;
            backgroundNoise[i] = rnd.nextBoolean() ? DARK : LIGHT;
        }
        frameImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        framePixels = ((DataBufferInt) frameImage.getRaster().getDataBuffer()).getData();
    }

    @Override
    protected void drawRevealed(Graphics2D g, RenderContext ctx, Rectangle area) {
        if (mask == null) {
            super.drawRevealed(g, ctx, area);
            return;
        }
        Rectangle r = area.intersection(new Rectangle(0, 0, width, height));
        int offset = (int) ((ctx.getFrame() * SPEED) % gridH);
        for (int y = r.y; y < r.y + r.height; y++) {
            int gy = y / GRAIN;
            int movingRow = ((gy - offset) % gridH + gridH) % gridH; // 글자 노이즈는 아래로 흐름
            for (int x = r.x; x < r.x + r.width; x++) {
                int gx = x / GRAIN;
                int i = y * width + x;
                framePixels[i] = mask[i] ? textNoise[movingRow * gridW + gx] : backgroundNoise[gy * gridW + gx];
            }
        }
        g.drawImage(frameImage, 0, 0, null);
    }
}
