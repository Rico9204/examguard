package examguard;

import examguard.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** 실행 시작점 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // 기본 모양으로 실행
            }
            // 운영체제 언어와 상관없이 대화상자 버튼을 한국어로
            UIManager.put("OptionPane.yesButtonText", "예");
            UIManager.put("OptionPane.noButtonText", "아니요");
            UIManager.put("OptionPane.okButtonText", "확인");
            UIManager.put("OptionPane.cancelButtonText", "취소");
            new MainFrame().setVisible(true);
        });
    }
}
