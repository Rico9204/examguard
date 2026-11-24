package examguard.display;

/**
 * 셀로판 안경 (색 열쇠). 이상적인 필터로 가정: 자기 색 채널만 통과시키고 나머지는 막는다.
 * ponytail: 실제 셀로판은 다른 색도 조금 새어 들어온다 (빨강 셀로판이 초록을 일부 통과 등). 실측으로 맞출 값.
 */
public enum Cellophane {
    RED("빨강", 16, 0.3),
    GREEN("초록", 8, 0.6),
    BLUE("파랑", 0, 0.1);

    private final String label;
    private final int shift;      // RGB 정수에서 이 채널의 비트 위치
    private final double weight;  // 밝기 계산 가중치 (프로젝트 공통: 0.3R + 0.6G + 0.1B)

    Cellophane(String label, int shift, double weight) {
        this.label = label;
        this.shift = shift;
        this.weight = weight;
    }

    public String getLabel() { return label; }
    double weight() { return weight; }
    int shift() { return shift; }

    @Override
    public String toString() { return label + " 셀로판"; }

    /** 이 채널 값 (0~255) */
    public int channel(int rgb) {
        return (rgb >> shift) & 0xFF;
    }

    /** 셀로판을 통과한 색: 자기 채널만 남는다 */
    public int pass(int rgb) {
        return channel(rgb) << shift;
    }
}
