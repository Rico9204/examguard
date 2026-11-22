package examguard.display;

import java.awt.Graphics2D;

/** 보호 없이 그대로 보여준다 (비교 실험용). */
public class PlainLayer implements ProtectionLayer {
    @Override
    public String getName() {
        return "보호 없음 (비교용)";
    }

    @Override
    public void paint(Graphics2D g, RenderContext ctx) {
        g.drawImage(ctx.getSharp(), 0, 0, null);
    }
}
