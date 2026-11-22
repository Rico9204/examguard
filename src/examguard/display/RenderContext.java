package examguard.display;

import java.awt.Point;
import java.awt.image.BufferedImage;

/** 보호 레이어가 한 프레임을 그릴 때 필요한 정보 */
public class RenderContext {
    private final BufferedImage sharp;
    private final BufferedImage blurred;
    private final Point focus;
    private final int radius;
    private final long frame;

    public RenderContext(BufferedImage sharp, BufferedImage blurred, Point focus, int radius, long frame) {
        this.sharp = sharp;
        this.blurred = blurred;
        this.focus = focus;
        this.radius = radius;
        this.frame = frame;
    }

    public BufferedImage getSharp() { return sharp; }
    public BufferedImage getBlurred() { return blurred; }
    /** 마우스(또는 방향키)가 가리키는 위치. 화면 밖이면 null */
    public Point getFocus() { return focus; }
    public int getRadius() { return radius; }
    /** 애니메이션 프레임 번호 */
    public long getFrame() { return frame; }
}
