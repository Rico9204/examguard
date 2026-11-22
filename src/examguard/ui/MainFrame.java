package examguard.ui;

import examguard.analysis.SuspicionScorer;
import examguard.analysis.TrajectoryRecorder;
import examguard.data.FileRepository;
import examguard.data.Repository;
import examguard.fingerprint.ExamGenerator;
import examguard.fingerprint.LeakTracer;
import examguard.model.QuestionBank;
import examguard.model.Student;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import java.nio.file.Paths;
import java.util.List;

/** 탭 네 개로 된 메인 창. 객체들을 만들어 각 탭에 연결(주입)한다. */
public class MainFrame extends JFrame {
    /** 실제로는 교수자만 아는 값. 이 값이 바뀌면 시험지도 전부 달라진다. */
    private static final String SECRET_KEY = "class-2026-final-secret";

    public MainFrame() {
        super("시험 공정성 보호 시스템 (프로토타입)");

        List<Student> roster = Student.createRoster(40);
        QuestionBank bank = QuestionBank.createSample();
        ExamGenerator generator = ExamGenerator.createDefault(bank, SECRET_KEY);
        LeakTracer tracer = new LeakTracer(generator, roster);
        TrajectoryRecorder recorder = new TrajectoryRecorder();
        SuspicionScorer scorer = SuspicionScorer.createDefault();
        Repository repository = new FileRepository(Paths.get("data"));

        DashboardTab dashboard = new DashboardTab(recorder, scorer);
        ExamTab exam = new ExamTab(roster, generator, tracer, recorder, scorer, repository, dashboard);
        PreviewTab preview = new PreviewTab(roster, generator);
        TraceTab trace = new TraceTab(tracer);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("① 응시 시작", exam);
        tabs.addTab("② 감독 대시보드", dashboard);
        tabs.addTab("③ 시험지 미리보기", preview);
        tabs.addTab("④ 유출 추적", trace);
        setContentPane(tabs);

        for (String w : generator.validate()) System.err.println("[문제 은행 경고] " + w);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1120, 780);
        setLocationRelativeTo(null);
    }
}
