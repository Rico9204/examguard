package examguard.ui;

import examguard.fingerprint.ExamGenerator;
import examguard.model.Student;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/** 관리자용: 두 학생의 시험지를 나란히 비교한다. */
public class PreviewTab extends JPanel {
    public PreviewTab(List<Student> roster, ExamGenerator generator) {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(new JLabel("<html>같은 문제 은행에서 학생마다 <b>문장 표현, 숫자, 보기 순서</b>가 다른 시험지가 만들어집니다. "
                + "두 학생을 골라 비교해 보세요. 식별 코드는 학번에서 만들어지며, 선택 지점마다 어떤 표현이 들어갈지 정합니다.</html>"),
                BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 2, 10, 0));
        columns.add(column(roster, generator, 0));
        columns.add(column(roster, generator, 1));
        add(columns, BorderLayout.CENTER);

        List<String> warnings = generator.validate();
        JLabel status = new JLabel(warnings.isEmpty()
                ? "문제 은행 검사: 선택 표현끼리 겹치는 곳 없음 (추적 가능)"
                : "<html>문제 은행 경고:<br>" + String.join("<br>", warnings) + "</html>");
        add(status, BorderLayout.SOUTH);
    }

    private JPanel column(List<Student> roster, ExamGenerator generator, int initial) {
        JComboBox<Student> box = new JComboBox<>(roster.toArray(new Student[0]));
        box.setSelectedIndex(initial);
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        area.setBorder(new EmptyBorder(8, 8, 8, 8));

        Runnable update = () -> {
            area.setText(generator.generate((Student) box.getSelectedItem()).toPreviewText());
            area.setCaretPosition(0);
        };
        box.addActionListener(e -> update.run());
        update.run();

        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.add(box, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(getBackground().darker()));
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }
}
