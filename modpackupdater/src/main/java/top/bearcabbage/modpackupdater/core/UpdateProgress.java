package top.bearcabbage.modpackupdater.core;

import java.awt.*;
import javax.swing.*;

/** JDK desktop progress window; unavailable desktops fall back to the same helper log. */
public final class UpdateProgress {
    private static JFrame frame;
    private static JLabel message;
    private static JProgressBar progress;
    private static JButton close;
    private UpdateProgress() {}
    private static String label(String text) {
        return "<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace(" / ", "<br>") + "</html>";
    }

    public static void open() {
        try {
            if (GraphicsEnvironment.isHeadless()) return;
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("MirrorTree · Minecraft Update / 整合包更新");
                frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                var panel = new JPanel(new BorderLayout(12, 14));
                panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
                var title = new JLabel("Updating Minecraft / 正在更新，请稍候");
                title.setFont(title.getFont().deriveFont(Font.BOLD, 17));
                message = new JLabel(label("Waiting for Minecraft to close / 等待游戏保存并关闭…"));
                progress = new JProgressBar(); progress.setIndeterminate(true);
                var center = new JPanel(new BorderLayout(8, 12));
                center.add(message, BorderLayout.NORTH); center.add(progress, BorderLayout.CENTER);
                close = new JButton("Close / 关闭"); close.setEnabled(false);
                close.addActionListener(event -> frame.dispose());
                panel.add(title, BorderLayout.NORTH); panel.add(center, BorderLayout.CENTER); panel.add(close, BorderLayout.SOUTH);
                frame.setContentPane(panel); frame.setSize(540, 210);
                frame.setLocationRelativeTo(null); frame.setVisible(true);
            });
            System.out.println("Update progress window displayed");
        } catch (Exception | LinkageError | AWTError unavailable) {
            frame = null;
            System.out.println("Desktop progress unavailable; continuing with update log");
        }
    }

    public static void message(String text) {
        System.out.println(text);
        if (frame != null) SwingUtilities.invokeLater(() -> message.setText(label(text)));
    }

    public static void error(String text) {
        System.err.println(text);
        if (frame != null) SwingUtilities.invokeLater(() -> {
            message.setText(label(text)); progress.setIndeterminate(false); close.setEnabled(true);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        });
    }

    public static void close() {
        if (frame != null) SwingUtilities.invokeLater(() -> frame.dispose());
    }
}
