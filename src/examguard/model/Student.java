package examguard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 응시자 한 명. 학번이 학생별 시험지 변형의 기준(시드)이 된다. */
public class Student {
    private final int id;
    private final String studentNo;
    private final String name;

    public Student(int id, String studentNo, String name) {
        this.id = id;
        this.studentNo = studentNo;
        this.name = name;
    }

    public int getId() { return id; }
    public String getStudentNo() { return studentNo; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return studentNo + " " + name;
    }

    /** 시연용 명단: 학번 20260001 ~ */
    public static List<Student> createRoster(int count) {
        List<Student> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new Student(i, String.format("2026%04d", i), String.format("학생%02d", i)));
        }
        return Collections.unmodifiableList(list);
    }
}
