package examguard.display;

/**
 * 화면 픽셀 값(0~255, 감마 적용)과 실제 빛의 세기(선형 0~1) 사이 변환.
 * 사람 눈과 카메라 노출은 "빛"을 평균한다(탤벗-플라토 법칙). 픽셀 값을 평균하면 사람이 보는 밝기와 달라진다.
 * ponytail: 모니터·카메라 감마를 2.2 하나로 가정. 실제 패널(sRGB 곡선, 패널별 차이, LCD 응답 속도)과 다를 수 있다 —
 * 맞지 않으면 사람 눈에 노이즈가 조금 남는다. 실측으로 맞출 손잡이.
 */
public final class Gamma {
    public static final double GAMMA = 2.2;
    private static final double[] LINEAR = new double[256];

    static {
        for (int v = 0; v < 256; v++) LINEAR[v] = Math.pow(v / 255.0, GAMMA);
    }

    private Gamma() {}

    /** 픽셀 값 → 빛의 세기 0~1 */
    public static double toLinear(int value) {
        return LINEAR[value];
    }

    /** 빛의 세기 0~1 → 가장 가까운 픽셀 값 */
    public static int toValue(double linear) {
        if (linear <= 0) return 0;
        if (linear >= 1) return 255;
        return (int) Math.round(255 * Math.pow(linear, 1 / GAMMA));
    }
}
