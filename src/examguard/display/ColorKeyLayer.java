package examguard.display;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 색 열쇠 고정 노이즈 (실험 기능). "사용자에게 열쇠를 주는" 방식 중 소프트웨어만으로 되는 것.
 *
 * 문제를 열쇠 색 채널(예: 빨강)에만 싣고, 나머지 두 채널은 강한 고정 노이즈로 채운다.
 * 같은 색 셀로판 안경을 쓰면 열쇠 채널만 보여 문제가 깨끗하게 읽힌다 (어린이용 "빨간 렌즈 해독기"와 같은 원리).
 * 맨눈에 글자가 밝기 차이로 비치지 않도록, 열쇠 채널이 어두워진 만큼 나머지 채널을 밝혀 밝기를 맞춘다.
 * 화면은 움직이지 않는다 (깜빡임 없음 → 시간차 마스킹보다 피로가 훨씬 적다).
 *
 * 열쇠 색과 노이즈는 시험 비밀키에서 만든 시드로 정해 시험마다 바뀐다. 감독관이 그 색의 셀로판 안경을 배포한다.
 *
 * 한계 (README "실험 기능" 참고):
 *  - 컬러 카메라는 색 채널을 따로 기록하므로, 찍은 뒤 채널만 나눠 보면 해독된다. 열쇠도 3가지뿐이라 다 시도해 볼 수 있다.
 *  - 밝기 보정 때문에 다른 색 채널에도 글자가 희미하게 섞인다 (틀린 색 셀로판으로도 어렴풋이 보임).
 *  - 맨눈에도 색 차이(색조)로 글자가 비칠 수 있다. 색각 이상이 있는 응시자는 불리할 수 있다.
 *  - 실제 셀로판은 이상적인 필터가 아니다 (Cellophane 참고).
 */
public class ColorKeyLayer implements ProtectionLayer {
    private static final int GRAIN = 4;          // 노이즈 알갱이 크기(px). 글자 획 굵기와 비슷해야 획을 잘 가린다
    // ponytail: 열쇠 채널에서 글자와 배경의 차이. 맨눈 은폐 ↔ 셀로판 가독성의 맞교환 (육안 비교 결과):
    //   110 맨눈으로도 색 차이로 읽힘 · 60 맨눈에 어렴풋이 보임 · 40 맨눈엔 줄 위치 정도, 셀로판으론 읽힘 · 25 셀로판으로도 흐림
    //   실제 모니터·셀로판으로 다시 맞출 값.
    private static final int KEY_CONTRAST = 40;

    private final Cellophane key;
    private final long noiseSeed;
    private BufferedImage encoded;

    /**
     * 열쇠로 쓰는 색. 초록은 뺀다: 초록이 밝기의 60%를 차지해 맨눈 밝기를 맞추려면 나머지 채널을 크게 보정해야 하고,
     * 그 보정이 노이즈보다 커서 틀린 색 셀로판으로도 글자가 거의 그대로 보인다 (SelfTest 측정: 상관 -0.88).
     */
    public static final Cellophane[] KEYS = {Cellophane.RED, Cellophane.BLUE};

    /** examSeed: 시험 비밀키에서 만든 시드 (ExamGenerator.examSeed) */
    public ColorKeyLayer(long examSeed) {
        this.key = KEYS[(int) Math.floorMod(examSeed, (long) KEYS.length)];
        this.noiseSeed = examSeed;
    }

    /** 이 시험의 지정 셀로판 색 */
    public Cellophane getKey() {
        return key;
    }

    @Override
    public String getName() {
        return "색 열쇠 고정 노이즈 · 셀로판 안경 (실험)";
    }

    @Override
    public boolean prefersLargeText() {
        return true; // 셀로판을 끼면 화면이 어두워져서 크고 굵게
    }

    @Override
    public void prepare(BufferedImage sharp) {
        int w = sharp.getWidth(), h = sharp.getHeight();
        Cellophane[] others = othersOf(key);
        double wa = others[0].weight(), wb = others[1].weight();
        // 밝기 보정 최댓값만큼 노이즈 범위를 비워 둔다 (0~255를 넘지 않게)
        double maxComp = key.weight() * KEY_CONTRAST / (wa + wb);
        int noiseRange = (int) Math.floor(255 - maxComp);

        int gw = w / GRAIN + 1, gh = h / GRAIN + 1;
        Random rnd = new Random(noiseSeed); // 같은 시험이면 같은 노이즈 (고정)
        int[] na = new int[gw * gh], nb = new int[gw * gh];
        for (int i = 0; i < na.length; i++) {
            na[i] = rnd.nextInt(noiseRange + 1);
            nb[i] = rnd.nextInt(noiseRange + 1);
        }

        int[] rgb = sharp.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[rgb.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x, gi = (y / GRAIN) * gw + x / GRAIN;
                int p = rgb[i];
                int lum = (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10;
                int k = 255 - (int) Math.round(KEY_CONTRAST * (255 - lum) / 255.0); // 글자일수록 어둡게
                // 열쇠 채널이 어두워진 만큼 나머지 채널을 밝혀 맨눈 밝기를 일정하게
                int comp = (int) Math.round(key.weight() * (255 - k) / (wa + wb));
                int a = Math.min(255, na[gi] + comp), b = Math.min(255, nb[gi] + comp);
                out[i] = (k << key.shift()) | (a << others[0].shift()) | (b << others[1].shift());
            }
        }
        encoded = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        encoded.setRGB(0, 0, w, h, out, 0, w);
    }

    @Override
    public void paint(Graphics2D g, RenderContext ctx) {
        g.drawImage(encoded != null ? encoded : ctx.getSharp(), 0, 0, null);
    }

    private static Cellophane[] othersOf(Cellophane key) {
        Cellophane[] r = new Cellophane[2];
        int n = 0;
        for (Cellophane c : Cellophane.values()) if (c != key) r[n++] = c;
        return r;
    }
}
