package examguard.ui;

import examguard.analysis.RuleResult;
import examguard.analysis.Sample;
import examguard.analysis.ScanRule;
import examguard.analysis.SuspicionReport;
import examguard.analysis.SuspicionScorer;
import examguard.analysis.TrajectoryRecorder;
import examguard.analysis.TrajectorySimulator;
import examguard.data.SessionResult;
import examguard.model.ExamPaper;
import examguard.model.Student;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;

/** 감독관 화면: 응시자의 궤적을 실시간 분석해 의심 점수를 보여준다 (학생 화면에는 표시하지 않음). */
public class DashboardTab extends JPanel {
    private static final Color RED = new Color(200, 40, 40);
    private static final Color ORANGE = new Color(215, 125, 0);
    private static final Color GREEN = new Color(30, 140, 60);

    private final TrajectoryRecorder recorder;
    private final SuspicionScorer scorer;
    private final JLabel sessionLabel = new JLabel(" ");
    private final JLabel totalLabel = new JLabel("-");
    private final JLabel levelLabel = new JLabel("데이터 부족");
    private final JProgressBar[] bars;
    private final JLabel[] details;
    private final TrajectoryView view = new TrajectoryView();
    private final JTextArea resultArea = new JTextArea(9, 40);

    private String studentText;
    private int questionCount;
    private int currentQuestion = -1;

    public DashboardTab(TrajectoryRecorder recorder, SuspicionScorer scorer) {
        this.recorder = recorder;
        this.scorer = scorer;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // ── 왼쪽: 점수
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(420, 10));

        sessionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(sessionLabel);
        left.add(spacer(10));

        JLabel caption = new JLabel("녹화 시도 의심 점수 (0~100)");
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(caption);
        JPanel scoreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        scoreRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 44f));
        levelLabel.setFont(levelLabel.getFont().deriveFont(Font.BOLD, 20f));
        scoreRow.add(totalLabel);
        scoreRow.add(levelLabel);
        scoreRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        left.add(scoreRow);
        left.add(spacer(8));

        List<ScanRule> rules = scorer.getRules();
        bars = new JProgressBar[rules.size()];
        details = new JLabel[rules.size()];
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < rules.size(); i++) {
            c.gridy = i * 2;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            JLabel name = new JLabel(rules.get(i).getName());
            name.setToolTipText(rules.get(i).getDescription());
            grid.add(name, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            bars[i] = new JProgressBar(0, 100);
            bars[i].setStringPainted(true);
            grid.add(bars[i], c);
            c.gridy = i * 2 + 1;
            c.gridx = 0;
            c.gridwidth = 2;
            details[i] = new JLabel(" ");
            details[i].setForeground(Color.GRAY);
            grid.add(details[i], c);
            c.gridwidth = 1;
        }
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60 * rules.size()));
        left.add(grid);
        left.add(spacer(12));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 6, 6));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setBorder(BorderFactory.createTitledBorder("시연용"));
        JButton replay = new JButton("궤적 재생");
        JButton simRead = new JButton("가짜 궤적: 읽기");
        JButton simSweep = new JButton("가짜 궤적: 훑기(녹화)");
        JButton clear = new JButton("궤적 지우기");
        replay.addActionListener(e -> view.startReplay());
        simRead.addActionListener(e -> simulate(false));
        simSweep.addActionListener(e -> simulate(true));
        clear.addActionListener(e -> { recorder.clear(); refresh(); });
        buttons.add(replay);
        buttons.add(simRead);
        buttons.add(simSweep);
        buttons.add(clear);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        left.add(buttons);
        left.add(spacer(8));

        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setText("제출된 시험이 없습니다.");
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("최근 제출 결과"));
        resultScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(resultScroll);

        add(left, BorderLayout.WEST);

        view.setBorder(BorderFactory.createTitledBorder(
                "이동 궤적 (파랑 = 마우스, 주황 = 방향키·스크롤, 오래될수록 옅게, 원 = 현재 스포트라이트)"));
        add(view, BorderLayout.CENTER);

        new Timer(500, e -> refresh()).start();
        refresh();
    }

    private static Component spacer(int h) {
        JPanel p = new JPanel();
        p.setMaximumSize(new Dimension(1, h));
        p.setPreferredSize(new Dimension(1, h));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    public void sessionStarted(Student student, int questions) {
        studentText = student.toString();
        questionCount = questions;
        currentQuestion = 0;
        resultArea.setText("시험 진행 중…");
        refresh();
    }

    public void sessionAborted() {
        studentText = null;
        resultArea.setText("시험이 중단되었습니다 (답안 미제출).");
        refresh();
    }

    public void setCurrentQuestion(int index) {
        currentQuestion = index;
    }

    public void showResult(SessionResult r, String savedTo) {
        studentText = null;
        ExamPaper p = r.getPaper();
        SuspicionReport rep = r.getReport();
        StringBuilder sb = new StringBuilder();
        sb.append("응시자: ").append(p.getStudent())
          .append(r.isKeyboardMode() ? "  [편의 제공 모드]" : "").append('\n');
        sb.append("점수: ").append(r.getScore()).append(" / ").append(p.getMaxScore()).append('\n');
        sb.append("의심 점수: ").append(rep.isSufficient() ? rep.getTotal() + "점 (" + rep.getLevel() + ")" : "데이터 부족")
          .append("  ·  ").append(rep.getInputSummary()).append("\n\n");

        sb.append("▶ 감독관 확인 권장 (판정 아님)\n");
        if (r.getReviewFlags().isEmpty()) {
            sb.append("  특이사항 없음\n");
        } else {
            for (String f : r.getReviewFlags()) sb.append("  ⚠ ").append(f).append('\n');
        }

        sb.append("\n문항별 머문 시간: ");
        long[] times = r.getTimeSpentMs();
        for (int i = 0; i < times.length; i++) {
            sb.append(i > 0 ? " · " : "").append(i + 1).append("번 ").append(times[i] / 1000).append("초");
        }
        sb.append("\n저장: ").append(savedTo);
        resultArea.setText(sb.toString());
        resultArea.setCaretPosition(0);
        refresh();
    }

    private void simulate(boolean sweep) {
        if (recorder.getAreaWidth() <= 0) recorder.setArea(760, 360);
        int w = recorder.getAreaWidth(), h = recorder.getAreaHeight();
        long start = recorder.lastTime() + 300;
        int q = Math.max(currentQuestion, 0);
        List<Sample> fake = sweep
                ? TrajectorySimulator.sweep(w, h, 90, start, q)
                : TrajectorySimulator.reading(w, h, 90, start, q, System.nanoTime());
        recorder.addAll(fake);
        refresh();
    }

    private void refresh() {
        List<Sample> samples = recorder.snapshot();
        int w = recorder.getAreaWidth() > 0 ? recorder.getAreaWidth() : 760;
        int h = recorder.getAreaHeight() > 0 ? recorder.getAreaHeight() : 360;
        SuspicionReport report = scorer.evaluate(samples, w, h);

        if (report.isSufficient()) {
            totalLabel.setText(String.valueOf(report.getTotal()));
            Color color = report.getTotal() >= 70 ? RED : report.getTotal() >= 40 ? ORANGE : GREEN;
            totalLabel.setForeground(color);
            levelLabel.setForeground(color);
        } else {
            totalLabel.setText("-");
            totalLabel.setForeground(Color.GRAY);
            levelLabel.setForeground(Color.GRAY);
        }
        levelLabel.setText(report.getLevel());

        List<RuleResult> results = report.getResults();
        for (int i = 0; i < bars.length && i < results.size(); i++) {
            int v = (int) Math.round(results.get(i).getScore() * 100);
            bars[i].setValue(v);
            bars[i].setString(v + "점");
            details[i].setText(results.get(i).getDetail());
        }

        String who = studentText == null ? "진행 중인 시험 없음" :
                "응시자 " + studentText + "  ·  문제 " + (currentQuestion + 1) + " / " + questionCount;
        sessionLabel.setText("<html><b>" + who + "</b><br><span style='color:gray'>궤적 " + samples.size()
                + "점 (" + report.getInputSummary() + ")</span></html>");
        view.setData(samples, w, h);
    }
}
