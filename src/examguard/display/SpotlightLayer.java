package examguard.display;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;

/**
 * 부분 표시: 문제 전체를 흐리게 두고, 마우스 주변 원 안만 보여준다.
 * 사진 한 장으로는 문제 전체를 찍을 수 없다.
 */
public class SpotlightLayer implements ProtectionLayer {
    private static final Color RING = new Color(40, 90, 200, 90);

    @Override
    public String getName() {
        return "부분 표시 (스포트라이트)";
    }

    /**
     * 템플릿 메서드: ① 배경(드러나지 않은 부분) → ② 드러낼 모양 → ③ 그 안을 그림 → ④ 테두리.
     * 하위 클래스는 필요한 단계만 바꾼다 (배경: ZeroInfoSpotlightLayer, 모양: LineRulerLayer, 안쪽: MotionNoiseLayer 등).
     */
    @Override
    public void paint(Graphics2D g, RenderContext ctx) {
        drawBackground(g, ctx);
        if (ctx.getFocus() == null) return;
        Shape shape = revealShape(ctx);
        if (shape == null) return;

        Shape oldClip = g.getClip();
        g.clip(shape);
        drawRevealed(g, ctx, shape.getBounds());
        g.setClip(oldClip);

        g.setColor(RING);
        g.setStroke(new BasicStroke(2f));
        g.draw(shape);
    }

    /** 드러나지 않은 부분을 어떻게 보여줄지. 기본은 흐린 문제 */
    protected void drawBackground(Graphics2D g, RenderContext ctx) {
        g.drawImage(ctx.getBlurred(), 0, 0, null);
    }

    /** 드러낼 영역의 모양. 기본은 마우스 주변 원 */
    protected Shape revealShape(RenderContext ctx) {
        Point f = ctx.getFocus();
        int r = ctx.getRadius();
        return new Ellipse2D.Double(f.x - r, f.y - r, 2.0 * r, 2.0 * r);
    }

    /** 원 안을 어떻게 보여줄지. 하위 클래스가 바꿀 수 있다 (MotionNoiseLayer). */
    protected void drawRevealed(Graphics2D g, RenderContext ctx, Rectangle area) {
        g.drawImage(ctx.getSharp(), 0, 0, null);
    }
}
