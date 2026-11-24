package examguard.display;

import examguard.analysis.Sample;
import examguard.analysis.TrajectoryRecorder;
import examguard.model.QuestionVariant;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** 시험 문제를 보호 레이어를 통해 그리고, 마우스·방향키 이동을 궤적으로 기록한다. */
public class ExamPanel extends JPanel {
    public static final int CONTENT_WIDTH = 760;
    private static final int KEY_STEP = 16;

    private final TrajectoryRecorder recorder;
    /**
     * 애니메이션 렌더 스레드 (null = 멈춤).
     * 예전 javax.swing.Timer(16)은 Windows 기본 타이머 단위(15.6ms) 때문에 실제로 약 30ms(33Hz)마다 돌아,
     * 시간차 마스킹의 깜빡임이 설계의 절반 속도(2장 약 17Hz, 3장 약 11Hz)로 느려져 있었다 (2026-11-23 실측).
     */
    private volatile Thread renderThread;
    private ProtectionLayer layer = new SpotlightLayer();
    private QuestionVariant current;
    private int questionIndex;
    private BufferedImage sharp;
    private BufferedImage blurred;
    private Point focus;
    private int radius = 90;
    private long frame;
    private int contentWidth = CONTENT_WIDTH;
    private double fontScale = 1.0;
    private boolean keyboardEnabled;
    private volatile Cellophane viewFilter;

    public ExamPanel(TrajectoryRecorder recorder) {
        this.recorder = recorder;
        setBackground(Color.WHITE);
        setFocusable(true);
        setPreferredSize(new Dimension(CONTENT_WIDTH, QuestionRenderer.MIN_HEIGHT));

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { moveFocus(e.getPoint(), Sample.Source.MOUSE); }
            @Override public void mouseDragged(MouseEvent e) { moveFocus(e.getPoint(), Sample.Source.MOUSE); }
            @Override public void mouseExited(MouseEvent e) { focus = null; repaint(); }
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        // 편의 제공(접근성): 감독관이 허용한 수험자는 방향키로 스포트라이트를 옮길 수 있다.
        // 방향키 이동도 궤적으로 기록·분석되므로 감시의 사각지대가 되지 않는다.
        bindKey("LEFT", -KEY_STEP, 0);
        bindKey("RIGHT", KEY_STEP, 0);
        bindKey("UP", 0, -KEY_STEP);
        bindKey("DOWN", 0, KEY_STEP);

        // 마우스는 그대로 두고 휠로 스크롤하면 문제가 원 아래로 지나간다.
        // 이때는 마우스 이벤트가 생기지 않으므로, 패널이 움직일 때 원 위치를 다시 계산해 기록한다.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                trackScroll();
            }
        });
    }

    /** 방향키 이동 허용 여부 (기본: 꺼짐) */
    public void setKeyboardEnabled(boolean enabled) {
        this.keyboardEnabled = enabled;
    }

    public boolean isKeyboardEnabled() {
        return keyboardEnabled;
    }

    private void trackScroll() {
        if (current == null || !isShowing()) return;
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) return;
        Point p = info.getLocation();
        SwingUtilities.convertPointFromScreen(p, this);
        if (contains(p)) moveFocus(p, Sample.Source.SCROLL);
    }

    private void bindKey(String key, int dx, int dy) {
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!keyboardEnabled) return;
                Point base = focus != null ? focus : new Point(getWidth() / 2, getHeight() / 2);
                int x = Math.max(0, Math.min(getWidth() - 1, base.x + dx));
                int y = Math.max(0, Math.min(getHeight() - 1, base.y + dy));
                moveFocus(new Point(x, y), Sample.Source.KEY);
            }
        });
    }

    private void moveFocus(Point p, Sample.Source source) {
        focus = p;
        if (current != null) {
            recorder.record(new Sample(p.x, p.y, System.currentTimeMillis(), questionIndex, radius, source));
        }
        if (renderThread == null) repaint();
    }

    private void startAnimation() {
        if (renderThread != null) return;
        Thread t = new Thread(this::renderLoop, "exam-render");
        t.setDaemon(true);
        renderThread = t;
        t.start();
    }

    private void stopAnimation() {
        Thread t = renderThread;
        renderThread = null;
        if (t != null) t.interrupt();
    }

    /**
     * 화면 주사율에 맞춰 한 번에 한 장씩 그린다.
     * - 시각: nanoTime 마감 시각까지 Thread.sleep(1)로 기다린 뒤 마지막 3ms는 바쁜 대기 (sleep만으로는 1ms 이상 밀림)
     * - 프레임 번호: "실제로 그린 횟수"로 1씩 올린다. 시각으로 번호를 매기면 밀렸을 때 번호가 건너뛰어
     *   서브프레임 쌍(R, 2T−R)의 한쪽이 화면에 안 나오고 노이즈가 지워지지 않는다.
     * - 밀렸으면(EDT가 바빴으면) 따라잡으려 몰아 그리지 않고 마감 시각을 새로 잡는다 (몰아 그리면 한 리프레시에 두 장).
     * ponytail: vsync를 보장하지 않는다 (Swing 창 모드의 한계). 모니터 실제 주사율(59.94Hz 등)과의 작은 차이로
     * 수 초~수십 초마다 짧은 위상 미끄러짐이 남는다. 완전히 맞추려면 전체화면 BufferStrategy 페이지 플립.
     */
    private void renderLoop() {
        long period = framePeriodNanos();
        long deadline = System.nanoTime();
        Runnable paintOne = () -> {
            frame++;
            paintImmediately(0, 0, getWidth(), getHeight());
        };
        try {
            while (renderThread == Thread.currentThread()) {
                deadline += period;
                while (System.nanoTime() < deadline - 3_000_000) Thread.sleep(1);
                while (System.nanoTime() < deadline) Thread.onSpinWait();
                SwingUtilities.invokeAndWait(paintOne);
                if (System.nanoTime() > deadline + period / 2) deadline = System.nanoTime();
            }
        } catch (InterruptedException e) {
            // stopAnimation()
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /**
     * 한 프레임 길이 = 모니터 주사율 (모르면 60Hz).
     * ponytail: 120/144Hz 모니터면 서브프레임 주파수도 같이 올라가 깜빡임은 줄지만 30fps 영상 방어도 약해진다 (README 참고).
     */
    private static long framePeriodNanos() {
        int hz = 60;
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                int r = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                        .getDisplayMode().getRefreshRate();
                if (r > 0) hz = r;
            }
        } catch (RuntimeException e) {
            // 주사율을 모르면 60Hz로 둔다
        }
        return 1_000_000_000L / hz;
    }

    public void setLayer(ProtectionLayer layer) {
        this.layer = layer;
        rebuild();
    }

    /** 스포트라이트 위치 (문제 밖이면 null) — 카메라 시뮬레이션이 같은 화면을 다시 그릴 때 쓴다 */
    public Point getFocus() {
        return focus == null ? null : new Point(focus);
    }

    /** 현재 애니메이션 프레임 번호 */
    public long getFrame() {
        return frame;
    }

    /** 지금 보이는 문제 (없으면 null) */
    public QuestionVariant getCurrent() {
        return current;
    }

    public ProtectionLayer getLayer() {
        return layer;
    }

    public void setRadius(int radius) {
        this.radius = radius;
        repaint();
    }

    /** CBT 화면의 [글자 크기] 버튼: 1.0 = 100% */
    public void setFontScale(double scale) {
        if (Math.abs(scale - fontScale) < 0.01) return;
        fontScale = scale;
        rebuild();
    }

    /** 문제 영역 너비를 창 크기에 맞춘다 (너무 작은 변화는 무시) */
    public void setContentWidth(int width) {
        int w = Math.max(560, Math.min(1200, width));
        if (Math.abs(w - contentWidth) < 16) return;
        contentWidth = w;
        rebuild();
    }

    public void showQuestion(QuestionVariant variant, int index) {
        this.current = variant;
        this.questionIndex = index;
        this.focus = null;
        rebuild();
    }

    public void clear() {
        current = null;
        sharp = null;
        blurred = null;
        focus = null;
        stopAnimation();
        repaint();
    }

    private void rebuild() {
        if (current == null) {
            stopAnimation();
            return;
        }
        boolean large = layer.prefersLargeText();
        int fontSize = (int) Math.round((large ? 32 : 20) * fontScale);
        sharp = QuestionRenderer.render(current, questionIndex + 1, contentWidth, fontSize, large);
        blurred = QuestionRenderer.blur(sharp, Math.max(8, fontSize / 2));
        layer.prepare(sharp);
        recorder.setArea(sharp.getWidth(), sharp.getHeight());
        setPreferredSize(new Dimension(sharp.getWidth(), sharp.getHeight()));
        revalidate();
        if (layer.isAnimated()) startAnimation(); else stopAnimation();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (sharp == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Cellophane filter = viewFilter;
        if (filter == null) {
            layer.paint(g2, new RenderContext(sharp, blurred, focus, radius, frame));
        } else {
            // 셀로판 안경 시뮬레이션: 그린 화면을 셀로판에 통과시켜 보여 준다 (카메라 시뮬레이션은 원래 화면을 찍음)
            BufferedImage img = snapshot(focus, frame);
            int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
            for (int i = 0; i < px.length; i++) px[i] = filter.pass(px[i]);
            img.setRGB(0, 0, img.getWidth(), img.getHeight(), px, 0, img.getWidth());
            g2.drawImage(img, 0, 0, null);
        }
        g2.dispose();
    }

    /** 셀로판 안경을 끼고 본 화면으로 그린다 (null이면 끔). 실험용 시뮬레이션 */
    public void setViewFilter(Cellophane filter) {
        this.viewFilter = filter;
        repaint();
    }

    /** 화면에 띄우지 않고 특정 위치·프레임의 모습을 이미지로 얻는다 (보고서용 캡처, 자체 테스트용) */
    public BufferedImage snapshot(Point at, long atFrame) {
        if (sharp == null) return null;
        BufferedImage img = new BufferedImage(sharp.getWidth(), sharp.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        layer.paint(g2, new RenderContext(sharp, blurred, at, radius, atFrame));
        g2.dispose();
        return img;
    }
}
