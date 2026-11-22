package examguard.display;

import examguard.analysis.Sample;
import examguard.analysis.TrajectoryRecorder;
import examguard.model.QuestionVariant;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
    private final Timer animation;
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

    public ExamPanel(TrajectoryRecorder recorder) {
        this.recorder = recorder;
        setBackground(Color.WHITE);
        setFocusable(true);
        setPreferredSize(new Dimension(CONTENT_WIDTH, QuestionRenderer.MIN_HEIGHT));

        animation = new Timer(16, e -> {
            frame++;
            repaint();
        });

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
        if (!animation.isRunning()) repaint();
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
        animation.stop();
        repaint();
    }

    private void rebuild() {
        if (current == null) {
            animation.stop();
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
        if (layer.isAnimated()) animation.start(); else animation.stop();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (sharp == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        layer.paint(g2, new RenderContext(sharp, blurred, focus, radius, frame));
        g2.dispose();
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
