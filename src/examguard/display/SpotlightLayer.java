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

    @Override
    public void paint(Graphics2D g, RenderContext ctx) {
        g.drawImage(ctx.getBlurred(), 0, 0, null);
        Point f = ctx.getFocus();
        if (f == null) return;

        int r = ctx.getRadius();
        Ellipse2D circle = new Ellipse2D.Double(f.x - r, f.y - r, 2.0 * r, 2.0 * r);
        Shape oldClip = g.getClip();
        g.clip(circle);
        drawRevealed(g, ctx, circle.getBounds());
        g.setClip(oldClip);

        g.setColor(RING);
        g.setStroke(new BasicStroke(2f));
        g.draw(circle);
    }

    /** 원 안을 어떻게 보여줄지. 하위 클래스가 바꿀 수 있다 (MotionNoiseLayer). */
    protected void drawRevealed(Graphics2D g, RenderContext ctx, Rectangle area) {
        g.drawImage(ctx.getSharp(), 0, 0, null);
    }
}
