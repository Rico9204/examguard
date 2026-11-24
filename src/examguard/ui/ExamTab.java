package examguard.ui;

import examguard.analysis.ReviewAdvisor;
import examguard.analysis.Sample;
import examguard.analysis.SuspicionReport;
import examguard.analysis.SuspicionScorer;
import examguard.analysis.TrajectoryRecorder;
import examguard.data.AutoSaveStore;
import examguard.data.Repository;
import examguard.data.SessionResult;
import examguard.display.AntiOcrWatermarkLayer;
import examguard.display.ColorKeyLayer;
import examguard.display.LineRulerLayer;
import examguard.display.ZeroInfoSpotlightLayer;
import examguard.display.MotionNoiseLayer;
import examguard.display.TemporalMaskLayer;
import examguard.display.PlainLayer;
import examguard.display.ProtectionLayer;
import examguard.display.SpotlightLayer;
import examguard.exam.ExamSession;
import examguard.fingerprint.AnswerCheck;
import examguard.fingerprint.ExamGenerator;
import examguard.fingerprint.LeakTracer;
import examguard.model.ExamPaper;
import examguard.model.Student;
import examguard.ui.cbt.CbtExamFrame;

import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * 감독관이 응시자와 보호 방식을 정하고 CBT 시험 창을 여는 화면.
 * 시험 자체는 별도 창(CbtExamFrame)에서 진행된다. 감독관 PC와 수험자 PC가 분리된 실제 시험장을 흉내 낸 구조.
 */
public class ExamTab extends JPanel {
    private static final int EXAM_SECONDS = 15 * 60;

    private final ExamGenerator generator;
    private final LeakTracer tracer;
    private final TrajectoryRecorder recorder;
    private final SuspicionScorer scorer;
    private final Repository repository;
    private final DashboardTab dashboard;

    private final JComboBox<Student> studentBox;
    private final JComboBox<ProtectionLayer> layerBox;
    private final JSlider radiusSlider = new JSlider(40, 220, 90);
    private final JButton openButton = new JButton("CBT 시험 창 열기");
    private final JCheckBox keyboardBox = new JCheckBox("방향키 이동 허용 (편의 제공)");
    private final AutoSaveStore autoSave = new AutoSaveStore(Paths.get("data", "autosave"));
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);

    private ProtectionLayer lastLayer;
    private CbtExamFrame examFrame;

    public ExamTab(List<Student> roster, ExamGenerator generator, LeakTracer tracer, TrajectoryRecorder recorder,
                   SuspicionScorer scorer, Repository repository, DashboardTab dashboard) {
        this.generator = generator;
        this.tracer = tracer;
        this.recorder = recorder;
        this.scorer = scorer;
        this.repository = repository;
        this.dashboard = dashboard;

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        studentBox = new JComboBox<>(roster.toArray(new Student[0]));
        layerBox = new JComboBox<>(new ProtectionLayer[]{new SpotlightLayer(), new ZeroInfoSpotlightLayer(),
                new LineRulerLayer(), new MotionNoiseLayer(),
                new TemporalMaskLayer(), new AntiOcrWatermarkLayer(),
                new ColorKeyLayer(generator.examSeed("color-key")), new PlainLayer()});
        layerBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof ProtectionLayer) setText(((ProtectionLayer) value).getName());
                return this;
            }
        });
        lastLayer = (ProtectionLayer) layerBox.getSelectedItem();
        layerBox.addActionListener(e -> onLayerChanged());
        radiusSlider.setPreferredSize(new Dimension(150, radiusSlider.getPreferredSize().height));
        radiusSlider.addChangeListener(e -> {
            if (examFrame != null) examFrame.setRadius(radiusSlider.getValue());
        });
        openButton.setFont(openButton.getFont().deriveFont(Font.BOLD, 14f));
        openButton.addActionListener(e -> openExam());

        JPanel setup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        setup.add(new JLabel("응시자"));
        setup.add(studentBox);
        setup.add(Box.createHorizontalStrut(10));
        setup.add(new JLabel("보호 방식"));
        setup.add(layerBox);
        setup.add(Box.createHorizontalStrut(10));
        setup.add(new JLabel("스포트라이트 반경"));
        setup.add(radiusSlider);
        keyboardBox.setToolTipText("마우스 사용이 어려운 수험자(편의 제공 신청자)에게만 켜세요. 방향키 이동도 기록·분석됩니다.");
        // 한 줄 FlowLayout은 줄바꿈이 안 돼 좁은 창·고배율 화면에서 버튼이 잘리므로 둘째 줄로 분리
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(keyboardBox);
        actions.add(openButton);
        JPanel top = new JPanel(new GridLayout(0, 1, 0, 6));
        top.add(setup);
        top.add(actions);
        add(top, BorderLayout.NORTH);

        JLabel guide = new JLabel("<html><div style='text-align:center; width:600px'>"
                + "<h2>응시 시작 (감독관)</h2>"
                + "<p>응시자와 보호 방식을 고른 뒤 <b>CBT 시험 창 열기</b>를 누르세요.</p><br>"
                + "<p>새 창에서 실제 CBT 시험장과 같은 순서로 진행됩니다.<br>"
                + "수험자 정보 확인 → 안내사항 → 문제 풀이 → 답안 제출</p><br>"
                + "<p style='color:gray'>시험 중 마우스 궤적은 감독 대시보드(②)에서 실시간으로 분석됩니다.<br>"
                + "모니터가 두 대라면 시험 창과 대시보드를 나란히 띄워 시연하세요.</p>"
                + "</div></html>", SwingConstants.CENTER);
        add(guide, BorderLayout.CENTER);

        status.setFont(status.getFont().deriveFont(Font.BOLD, 14f));
        add(status, BorderLayout.SOUTH);
    }

    private void onLayerChanged() {
        ProtectionLayer chosen = (ProtectionLayer) layerBox.getSelectedItem();
        if (chosen == lastLayer) return;
        if (chosen.isAnimated()) { // 움직임 노이즈·시간차 마스킹 모두 화면이 계속 바뀐다
            int ok = JOptionPane.showConfirmDialog(this,
                    "이 모드는 문제 일부를 계속 움직이거나 깜빡이는 점(노이즈)으로 표시합니다.\n"
                    + "시간차 마스킹(기본 3장)은 약 20Hz로 깜빡여 광과민성 민감 대역(약 15~25Hz) 안입니다.\n"
                    + "빛 자극에 민감한 분(광과민성 발작 위험)은 사용하지 마세요.\n"
                    + "시험 중 [깜빡임 끄기] 또는 F2로 언제든 정지 화면으로 바꿀 수 있고(감독관 기록에 남음),\n"
                    + "깜빡이는 화면은 10분마다 25초 눈 휴식이 있습니다(그만큼 시험 시간 연장).\n\n계속할까요?",
                    "주의: 움직이는 화면", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) {
                layerBox.setSelectedItem(lastLayer);
                return;
            }
        }
        lastLayer = chosen;
    }

    private void openExam() {
        Student student = (Student) studentBox.getSelectedItem();
        ExamPaper paper = generator.generate(student);
        ExamSession session = new ExamSession(paper, EXAM_SECONDS, String.format("%02d", student.getId()),
                keyboardBox.isSelected());

        // 정전·오류로 꺼졌던 응시가 있으면 이어서 응시할지 묻는다
        AutoSaveStore.Progress saved = autoSave.load(student.getStudentNo());
        if (saved != null) {
            int ok = JOptionPane.showConfirmDialog(this, String.format(
                    "%s의 제출되지 않은 응시 기록이 있습니다.%n표기한 문제 %d개, 경과 시간 %d분 %d초%n%n이어서 응시할까요? "
                    + "([아니요]를 누르면 기록을 지우고 처음부터 시작합니다)",
                    student, saved.answers.size(), saved.elapsedSeconds / 60, saved.elapsedSeconds % 60),
                    "이어서 응시", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok == JOptionPane.YES_OPTION) session.restore(saved.answers, saved.elapsedSeconds, saved.timeSpentMs);
            else autoSave.delete(student.getStudentNo());
            recorder.clear();
            // 이어서 응시하면 중단 전 궤적도 이어 붙인다 (창을 닫아 기록을 지우는 우회로 차단)
            if (ok == JOptionPane.YES_OPTION) recorder.addAll(saved.samples);
        } else {
            recorder.clear();
        }
        dashboard.sessionStarted(student, paper.getVariants().size());

        examFrame = new CbtExamFrame(session, lastLayer, radiusSlider.getValue(), recorder,
                dashboard::setCurrentQuestion, this::finish, this::aborted, this::autosave);
        examFrame.setVisible(true);
        setControlsEnabled(false);
        status.setText("응시 중: " + student + (session.isKeyboardMode() ? "  [편의 제공 모드]" : "") + "  (CBT 시험 창)");
    }

    /** CBT 창에서 답안을 제출하면 호출된다: 채점, 분석, 저장, 대시보드 표시 */
    private void finish(ExamSession session) {
        ExamPaper paper = session.getPaper();
        List<Sample> samples = recorder.snapshot();
        SuspicionReport report = scorer.evaluate(samples, recorder.getAreaWidth(), recorder.getAreaHeight());
        int score = paper.grade(session.getAnswers());
        List<String> foreign = AnswerCheck.findForeignAnswers(paper, session.getAnswers(), tracer.getPapers());
        long[] times = session.getTimeSpentMs();
        List<String> flags = new java.util.ArrayList<>(ReviewAdvisor.advise(paper, session.getAnswers(), times, report,
                foreign, session.isKeyboardMode()));
        flags.addAll(session.getSupervisorNotes()); // 깜빡임 끄기 사용 시각 등
        SessionResult result = new SessionResult(paper, session.getAnswers(), score, samples, report, foreign,
                flags, times, session.isKeyboardMode(), session.getStartedAt(), System.currentTimeMillis());
        autoSave.delete(paper.getStudent().getStudentNo());

        String savedTo;
        try {
            savedTo = repository.save(result).toString();
        } catch (IOException ex) {
            savedTo = "저장 실패: " + ex.getMessage();
        }
        dashboard.showResult(result, savedTo);
        examFrame = null;
        setControlsEnabled(true);
        status.setText("제출 완료: " + paper.getStudent() + "  ·  결과는 감독 대시보드(②)에서 확인");
    }

    private void autosave(ExamSession session) {
        try {
            autoSave.save(session, recorder.snapshot());
        } catch (IOException ex) {
            System.err.println("[임시 저장 실패] " + ex.getMessage());
        }
    }

    private void aborted() {
        dashboard.sessionAborted();
        examFrame = null;
        setControlsEnabled(true);
        status.setText("시험이 중단되었습니다 (답안 미제출, 임시 저장됨 — 같은 수험자로 다시 열면 이어서 응시).");
    }

    private void setControlsEnabled(boolean enabled) {
        studentBox.setEnabled(enabled);
        layerBox.setEnabled(enabled);
        keyboardBox.setEnabled(enabled);
        openButton.setEnabled(enabled);
    }
}
