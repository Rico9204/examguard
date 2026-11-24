package examguard.display;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;

/**
 * 부분 표시 · 줄 단위 (읽기 자).
 * 마우스(또는 ↑↓ 방향키)가 가리키는 한 줄만 화면 폭 전체로 보여 준다. 나머지는 정보 없는 원과 같은 회색 막대.
 * 사람은 원래 한 줄씩 읽으므로 원을 이리저리 옮기는 것보다 부담이 적고, 사진 한 장에는 한 줄만 담긴다.
 *
 * 한계: 궤적 판정 규칙(특히 "빠른 화면 노출")은 원 모양 기준으로 맞춰져 있어 이 모드에서는 노출량을 적게 잡는다.
 */
public class LineRulerLayer extends ZeroInfoSpotlightLayer {
    private static final int PAD = 4; // 줄 위아래 여유 (px)

    @Override
    public String getName() {
        return "부분 표시 · 줄 단위 (읽기 자)";
    }

    /** 마우스 높이에 가장 가까운 줄 하나를 화면 폭 전체로 */
    @Override
    protected Shape revealShape(RenderContext ctx) {
        int[] best = nearestLine(ctx.getFocus().y);
        if (best == null) return super.revealShape(ctx);
        return new Rectangle(0, best[0] - PAD, ctx.getSharp().getWidth(), best[1] - best[0] + 1 + 2 * PAD);
    }
}
