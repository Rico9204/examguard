package examguard.ui.cbt;

import examguard.analysis.TrajectoryRecorder;
import examguard.display.CameraModel;
import examguard.display.ExamPanel;
import examguard.display.ProtectionLayer;
import examguard.exam.ExamSession;
import examguard.model.QuestionVariant;
import examguard.model.Student;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 실제 CBT 시험장과 같은 흐름의 응시 창.
 *   수험자 정보 확인 → 안내사항 → 문제 풀이 → 답안 제출 완료
 * 문제 풀이 화면: 위(시험 정보·남은 시간), 가운데(문제), 오른쪽(답안 표기란), 아래(이동·제출).
 */
public class CbtExamFrame extends JFrame {
    public static final String EXAM_TITLE = "2026학년도 2학기 객체지향언어 프로그래밍 기말시험";
    private static final int WARNING_SECONDS = 5 * 60;

    private final ExamSession session;
    private final ProtectionLayer layer;
    private final IntConsumer onQuestionChanged;
    private final Consumer<ExamSession> onSubmit;
    private final Runnable onAbort;
    private final Consumer<ExamSession> autosaver;
    private final Timer autosaveTimer;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final ExamPanel examPanel;
    private final List<OmrRow> rows = new ArrayList<>();
    private final JLabel timeLabel = CbtStyle.label("00:00", Font.BOLD, 30, Color.WHITE);
    private final JLabel countLabel = CbtStyle.label(" ", Font.BOLD, 14, CbtStyle.INK);
    private final JLabel warningLabel = CbtStyle.label(" ", Font.BOLD, 14, CbtStyle.WARNING);
    private final JLabel questionTitle = CbtStyle.label(" ", Font.BOLD, 16, CbtStyle.INK);
    private final JLabel positionLabel = CbtStyle.label(" ", Font.BOLD, 15, CbtStyle.INK);
    private final JLabel doneDetail = CbtStyle.label(" ", Font.PLAIN, 16, CbtStyle.INK);
    private final JButton prevButton = CbtStyle.button("◀ 이전 문제", Color.WHITE, CbtStyle.INK);
    private final JButton nextButton = CbtStyle.button("다음 문제 ▶", Color.WHITE, CbtStyle.INK);
    private final Timer clock;
    private JScrollPane omrScroll;
    private CalculatorDialog calculator;
    private CameraPreviewDialog cameraPreview;
    private int current;
    private boolean warned;
    private boolean finished;

    public CbtExamFrame(ExamSession session, ProtectionLayer layer, int radius, TrajectoryRecorder recorder,
                        IntConsumer onQuestionChanged, Consumer<ExamSession> onSubmit, Runnable onAbort,
                        Consumer<ExamSession> autosaver) {
        super("CBT 시험 - " + session.getPaper().getStudent().getName());
        this.session = session;
        this.layer = layer;
        this.onQuestionChanged = onQuestionChanged;
        this.onSubmit = onSubmit;
        this.onAbort = onAbort;
        this.autosaver = autosaver;

        examPanel = new ExamPanel(recorder);
        // 이름·학번 대신 식별 코드만 (화면 촬영본에 개인정보가 남지 않게, 코드→학생은 유출 추적으로 역조회)
        layer.setWatermark("ID " + session.getPaper().getCodeString());
        examPanel.setLayer(layer);
        examPanel.setRadius(radius);
        examPanel.setKeyboardEnabled(session.isKeyboardMode());

        root.add(buildInfoPage(), "info");
        root.add(buildNoticePage(), "notice");
        root.add(buildExamPage(), "exam");
        root.add(buildDonePage(), "done");
        setContentPane(root);
        cards.show(root, "info");

        clock = new Timer(500, e -> tick());
        autosaveTimer = new Timer(30_000, e -> autosaver.accept(session)); // 30초마다 임시 저장

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { requestClose(); }
        });
        setSize(1280, 820);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    public void setRadius(int radius) {
        examPanel.setRadius(radius);
    }

    // ───────────────────────── 1. 수험자 정보 확인

    private JComponent buildInfoPage() {
        Student s = session.getPaper().getStudent();
        JPanel card = card(560);
        card.add(CbtStyle.label("수험자 정보 확인", Font.BOLD, 24, CbtStyle.NAVY), gbc(0));

        JPanel table = new JPanel(new GridLayout(4, 2, 0, 0));
        table.setBackground(CbtStyle.PANEL);
        table.setBorder(BorderFactory.createLineBorder(CbtStyle.LINE));
        addInfoRow(table, "시험명", EXAM_TITLE);
        addInfoRow(table, "수험번호", s.getStudentNo());
        addInfoRow(table, "성명", s.getName());
        addInfoRow(table, "좌석 번호", session.getSeat());
        card.add(table, gbc(1));

        card.add(CbtStyle.label("<html>화면의 정보가 본인과 같으면 <b>[예]</b>를 누르세요.<br>"
                + "다르면 [아니요]를 누르고 손을 들어 감독관에게 알리세요.</html>", Font.PLAIN, 15, CbtStyle.INK), gbc(2));

        JButton yes = CbtStyle.button("예", CbtStyle.NAVY, Color.WHITE);
        JButton no = CbtStyle.button("아니요", Color.WHITE, CbtStyle.INK);
        yes.addActionListener(e -> cards.show(root, "notice"));
        no.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "자리에 앉은 채로 손을 들어 감독관에게 알려 주세요.", "감독관 호출", JOptionPane.INFORMATION_MESSAGE));
        card.add(buttonRow(yes, no), gbc(3));
        return page("수험자 정보 확인", card);
    }

    private void addInfoRow(JPanel table, String key, String value) {
        JLabel k = CbtStyle.label(key, Font.BOLD, 15, CbtStyle.INK);
        k.setOpaque(true);
        k.setBackground(CbtStyle.BACKGROUND);
        k.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, CbtStyle.LINE), BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel v = CbtStyle.label(value, Font.PLAIN, 15, CbtStyle.INK);
        v.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CbtStyle.LINE), BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        table.add(k);
        table.add(v);
    }

    // ───────────────────────── 2. 안내사항

    private JComponent buildNoticePage() {
        JPanel card = card(680);
        card.add(CbtStyle.label("안내사항", Font.BOLD, 24, CbtStyle.NAVY), gbc(0));
        int minutes = session.getDurationSeconds() / 60;
        card.add(CbtStyle.label("<html><div style='width:600px'>"
                + "<ol>"
                + "<li>시험 시간은 <b>" + minutes + "분</b>입니다. 남은 시간은 오른쪽 위에 표시되며, "
                + "시간이 끝나면 자동 제출됩니다.</li>"
                + "<li>문제는 흐리게 표시되며, <b>마우스를 올린 곳</b>"
                + (session.isKeyboardMode() ? "(또는 방향키로 옮긴 곳)" : "") + "만 선명하게 보입니다.</li>"
                + "<li>답은 오른쪽 <b>[답안 표기란]</b>에서 고르거나 입력합니다. 제출 전까지 언제든 바꿀 수 있습니다.</li>"
                + "<li>문제 화면의 <b>마우스·방향키·스크롤 움직임은 부정행위 방지를 위해 기록·분석</b>되며, "
                + "시험 관리 목적으로만 쓰고 정해진 기간이 지나면 파기합니다.</li>"
                + "<li>답안은 30초마다 임시 저장되어, 장애가 생겨도 이어서 응시할 수 있습니다.</li>"
                + "<li>시험지는 응시자마다 문장 표현, 숫자, 보기 순서가 다르게 구성되며, 유출 시 추적할 수 있습니다.</li>"
                + "<li>스마트 안경, 휴대폰, 스마트워치 등 촬영·통신 기기는 반입할 수 없습니다.</li>"
                + "</ol></div></html>", Font.PLAIN, 15, CbtStyle.INK), gbc(1));

        JCheckBox agree = new JCheckBox("위 안내사항을 모두 읽고 확인했습니다.");
        agree.setFont(CbtStyle.font(Font.BOLD, 15));
        agree.setOpaque(false);
        card.add(agree, gbc(2));

        JButton start = CbtStyle.button("시험 시작", CbtStyle.NAVY, Color.WHITE);
        start.setEnabled(false);
        agree.addActionListener(e -> start.setEnabled(agree.isSelected()));
        start.addActionListener(e -> startExam());
        JButton back = CbtStyle.button("이전", Color.WHITE, CbtStyle.INK);
        back.addActionListener(e -> cards.show(root, "info"));
        card.add(buttonRow(start, back), gbc(3));
        return page("안내사항", card);
    }

    // ───────────────────────── 3. 문제 풀이

    private JComponent buildExamPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(CbtStyle.BACKGROUND);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(examHeader());
        north.add(toolbar());
        page.add(north, BorderLayout.NORTH);

        // 가운데: 문제
        JPanel question = new JPanel(new BorderLayout(0, 6));
        question.setOpaque(false);
        question.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 6));
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(CbtStyle.PANEL);
        titleBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CbtStyle.LINE), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        titleBar.add(questionTitle, BorderLayout.WEST);
        titleBar.add(CbtStyle.label(session.isKeyboardMode()
                ? "마우스를 올린 곳만 선명하게 보입니다 · 방향키로도 이동 (편의 제공)"
                : "마우스를 올린 곳만 선명하게 보입니다",
                Font.PLAIN, 13, CbtStyle.MUTED), BorderLayout.EAST);
        question.add(titleBar, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(examPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(CbtStyle.LINE));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                examPanel.setContentWidth(scroll.getViewport().getWidth() - 2);
            }
        });
        question.add(scroll, BorderLayout.CENTER);
        page.add(question, BorderLayout.CENTER);

        page.add(omrPanel(), BorderLayout.EAST);
        page.add(bottomBar(), BorderLayout.SOUTH);
        return page;
    }

    private JComponent examHeader() {
        Student s = session.getPaper().getStudent();
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CbtStyle.NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(CbtStyle.label(EXAM_TITLE, Font.BOLD, 18, Color.WHITE));
        left.add(Box.createVerticalStrut(4));
        left.add(CbtStyle.label("수험번호  " + s.getStudentNo() + "     성명  " + s.getName()
                + "     좌석  " + session.getSeat(), Font.PLAIN, 14, new Color(210, 222, 240)));
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(cameraSelector());
        right.add(Box.createHorizontalStrut(12));
        JPanel timeBox = new JPanel();
        timeBox.setOpaque(false);
        timeBox.setLayout(new BoxLayout(timeBox, BoxLayout.Y_AXIS));
        JLabel limit = CbtStyle.label("제한 시간 " + session.getDurationSeconds() / 60 + "분  |  남은 시간",
                Font.PLAIN, 13, new Color(210, 222, 240));
        limit.setAlignmentX(Component.RIGHT_ALIGNMENT);
        timeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        timeBox.add(limit);
        timeBox.add(timeLabel);
        right.add(timeBox);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    /** 실험 도구: 촬영 기기를 고르면 그 기기에 찍히는 모습을 별도 창으로 보여 준다 */
    private JComponent cameraSelector() {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel title = CbtStyle.label("카메라 시뮬레이션 (실험)", Font.PLAIN, 13, new Color(210, 222, 240));
        title.setAlignmentX(Component.RIGHT_ALIGNMENT);
        Object off = "끄기";
        List<Object> items = new ArrayList<>();
        items.add(off);
        items.addAll(CameraModel.PRESETS);
        JComboBox<Object> combo = new JComboBox<>(items.toArray());
        combo.setFont(CbtStyle.font(Font.PLAIN, 13));
        combo.setFocusable(false); // 문제 화면의 방향키 입력을 가로채지 않게
        combo.setAlignmentX(Component.RIGHT_ALIGNMENT);
        combo.addActionListener(e -> {
            Object sel = combo.getSelectedItem();
            if (sel instanceof CameraModel) {
                if (cameraPreview == null) cameraPreview = new CameraPreviewDialog(this, examPanel, () -> combo.setSelectedItem(off));
                cameraPreview.show((CameraModel) sel);
            } else if (cameraPreview != null) {
                cameraPreview.stop();
            }
        });
        box.add(title);
        box.add(combo);
        return box;
    }

    private JComponent toolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(CbtStyle.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CbtStyle.LINE), BorderFactory.createEmptyBorder(6, 14, 6, 14)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(CbtStyle.label("글자 크기", Font.BOLD, 14, CbtStyle.INK));
        ButtonGroup group = new ButtonGroup();
        String[] names = {"100%", "130%", "160%"};
        double[] scales = {1.0, 1.3, 1.6};
        for (int i = 0; i < names.length; i++) {
            JToggleButton b = new JToggleButton(names[i], i == 0);
            b.setFont(CbtStyle.font(Font.BOLD, 13));
            b.setFocusPainted(false);
            final double scale = scales[i];
            b.addActionListener(e -> examPanel.setFontScale(scale));
            group.add(b);
            left.add(b);
        }
        left.add(Box.createHorizontalStrut(18));
        left.add(CbtStyle.label("보호 방식: " + layer.getName(), Font.PLAIN, 13, CbtStyle.MUTED));
        bar.add(left, BorderLayout.WEST);

        JPanel middle = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        middle.setOpaque(false);
        middle.add(warningLabel);
        bar.add(middle, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        right.setOpaque(false);
        right.add(countLabel);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent omrPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(330, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 6, 10, 12));
        panel.setOpaque(false);

        JLabel title = CbtStyle.label("답안 표기란", Font.BOLD, 16, Color.WHITE);
        title.setOpaque(true);
        title.setBackground(CbtStyle.NAVY_LIGHT);
        title.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        panel.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(CbtStyle.PANEL);
        for (int i = 0; i < session.getQuestionCount(); i++) {
            QuestionVariant v = session.getPaper().getVariants().get(i);
            OmrRow row = new OmrRow(i, v, session, this::goTo, this::updateCounts);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            rows.add(row);
            list.add(row);
        }
        list.add(Box.createVerticalGlue());
        omrScroll = new JScrollPane(list);
        omrScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, CbtStyle.LINE));
        omrScroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(omrScroll, BorderLayout.CENTER);

        JLabel legend = CbtStyle.label("<html>번호를 누르면 그 문제로 이동합니다.<br>"
                + "<span style='color:#1a345c'>■</span> 표기한 문제 &nbsp; □ 안 푼 문제</html>", Font.PLAIN, 12, CbtStyle.MUTED);
        legend.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        panel.add(legend, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent bottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(CbtStyle.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, CbtStyle.LINE), BorderFactory.createEmptyBorder(8, 14, 8, 14)));

        JButton calc = CbtStyle.button("계산기", Color.WHITE, CbtStyle.INK);
        calc.addActionListener(e -> openCalculator());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(calc);
        bar.add(left, BorderLayout.WEST);

        prevButton.addActionListener(e -> goTo(current - 1));
        nextButton.addActionListener(e -> goTo(current + 1));
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        center.setOpaque(false);
        center.add(prevButton);
        center.add(positionLabel);
        center.add(nextButton);
        bar.add(center, BorderLayout.CENTER);

        JButton unanswered = CbtStyle.button("안 푼 문제", Color.WHITE, CbtStyle.INK);
        unanswered.addActionListener(e -> showUnanswered());
        JButton submit = CbtStyle.button("답안 제출", CbtStyle.SUBMIT, Color.WHITE);
        submit.addActionListener(e -> confirmSubmit());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(unanswered);
        right.add(submit);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ───────────────────────── 4. 제출 완료

    private JComponent buildDonePage() {
        JPanel card = card(520);
        card.add(CbtStyle.label("답안 제출이 완료되었습니다", Font.BOLD, 24, CbtStyle.NAVY), gbc(0));
        card.add(doneDetail, gbc(1));
        card.add(CbtStyle.label("감독관의 안내에 따라 퇴실하세요.", Font.PLAIN, 15, CbtStyle.MUTED), gbc(2));
        JButton close = CbtStyle.button("창 닫기", CbtStyle.NAVY, Color.WHITE);
        close.addActionListener(e -> dispose());
        card.add(buttonRow(close), gbc(3));
        return page("답안 제출 완료", card);
    }

    // ───────────────────────── 동작

    private void startExam() {
        session.start();
        cards.show(root, "exam");
        for (OmrRow r : rows) r.refresh(); // 이어서 응시하는 경우 저장된 답 표시
        updateCounts();
        tick();
        clock.start();
        autosaveTimer.start();
        SwingUtilities.invokeLater(() -> {
            goTo(0);
            examPanel.requestFocusInWindow();
        });
    }

    private void goTo(int index) {
        if (index < 0 || index >= session.getQuestionCount()) return;
        current = index;
        session.enterQuestion(index);
        QuestionVariant v = session.getPaper().getVariants().get(index);
        examPanel.showQuestion(v, index);
        for (int i = 0; i < rows.size(); i++) rows.get(i).setCurrent(i == index);
        rows.get(index).scrollRectToVisible(rows.get(index).getBounds());
        int n = session.getQuestionCount();
        String category = v.getSource().getCategory();
        questionTitle.setText("문제 " + (index + 1) + " / " + n + "     " + v.getSource().getTypeName()
                + " · " + v.getSource().getPoints() + "점" + (category.isEmpty() ? "" : "     [" + category + "]"));
        positionLabel.setText((index + 1) + " / " + n);
        prevButton.setEnabled(index > 0);
        nextButton.setEnabled(index < n - 1);
        onQuestionChanged.accept(index);
    }

    private void updateCounts() {
        int n = session.getQuestionCount();
        countLabel.setText("전체 " + n + "문제  |  푼 문제 " + session.getAnsweredCount()
                + "  |  안 푼 문제 " + (n - session.getAnsweredCount()));
    }

    private void tick() {
        int left = session.getRemainingSeconds();
        timeLabel.setText(String.format("%02d:%02d", left / 60, left % 60));
        if (!warned && left <= WARNING_SECONDS) {
            warned = true;
            timeLabel.setForeground(new Color(255, 120, 120));
            warningLabel.setText("시험 종료 " + WARNING_SECONDS / 60 + "분 전입니다. 답안 표기를 확인하세요.");
        }
        if (left <= 0) submitNow(true);
    }

    private void showUnanswered() {
        List<Integer> list = session.getUnanswered();
        if (list.isEmpty()) {
            JOptionPane.showMessageDialog(this, "안 푼 문제가 없습니다.", "안 푼 문제", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object[] options = new Object[list.size() + 1];
        for (int i = 0; i < list.size(); i++) options[i] = (list.get(i) + 1) + "번";
        options[list.size()] = "닫기";
        int choice = JOptionPane.showOptionDialog(this,
                "안 푼 문제가 " + list.size() + "개 있습니다. 이동할 문제를 고르세요.", "안 푼 문제",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice >= 0 && choice < list.size()) goTo(list.get(choice));
    }

    private void confirmSubmit() {
        List<Integer> list = session.getUnanswered();
        StringBuilder msg = new StringBuilder("답안을 제출하시겠습니까?\n\n");
        if (list.isEmpty()) {
            msg.append("모든 문제에 답을 표기했습니다.");
        } else {
            msg.append("안 푼 문제: ").append(list.size()).append("개 (");
            for (int i = 0; i < list.size(); i++) msg.append(i > 0 ? ", " : "").append(list.get(i) + 1).append("번");
            msg.append(")");
        }
        if (JOptionPane.showConfirmDialog(this, msg.toString(), "답안 제출",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) return;
        if (JOptionPane.showConfirmDialog(this, "제출한 뒤에는 답안을 고칠 수 없습니다.\n정말 제출할까요?", "최종 확인",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        submitNow(false);
    }

    private void submitNow(boolean timeUp) {
        if (finished) return;
        finished = true;
        clock.stop();
        autosaveTimer.stop();
        session.stopTiming();
        if (calculator != null) calculator.dispose();
        if (cameraPreview != null) cameraPreview.dispose();
        for (OmrRow r : rows) r.setInputEnabled(false);
        examPanel.clear();
        onSubmit.accept(session);
        doneDetail.setText("<html><div style='text-align:center'>"
                + (timeUp ? "시험 시간이 끝나 답안이 자동으로 제출되었습니다.<br>" : "")
                + "표기한 문제 " + session.getAnsweredCount() + " / " + session.getQuestionCount() + "</div></html>");
        cards.show(root, "done");
    }

    private void requestClose() {
        if (finished) {
            dispose();
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this,
                "시험을 중단하고 창을 닫을까요? (감독관 전용)\n답안은 제출되지 않고 임시 저장되며, 다시 열면 이어서 응시할 수 있습니다.",
                "시험 중단", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        finished = true;
        clock.stop();
        autosaveTimer.stop();
        if (session.isStarted()) autosaver.accept(session); // 중단해도 이어서 응시할 수 있게 마지막 상태 저장
        examPanel.clear();
        if (calculator != null) calculator.dispose();
        if (cameraPreview != null) cameraPreview.dispose();
        onAbort.run();
        dispose();
    }

    private void openCalculator() {
        if (calculator == null) calculator = new CalculatorDialog(this);
        calculator.setVisible(true);
        calculator.toFront();
    }

    // ───────────────────────── 화면 조립 도우미

    /** 남색 상단 띠 + 가운데 카드 */
    private JComponent page(String step, JComponent card) {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(CbtStyle.BACKGROUND);
        JPanel band = new JPanel(new BorderLayout());
        band.setBackground(CbtStyle.NAVY);
        band.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        band.add(CbtStyle.label(EXAM_TITLE, Font.BOLD, 18, Color.WHITE), BorderLayout.WEST);
        band.add(CbtStyle.label(step, Font.PLAIN, 15, new Color(210, 222, 240)), BorderLayout.EAST);
        page.add(band, BorderLayout.NORTH);
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(card);
        page.add(center, BorderLayout.CENTER);
        return page;
    }

    private static JPanel card(int width) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CbtStyle.PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CbtStyle.LINE), BorderFactory.createEmptyBorder(28, 36, 28, 36)));
        card.setPreferredSize(null);
        card.setMinimumSize(new Dimension(width, 10));
        return card;
    }

    private static GridBagConstraints gbc(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(row == 0 ? 0 : 16, 0, 0, 0);
        return c;
    }

    private static JPanel buttonRow(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);
        for (JButton b : buttons) {
            b.setPreferredSize(new Dimension(Math.max(120, b.getPreferredSize().width), 42));
            row.add(b);
        }
        return row;
    }
}
