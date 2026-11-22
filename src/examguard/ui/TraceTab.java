package examguard.ui;

import examguard.fingerprint.LeakTracer;
import examguard.fingerprint.TraceReport;
import examguard.fingerprint.TraceResult;
import examguard.model.ExamPaper;
import examguard.model.QuestionVariant;
import examguard.model.Student;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 관리자용: 유출된 시험지 텍스트를 붙여 넣으면 누구의 시험지인지 순위로 보여준다. */
public class TraceTab extends JPanel {
    private final LeakTracer tracer;
    private final JTextArea leakArea = new JTextArea();
    private final JLabel summary = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"순위", "학생", "선택 지점 일치", "숫자 일치", "일치율"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final Random random = new Random();
    private Student sampleLeaker;

    public TraceTab(LeakTracer tracer) {
        this.tracer = tracer;
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(new JLabel("<html>온라인에 퍼진 시험 문제(사진을 옮겨 적은 글, 일부만이어도 됨)를 붙여 넣으면, "
                + "선택 지점의 표현과 숫자를 학생별 시험지와 대조해 <b>누구의 시험지인지</b> 추정합니다. "
                + "띄어쓰기가 달라도 찾을 수 있습니다.</html>"), BorderLayout.NORTH);

        leakArea.setLineWrap(true);
        leakArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        JScrollPane leakScroll = new JScrollPane(leakArea);
        leakScroll.setBorder(BorderFactory.createTitledBorder("유출본 붙여 넣기"));

        JButton sampleButton = new JButton("샘플 유출본 만들기");
        JButton traceButton = new JButton("추적 실행");
        sampleButton.addActionListener(e -> makeSample());
        traceButton.addActionListener(e -> runTrace());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(sampleButton);
        buttons.add(traceButton);

        JPanel left = new JPanel(new BorderLayout());
        left.add(leakScroll, BorderLayout.CENTER);
        left.add(buttons, BorderLayout.SOUTH);

        JTable table = new JTable(model);
        table.setRowHeight(24);
        int[] widths = {40, 150, 100, 80, 70};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JPanel right = new JPanel(new BorderLayout(0, 8));
        summary.setVerticalAlignment(JLabel.TOP);
        right.add(summary, BorderLayout.NORTH);
        right.add(new JScrollPane(table), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);
    }

    /** 무작위 학생 한 명의 문제 1~2개를 띄어쓰기를 바꿔 옮겨 적은 상황을 만든다. */
    private void makeSample() {
        List<ExamPaper> papers = tracer.getPapers();
        ExamPaper p = papers.get(random.nextInt(papers.size()));
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < p.getVariants().size(); i++) idx.add(i);
        Collections.shuffle(idx, random);
        List<Integer> chosen = new ArrayList<>(idx.subList(0, 1 + random.nextInt(2)));
        Collections.sort(chosen);

        StringBuilder sb = new StringBuilder();
        for (int q : chosen) {
            QuestionVariant v = p.getVariants().get(q);
            sb.append(v.getText()).append('\n');
            for (int j = 0; j < v.getChoices().size(); j++) {
                sb.append(ExamPaper.CIRCLED[j]).append(' ').append(v.getChoices().get(j)).append('\n');
            }
            sb.append('\n');
        }
        leakArea.setText(respace(sb.toString()));
        leakArea.setCaretPosition(0);
        sampleLeaker = p.getStudent();

        StringBuilder qs = new StringBuilder();
        for (int q : chosen) qs.append(qs.length() > 0 ? ", " : "").append("문제 ").append(q + 1);
        model.setRowCount(0);
        summary.setText("<html>샘플 유출본: 무작위 학생 1명의 " + qs
                + "를 띄어쓰기를 바꿔 옮겨 적은 상황입니다.<br>누구인지는 <b>추적 실행</b> 후 공개됩니다.</html>");
    }

    private String respace(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == ' ' && random.nextDouble() < 0.3) continue;
            sb.append(c);
            if (c >= '가' && c <= '힣' && random.nextDouble() < 0.05) sb.append(' ');
        }
        return sb.toString();
    }

    private void runTrace() {
        TraceReport report = tracer.trace(leakArea.getText());
        model.setRowCount(0);
        if (!report.hasEvidence()) {
            summary.setText("<html>시험지 식별 표시를 찾지 못했습니다.<br>"
                    + "문제 문장을 더 많이 붙여 넣어 보세요.</html>");
            return;
        }
        List<TraceResult> results = report.getResults();
        for (int i = 0; i < Math.min(10, results.size()); i++) {
            TraceResult r = results.get(i);
            model.addRow(new Object[]{
                    i + 1,
                    r.getStudent().toString(),
                    r.getMatchedPoints() + " / " + r.getObservedPoints(),
                    r.getMatchedNumbers() + " / " + r.getObservedNumbers(),
                    String.format("%.0f%%", r.getScore() * 100)});
        }

        StringBuilder qs = new StringBuilder();
        for (int q : report.getDetectedQuestions()) qs.append(qs.length() > 0 ? ", " : "").append(q + 1);
        StringBuilder html = new StringBuilder("<html>");
        html.append("발견한 문제: ").append(qs).append("  ·  판독한 선택 지점 ").append(report.getObservedPoints())
            .append("곳  ·  대조한 숫자 ").append(report.getObservedNumbers()).append("개<br>");
        html.append("신뢰도: <b>").append(report.getConfidence()).append("</b>");
        double top = results.get(0).getScore();
        int tied = 0;
        boolean leakerInTop = false;
        for (TraceResult r : results) {
            if (r.getScore() < top) break;
            tied++;
            if (r.getStudent() == sampleLeaker) leakerInTop = true;
        }
        if (tied > 1) html.append("<br>공동 1위: ").append(tied).append("명");
        if (sampleLeaker != null) {
            html.append("<br>실제 유출자(샘플): ").append(sampleLeaker).append(" → ");
            if (leakerInTop && tied == 1) {
                html.append("<span style='color:green'>단독 1위로 찾아냄</span>");
            } else if (leakerInTop) {
                html.append("<span style='color:#b46400'>공동 1위 ").append(tied)
                    .append("명 안에 있음 (유출 분량이 적어 더 좁히지 못함)</span>");
            } else {
                html.append("<span style='color:#c80000'>1위 후보에 없음</span>");
            }
        }
        html.append("<br><span style='color:gray'>※ 추적 결과는 처벌 근거가 아니라 조사를 시작할 근거입니다.</span></html>");
        summary.setText(html.toString());
    }
}
