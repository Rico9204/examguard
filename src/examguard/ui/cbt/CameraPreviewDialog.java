package examguard.ui.cbt;

import examguard.ai.GlassesAssistant;
import examguard.display.CameraModel;
import examguard.display.ExamPanel;
import examguard.model.QuestionVariant;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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
    private BufferedImage lastShot;
    private final GlassesAssistant assistant = new GlassesAssistant();
    private final JButton askButton = new JButton("사진 한 장으로 묻기");
    private final JButton askVideoButton = new JButton("새로 본 장면(영상)으로 묻기");
    private final JButton askTextButton = new JButton("말로만 묻기 (후속 질문)");
    private final JButton resetButton = new JButton("대화 초기화");
    /**
     * ponytail: 라이브 AI가 실제로 몇 초마다 프레임을 보는지는 공개되지 않음. 2초 간격·최근 10장(20초)은 가정값.
     * 프레임은 이 창이 열려 있는 동안만 모인다.
     */
    private static final int LIVE_FRAME_MS = 2000, LIVE_MAX_FRAMES = 10;
    private final ArrayDeque<BufferedImage> recent = new ArrayDeque<>();
    private long lastSampleMs;
    private QuestionVariant lastQuestion;
    private final JTextArea answer = new JTextArea(8, 40);

    CameraPreviewDialog(JFrame owner, ExamPanel examPanel, Runnable onClosed) {
        super(owner, "카메라 시뮬레이션 (실험)", false);
        this.examPanel = examPanel;
        setFocusableWindowState(false); // 시험 창의 키보드 포커스를 빼앗지 않게
        image.setOpaque(true);
        image.setBackground(Color.BLACK);
        caption.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        add(image, BorderLayout.CENTER);
        answer.setEditable(false);
        answer.setLineWrap(true);
        answer.setText("고정 프롬프트: " + GlassesAssistant.PROMPT
                + "\nAI는 이 창이 열린 동안 주고받은 장면과 대화를 기억합니다 (카메라를 바꾸거나 [대화 초기화]하면 지워짐).\n");
        askButton.addActionListener(e -> ask(Mode.PHOTO, GlassesAssistant.PROMPT));
        askVideoButton.addActionListener(e -> ask(Mode.VIDEO, GlassesAssistant.PROMPT));
        askTextButton.addActionListener(e -> {
            // 이 창은 시험 창 포커스를 안 뺏으려고 포커스를 받지 않으므로, 입력은 별도 대화상자로 받는다
            String q = JOptionPane.showInputDialog(getOwner(), "AI 안경에 할 말 (예: 아까 그 문제 답이 뭐였지?)");
            if (q != null && !q.isBlank()) ask(Mode.TEXT, q.trim());
        });
        resetButton.addActionListener(e -> resetConversation());
        JPanel buttons = new JPanel(new java.awt.GridLayout(4, 1));
        buttons.add(askButton);
        buttons.add(askVideoButton);
        buttons.add(askTextButton);
        buttons.add(resetButton);
        JPanel south = new JPanel(new BorderLayout());
        south.add(caption, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.WEST);
        south.add(new JScrollPane(answer), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
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
        if (this.model != null && this.model != model) resetConversation(); // 다른 카메라 장면·대화가 섞이지 않게
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
        QuestionVariant current = examPanel.getCurrent();
        if (lastQuestion != null && current != lastQuestion) {
            // 새 문제: 이전 문제 장면(모아 둔 프레임·기억 속 이미지)을 지워 메모리를 확보한다. 대화 글은 남긴다
            recent.clear();
            assistant.forgetImages();
            answer.append("── 새 문제: 이전 장면 이미지 삭제 (대화 글은 기억) ──" + System.lineSeparator());
        }
        lastQuestion = current;
        BufferedImage shot = model.capture(f -> examPanel.snapshot(focus, f), frame, rnd);
        lastShot = shot;
        long now = System.currentTimeMillis();
        if (now - lastSampleMs >= LIVE_FRAME_MS) {
            lastSampleMs = now;
            recent.addLast(shot);
            while (recent.size() > LIVE_MAX_FRAMES) recent.removeFirst();
        }

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

    private enum Mode { PHOTO, VIDEO, TEXT }

    private void resetConversation() {
        assistant.reset();
        recent.clear();
        answer.append("\n── 대화 초기화 ──\n");
    }

    /**
     * 촬영본(화면 확대본이 아니라 센서 해상도 원본)과 말을 모델에 보낸다. 앞선 장면·대화는 assistant가 기억한다.
     * PHOTO: 지금 사진 한 장 ("Hey Meta, look and…"). VIDEO: 지난 질문 뒤 새로 모인 프레임들 (라이브 AI).
     * TEXT: 글만 (앞서 본 것에 대한 후속 질문)
     */
    private void ask(Mode mode, String prompt) {
        BufferedImage shot = lastShot;
        if (shot == null && mode != Mode.TEXT) return;
        List<BufferedImage> shots = mode == Mode.VIDEO ? new ArrayList<>(recent)
                : mode == Mode.PHOTO ? List.of(shot) : List.of();
        if (mode == Mode.VIDEO && shots.isEmpty()) {
            answer.append("\n(지난 질문 뒤 새로 찍힌 장면이 아직 없음)\n");
            return;
        }
        QuestionVariant q = examPanel.getCurrent();
        String truth = q == null ? "?" : q.getCorrectAnswer();
        CameraModel cam = model;
        setAskEnabled(false);
        answer.append(String.format("%n나: %s  [%s]%n묻는 중... (첫 호출은 모델을 올리느라 오래 걸림)%n", prompt,
                shots.isEmpty() ? "이미지 없음" : (cam.getName() + " · 이미지 " + shots.size() + "장")));
        new SwingWorker<GlassesAssistant.Answer, Void>() {
            @Override protected GlassesAssistant.Answer doInBackground() throws Exception {
                return assistant.ask(shots, prompt);
            }
            @Override protected void done() {
                setAskEnabled(true);
                try {
                    GlassesAssistant.Answer a = get();
                    recent.removeAll(shots); // 이미 보여 준 장면은 기억에 있으니 다음 영상 질문에서 다시 보내지 않는다
                    answer.append(String.format("AI (%s · %.1f초 · 대화 %d차례 · 실제 정답 %s): %s%n",
                            GlassesAssistant.MODEL, a.millis / 1000.0, assistant.turns() / 2, truth, a.text));
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    answer.append("실패: " + c.getMessage() + "\n");
                }
                answer.setCaretPosition(answer.getDocument().getLength());
            }
        }.execute();
    }

    private void setAskEnabled(boolean on) {
        askButton.setEnabled(on);
        askVideoButton.setEnabled(on);
        askTextButton.setEnabled(on);
        resetButton.setEnabled(on);
    }
}
