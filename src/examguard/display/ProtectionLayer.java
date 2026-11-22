package examguard.display;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * 시험 문제를 "어떻게 보여줄지" 정하는 보호 레이어.
 * ExamPanel은 어떤 레이어인지 몰라도 paint()만 호출한다 (다형성).
 */
public interface ProtectionLayer {
    String getName();

    void paint(Graphics2D g, RenderContext ctx);

    /** true면 ExamPanel이 매 프레임 다시 그린다 */
    default boolean isAnimated() { return false; }

    /** true면 문제를 크고 굵은 글씨로 렌더링한다 */
    default boolean prefersLargeText() { return false; }

    /** 새 문제 이미지가 준비될 때 한 번 호출된다 (미리 계산할 것이 있으면 여기서) */
    default void prepare(BufferedImage sharp) {}

    /** 시험 창을 열 때 수험자 식별 문자열을 알려 준다 (워터마크를 그리는 레이어만 사용) */
    default void setWatermark(String text) {}
}
