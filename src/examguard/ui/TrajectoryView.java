package examguard.ui;

import examguard.analysis.Sample;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.List;

/** 궤적을 그리고, 실제 시간의 3배속으로 재생한다. */
public class TrajectoryView extends JPanel {
    private static final long GAP_MS = 250;
    private static final Color OLD = new Color(215, 215, 215);
    private static final Color NEW = new Color(30, 90, 200);
    private static final Color KEY_NEW = new Color(225, 120, 0);

    private List<Sample> samples = Collections.emptyList();
    private int areaW = 760;
    private int areaH = 360;
    private int replayIndex = -1;
    private long replayClock;
    private final Timer replayTimer;

    public TrajectoryView() {
        setBackground(new Color(245, 245, 245));
        replayTimer = new Timer(16, e -> stepReplay());
    }

    public void setData(List<Sample> samples, int w, int h) {
        if (replayTimer.isRunning()) return; // 재생 중에는 화면을 고정
        this.samples = samples;
        this.areaW = w;
        this.areaH = h;
        repaint();
    }

    public void startReplay() {
        if (samples.size() < 2) return;
        replayIndex = 0;
        replayClock = samples.get(0).getTime();
        replayTimer.start();
    }

    private void stepReplay() {
        replayClock += 48;
        if (replayIndex + 1 < samples.size() && samples.get(replayIndex + 1).getTime() - replayClock > 1000) {
            replayClock = samples.get(replayIndex + 1).getTime(); // 긴 멈춤은 건너뜀
        }
        while (replayIndex + 1 < samples.size() && samples.get(replayIndex + 1).getTime() <= replayClock) {
            replayIndex++;
        }
        if (replayIndex >= samples.size() - 1) {
            replayTimer.stop();
            replayIndex = -1;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Insets in = getInsets();
        int availW = getWidth() - in.left - in.right - 20;
        int availH = getHeight() - in.top - in.bottom - 20;
        double scale = Math.min(availW / (double) areaW, availH / (double) areaH);
        int ox = in.left + 10 + (int) ((availW - areaW * scale) / 2);
        int oy = in.top + 10;

        g2.setColor(Color.WHITE);
        g2.fillRect(ox, oy, (int) (areaW * scale), (int) (areaH * scale));
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawRect(ox, oy, (int) (areaW * scale), (int) (areaH * scale));

        if (samples.size() < 2) {
            g2.setColor(Color.GRAY);
            g2.drawString("아직 궤적이 없습니다. 시험을 시작하거나 '가짜 궤적' 버튼을 눌러 보세요.", ox + 16, oy + 30);
            g2.dispose();
            return;
        }

        g2.clipRect(ox, oy, (int) (areaW * scale) + 1, (int) (areaH * scale) + 1);
        int end = replayIndex >= 0 ? replayIndex : samples.size() - 1;
        g2.setStroke(new BasicStroke(1.4f));
        for (int i = 1; i <= end; i++) {
            Sample a = samples.get(i - 1), b = samples.get(i);
            if (b.getTime() - a.getTime() > GAP_MS || a.getQuestion() != b.getQuestion()) continue;
            float t = i / (float) end;
            g2.setColor(mix(OLD, b.getSource() == Sample.Source.MOUSE ? NEW : KEY_NEW, t));
            g2.drawLine(ox + (int) (a.getX() * scale), oy + (int) (a.getY() * scale),
                        ox + (int) (b.getX() * scale), oy + (int) (b.getY() * scale));
        }

        Sample last = samples.get(end);
        int cx = ox + (int) (last.getX() * scale), cy = oy + (int) (last.getY() * scale);
        int r = (int) (last.getRadius() * scale);
        g2.setColor(new Color(30, 90, 200, 40));
        g2.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g2.setColor(NEW);
        g2.drawOval(cx - r, cy - r, 2 * r, 2 * r);
        g2.fillOval(cx - 4, cy - 4, 8, 8);

        g2.setClip(null);
        if (replayIndex >= 0) {
            g2.setColor(Color.DARK_GRAY);
            g2.drawString("재생 중 (3배속)", ox + 8, oy + (int) (areaH * scale) + 16);
        }
        g2.dispose();
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
