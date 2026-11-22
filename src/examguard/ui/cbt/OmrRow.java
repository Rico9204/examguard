package examguard.ui.cbt;

import examguard.exam.ExamSession;
import examguard.model.QuestionVariant;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/**
 * 답안 표기란의 한 줄: [문제 번호] ① ② ③ ④  또는  [문제 번호] [숫자 입력칸]
 * 번호를 누르면 그 문제로 이동한다.
 */
class OmrRow extends JPanel {
    private final int index;
    private final ExamSession session;
    private final Runnable onChanged;
    private final JLabel number;
    private final OmrBubble[] bubbles;
    private final JTextField field;

    OmrRow(int index, QuestionVariant variant, ExamSession session, IntConsumer onNavigate, Runnable onChanged) {
        this.index = index;
        this.session = session;
        this.onChanged = onChanged;
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));
        setBackground(CbtStyle.PANEL);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CbtStyle.LINE));

        number = new JLabel(String.valueOf(index + 1), JLabel.CENTER);
        number.setPreferredSize(new Dimension(34, 30));
        number.setOpaque(true);
        number.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        number.setToolTipText("문제 " + (index + 1) + "로 이동");
        number.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onNavigate.accept(index); }
        });
        add(number);

        if (variant.isMultipleChoice()) {
            bubbles = new OmrBubble[variant.getChoices().size()];
            for (int k = 0; k < bubbles.length; k++) {
                final String value = String.valueOf(k + 1);
                bubbles[k] = new OmrBubble(value, () -> choose(value, onNavigate));
                add(bubbles[k]);
            }
            field = null;
        } else {
            bubbles = new OmrBubble[0];
            field = new JTextField(8);
            field.setFont(CbtStyle.font(Font.PLAIN, 15));
            field.setToolTipText("숫자로 입력");
            String saved = session.getAnswer(index);
            if (saved != null) field.setText(saved); // 이어서 응시하는 경우
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { typed(); }
                @Override public void removeUpdate(DocumentEvent e) { typed(); }
                @Override public void changedUpdate(DocumentEvent e) { typed(); }
            });
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { onNavigate.accept(index); }
            });
            add(field);
            add(CbtStyle.label("(숫자)", Font.PLAIN, 12, CbtStyle.MUTED));
        }
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        refresh();
    }

    /** 같은 번호를 다시 누르면 표기를 지운다. */
    private void choose(String value, IntConsumer onNavigate) {
        session.setAnswer(index, value.equals(session.getAnswer(index)) ? null : value);
        onNavigate.accept(index);
        refresh();
        onChanged.run();
    }

    private void typed() {
        session.setAnswer(index, field.getText());
        refreshNumber();
        onChanged.run();
    }

    void refresh() {
        String answer = session.getAnswer(index);
        for (int k = 0; k < bubbles.length; k++) {
            bubbles[k].setSelected(String.valueOf(k + 1).equals(answer));
        }
        refreshNumber();
    }

    private void refreshNumber() {
        boolean answered = session.isAnswered(index);
        number.setFont(CbtStyle.font(Font.BOLD, 15));
        number.setBackground(answered ? CbtStyle.NAVY : CbtStyle.BACKGROUND);
        number.setForeground(answered ? java.awt.Color.WHITE : CbtStyle.INK);
    }

    void setCurrent(boolean current) {
        setBackground(current ? CbtStyle.CURRENT_ROW : CbtStyle.PANEL);
    }

    void setInputEnabled(boolean enabled) {
        for (OmrBubble b : bubbles) b.setEnabled(enabled);
        if (field != null) field.setEnabled(enabled);
    }
}
