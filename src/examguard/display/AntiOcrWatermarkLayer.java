package examguard.display;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

/**
 * 안티-OCR 셰이딩 + 화면 워터마크 (실험 기능).
 *
 * 1) 셰이딩: 화면 전체에 1px 간격 미세 격자를 옅게 깐다. 카메라 센서 격자(Bayer CFA)·모니터 서브픽셀과
 *    간섭해 촬영본에 모아레 줄무늬가 생기도록 유도한다. 격자는 반드시 "모니터 실제 픽셀" 단위로 그린다
 *    (Windows 배율 125%처럼 확대되면 보간으로 패턴이 뭉개지므로 변환을 풀고 그림).
 * 2) 워터마크: 수험자 식별 코드를 비스듬히 옅게 반복해서 깐다. 촬영본이 그대로 퍼지면 사진에서 바로
 *    누구의 시험지인지 읽을 수 있다 (텍스트 유출 추적의 보완).
 *
 * 한계: 모아레 강도는 촬영 거리·각도·초점에 따라 크게 달라져 OCR을 확실히 막지 못한다.
 *       워터마크는 잘라 내거나 편집으로 지울 수 있고, 문제를 옮겨 적으면 사라진다.
 *       논문 수준의 "강인성 워터마크"(주파수 영역 삽입, 오류 정정)는 구현하지 않았다.
 */
public class AntiOcrWatermarkLayer extends SpotlightLayer {
    /** 격자 색. RGB는 세로줄을 빨강·초록·파랑 순서로 칠해 모니터 서브픽셀·카메라 색 필터와 간섭시키려는 것 */
    public enum GridColor {
        BLACK("검정 (가로·세로)"), RGB("RGB 세로 줄무늬");

        private final String label;
        GridColor(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static final int DEFAULT_PERIOD = 2;
    public static final int DEFAULT_ALPHA = 26;   // 대비 약 10%
    private static final Color MARK = new Color(30, 60, 160);
    private static final float MARK_ALPHA = 0.09f;

    private volatile String watermark = "";
    // 실험 조절값 (시험 창의 [격자 조절]에서 바꾼다). 실제 카메라로 모아레가 생기는 조건을 찾기 위한 손잡이.
    private volatile int gridPeriod = DEFAULT_PERIOD; // 모니터 실제 픽셀 단위 주기
    private volatile int gridAlpha = DEFAULT_ALPHA;   // 0~255, 선이 얼마나 진한가
    private volatile GridColor gridColor = GridColor.BLACK;

    public void setGrid(int period, int alpha, GridColor color) {
        gridPeriod = Math.max(2, period);
        gridAlpha = Math.max(0, Math.min(255, alpha));
        gridColor = color;
    }

    public int getGridPeriod() { return gridPeriod; }
    public int getGridAlpha() { return gridAlpha; }
    public GridColor getGridColor() { return gridColor; }

    @Override
    public String getName() {
        return "부분 표시 + 안티-OCR 셰이딩·워터마크 (실험)";
    }

    @Override
    public void setWatermark(String text) {
        watermark = text == null ? "" : text;
    }

    @Override
    public void paint(Graphics2D g, RenderContext ctx) {
        super.paint(g, ctx);
        int w = ctx.getSharp().getWidth(), h = ctx.getSharp().getHeight();
        drawGrid(g, w, h);
        drawWatermark(g, w, h);
    }

    private void drawGrid(Graphics2D g, int w, int h) {
        AffineTransform old = g.getTransform();
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        // 배율을 풀고 모니터 픽셀 좌표로 그린다
        int dw = (int) Math.ceil(w * old.getScaleX()), dh = (int) Math.ceil(h * old.getScaleY());
        g.setTransform(AffineTransform.getTranslateInstance(old.getTranslateX(), old.getTranslateY()));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int period = gridPeriod, alpha = gridAlpha;
        int line = Math.max(1, period / 2); // 선:빈칸 = 1:1 (같은 주기에서 줄무늬 성분이 가장 강함)
        if (alpha > 0) {
            if (gridColor == GridColor.RGB) {
                Color[] rgb = {new Color(255, 0, 0, alpha), new Color(0, 255, 0, alpha), new Color(0, 0, 255, alpha)};
                for (int x = 0, k = 0; x < dw; x += period, k++) {
                    g.setColor(rgb[k % 3]);
                    g.fillRect(x, 0, line, dh);
                }
            } else {
                g.setColor(new Color(0, 0, 0, alpha));
                for (int x = 0; x < dw; x += period) g.fillRect(x, 0, line, dh);
                for (int y = 0; y < dh; y += period) g.fillRect(0, y, dw, line);
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setTransform(old);
    }

    private void drawWatermark(Graphics2D g, int w, int h) {
        String text = watermark;
        if (text.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MARK_ALPHA));
        g2.setColor(MARK);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g2.rotate(Math.toRadians(-20), w / 2.0, h / 2.0);
        int stepX = g2.getFontMetrics().stringWidth(text) + 60, stepY = 56;
        // 회전해도 빈 곳이 없도록 화면보다 넓게 깐다
        for (int y = -h; y < 2 * h; y += stepY) {
            int shift = Math.floorMod(y / stepY, 2) == 0 ? 0 : stepX / 2;
            for (int x = -w; x < 2 * w; x += stepX) g2.drawString(text, x + shift, y);
        }
        g2.dispose();
    }
}
