package examguard.ui.cbt;

import examguard.display.CameraModel;
import examguard.display.ExamPanel;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 실험 도구: 지금 시험 화면이 선택한 촬영 기기에 어떻게 찍히는지 0.3초마다 보여 준다.
 * 촬영본은 화면 원래 크기로 확대해서 보여 준다.
 */
class CameraPreviewDialog extends JDialog {
    private static final int MAX_VIEW_WIDTH = 1000;

    private final ExamPanel examPanel;
    private final JLabel image = new JLabel("", SwingConstants.CENTER);
    private final JLabel caption = CbtStyle.label(" ", Font.PLAIN, 12, CbtStyle.MUTED);
    private final Timer timer;
    private final Random rnd = new Random();
    private CameraModel model;

    CameraPreviewDialog(JFrame owner, ExamPanel examPanel, Runnable onClosed) {
        super(owner, "카메라 시뮬레이션 (실험)", false);
        this.examPanel = examPanel;
        setFocusableWindowState(false); // 시험 창의 키보드 포커스를 빼앗지 않게
        image.setOpaque(true);
        image.setBackground(Color.BLACK);
        caption.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        add(image, BorderLayout.CENTER);
        add(caption, BorderLayout.SOUTH);
        timer = new Timer(300, e -> refresh());
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                timer.stop();
                onClosed.run();
            }
        });
    }

    void show(CameraModel model) {
        this.model = model;
        setTitle("카메라 시뮬레이션 (실험) · " + model.getName());
        refresh();
        pack();
        if (!isVisible()) {
            // 시험 창 오른쪽 위에 띄운다
            Point p = getOwner().getLocationOnScreen();
            setLocation(p.x + getOwner().getWidth() - getWidth() - 20, p.y + 120);
        }
        setVisible(true);
        timer.start();
    }

    void stop() {
        timer.stop();
        setVisible(false);
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }

    private void refresh() {
        if (model == null) return;
        Point focus = examPanel.getFocus();
        long frame = examPanel.getFrame();
        if (examPanel.snapshot(focus, frame) == null) return; // 아직 문제가 없음
        BufferedImage shot = model.capture(f -> examPanel.snapshot(focus, f), frame, rnd);

        // 촬영본을 화면 원래 크기로 되돌려 보여 준다 (사진을 확대해 보는 것과 같게, 부드럽게 보간).
        // 예전에는 560px로 "더 줄여서" 보여 줘 실제 촬영본보다 나쁘게 보였다.
        int vw = Math.min(MAX_VIEW_WIDTH, examPanel.getWidth());
        int vh = (int) Math.round(shot.getHeight() * (double) vw / shot.getWidth());
        BufferedImage view = new BufferedImage(vw, vh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = view.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(shot, 0, 0, vw, vh, null);
        g.dispose();
        image.setIcon(new ImageIcon(view));
        caption.setText(model.describe(examPanel.getWidth())
                + (focus == null ? " · 마우스가 문제 밖에 있음" : ""));
    }
}
