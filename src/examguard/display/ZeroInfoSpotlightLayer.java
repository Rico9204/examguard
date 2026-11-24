package examguard.display;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 부분 표시 · 정보 없는 원.
 * 원 밖을 "흐린 문제" 대신 글줄 위치만 알려 주는 회색 막대로 그린다. 원 밖에는 글자 정보가 전혀 없다.
 *
 * 흐림은 글자를 수학적으로 뭉갠 것이라 이론상 일부 되돌리거나(디블러) AI가 추측할 여지가 있다.
 * 이 방식은 화면 어디에도 원 안 말고는 글자 정보가 없으므로, 사진 한 장에는 원 안 내용만 담긴다
 * — 카메라 성능·노출·후처리·AI와 무관하게 성립한다 ("사진 한 장으로 문제 전체 획득"을 완벽하게 막음).
 *
 * 남는 정보: 줄 수와 각 줄의 길이 (막대). 읽는 위치를 잡는 데 필요해서 남겼다.
 */
public class ZeroInfoSpotlightLayer extends SpotlightLayer {
    private static final Color BAR = new Color(222, 226, 232);
    private static final int INK = 200;     // 이 밝기보다 어두우면 글자 픽셀
    private static final int MAX_GAP = 2;   // 한 줄 안에서 허용하는 빈 행 수

    private BufferedImage placeholder;
    private List<int[]> lines = Collections.emptyList(); // {y0, y1, x0, x1}

    @Override
    public String getName() {
        return "부분 표시 · 정보 없는 원";
    }

    @Override
    public void prepare(BufferedImage sharp) {
        lines = detectLines(sharp);
        placeholder = new BufferedImage(sharp.getWidth(), sharp.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = placeholder.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, sharp.getWidth(), sharp.getHeight());
        g.setColor(BAR);
        for (int[] l : lines) {
            int h = l[1] - l[0] + 1;
            g.fillRoundRect(l[2], l[0], l[3] - l[2] + 1, h, h / 2, h / 2);
        }
        g.dispose();
    }

    @Override
    protected void drawBackground(Graphics2D g, RenderContext ctx) {
        g.drawImage(placeholder != null ? placeholder : ctx.getBlurred(), 0, 0, null);
    }

    /** 감지한 글줄 {위 y, 아래 y, 왼쪽 x, 오른쪽 x} (LineRulerLayer가 줄 단위로 드러낼 때 쓴다) */
    protected List<int[]> getLines() {
        return lines;
    }

    /** 높이 y에 가장 가까운 글줄 (줄이 없으면 null) — 줄 단위·시간차 마스킹 창이 쓴다 */
    protected int[] nearestLine(int y) {
        int[] best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int[] l : lines) {
            int d = y < l[0] ? l[0] - y : y > l[1] ? y - l[1] : 0;
            if (d < bestDist) {
                bestDist = d;
                best = l;
            }
        }
        return best;
    }

    /** 글자 픽셀이 있는 행을 묶어 줄로 나눈다 {위 y, 아래 y, 왼쪽 x, 오른쪽 x} */
    public static List<int[]> detectLines(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);
        List<int[]> out = new ArrayList<>();
        int start = -1, lastInk = -1, x0 = w, x1 = -1;
        for (int y = 0; y < h; y++) {
            int rowMin = w, rowMax = -1;
            for (int x = 0; x < w; x++) {
                int p = rgb[y * w + x];
                int lum = (((p >> 16) & 0xFF) * 3 + ((p >> 8) & 0xFF) * 6 + (p & 0xFF)) / 10;
                if (lum < INK) {
                    rowMin = Math.min(rowMin, x);
                    rowMax = x;
                }
            }
            if (rowMax >= 0) {
                if (start < 0) start = y;
                lastInk = y;
                x0 = Math.min(x0, rowMin);
                x1 = Math.max(x1, rowMax);
            } else if (start >= 0 && y - lastInk > MAX_GAP) {
                out.add(new int[]{start, lastInk, x0, x1});
                start = -1;
                x0 = w;
                x1 = -1;
            }
        }
        if (start >= 0) out.add(new int[]{start, lastInk, x0, x1});
        return out;
    }
}
