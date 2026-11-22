package examguard.analysis;

/** 궤적의 점 하나: 언제, 어디를, 어느 문제에서, 어떤 반경으로 보고 있었는지 */
public class Sample {
    /** MOUSE: 마우스 이동, KEY: 방향키 이동(편의 제공 모드), SCROLL: 휠 스크롤로 내용이 원 아래를 지나감 */
    public enum Source { MOUSE, KEY, SCROLL }

    private final int x;
    private final int y;
    private final long time;
    private final int question;
    private final int radius;
    private final Source source;

    public Sample(int x, int y, long time, int question, int radius, Source source) {
        this.x = x;
        this.y = y;
        this.time = time;
        this.question = question;
        this.radius = radius;
        this.source = source;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public long getTime() { return time; }
    public int getQuestion() { return question; }
    public int getRadius() { return radius; }
    public Source getSource() { return source; }
}
