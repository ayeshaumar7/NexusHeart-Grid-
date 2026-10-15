import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

class NexusHeartGridHUD extends JFrame {

    static final Color BG        = Color.decode("#0B0E14");
    static final Color PANEL_BG  = Color.decode("#11151F");
    static final Color WAVE_BG   = Color.decode("#05070C");
    static final Color CYAN      = Color.decode("#00E5FF");
    static final Color CRIMSON   = Color.decode("#FF1744");
    static final Color AMBER     = Color.decode("#FFAB00");
    static final Color EMERALD   = Color.decode("#00E676");
    static final Color GRID      = new Color(0, 229, 255, 35);
    static final Color TEXT_DIM  = new Color(160, 180, 210);

    private volatile boolean beltLocked        = true;
    private volatile int     batteryLevel      = 78;
    private volatile boolean chargerFail       = false;
    private volatile boolean overheat          = false;
    private volatile boolean noiseDisturbance  = false;
    private volatile double  chargingVoltage   = 5.0;

    private volatile boolean batteryCriticalDispatched = false;
    private volatile boolean beltCriticalDispatched     = false;

    private String contactPrimary   = "+92";
    private String contactSecondary = "+92";
    private String contactDoctor    = "+92 ";

    private JLabel clockLabel;
    private JLabel connectionBadge;
    private JLabel systemStatusLabel;
    private JLabel beltLockLabel;
    private JLabel thermalLabel;
    private OscilloscopePanel scope;
    private RadialGauge batteryGauge;
    private RadialGauge voltageGauge;
    private JTextArea systemLog;
    private JSlider batterySlider;
    private JToggleButton beltShiftToggle;
    private JToggleButton chargerToggle;
    private JToggleButton overheatToggle;
    private JToggleButton noiseToggle;

    private final ExecutorService dispatchExecutor = Executors.newFixedThreadPool(3);

    public NexusHeartGridHUD() {
        super("NexusHeart Grid :: Wireless Heart Power Belt Telemetry System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1440, 900);
        setMinimumSize(new Dimension(1200, 800));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(6, 6));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);
        add(buildTestingPanel(), BorderLayout.SOUTH);

        startClock();
        startTelemetryLoop();
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(PANEL_BG);
        bar.setBorder(new EmptyBorder(10, 16, 10, 16));

        JLabel title = new JLabel("NEXUSHEART GRID  //  HEART POWER BELT TELEMETRY");
        title.setFont(new Font("Consolas", Font.BOLD, 20));
        title.setForeground(CYAN);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        right.setOpaque(false);

        systemStatusLabel = badgeLabel("SYSTEM: NOMINAL", EMERALD);
        connectionBadge   = badgeLabel("BLE LINKED", EMERALD);
        clockLabel        = badgeLabel("--:--:--", TEXT_DIM);

        right.add(systemStatusLabel);
        right.add(connectionBadge);
        right.add(clockLabel);

        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JLabel badgeLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Consolas", Font.BOLD, 13));
        l.setForeground(color);
        l.setBorder(new CompoundBorder(new LineBorder(color, 1, true), new EmptyBorder(4, 10, 4, 10)));
        return l;
    }

    private JPanel buildCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBackground(BG);
        center.setBorder(new EmptyBorder(0, 12, 0, 0));

        JLabel header = new JLabel("LIVE TELEMETRY :: INDUCTION PULSE / CARDIAC WAVEFORM");
        header.setFont(new Font("Consolas", Font.BOLD, 13));
        header.setForeground(TEXT_DIM);

        scope = new OscilloscopePanel(400);
        scope.setBorder(new LineBorder(new Color(0, 229, 255, 60), 1));

        beltLockLabel = badgeLabel("BELT: LOCKED", EMERALD);
        thermalLabel  = badgeLabel("THERMAL: NORMAL", EMERALD);
        JPanel statusStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        statusStrip.setOpaque(false);
        statusStrip.add(beltLockLabel);
        statusStrip.add(thermalLabel);

        center.add(header, BorderLayout.NORTH);
        center.add(scope, BorderLayout.CENTER);
        center.add(statusStrip, BorderLayout.SOUTH);
        return center;
    }

    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(BG);
        side.setPreferredSize(new Dimension(340, 0));
        side.setBorder(new EmptyBorder(0, 8, 0, 8));

        side.add(sectionPanel("POWER & ALIGNMENT", buildGaugeRow()));
        side.add(Box.createVerticalStrut(10));
        side.add(sectionPanel("EMERGENCY DISPATCH CONTACTS", buildContactsPanel()));
        side.add(Box.createVerticalStrut(10));
        side.add(sectionPanel("SYSTEM LOG", buildLogPanel()));
        return side;
    }

    private JPanel sectionPanel(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(PANEL_BG);
        p.setBorder(new CompoundBorder(new LineBorder(new Color(255,255,255,20), 1),
                new EmptyBorder(10, 10, 10, 10)));
        JLabel l = new JLabel(title);
        l.setFont(new Font("Consolas", Font.BOLD, 12));
        l.setForeground(CYAN);
        p.add(l, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildGaugeRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setOpaque(false);
        batteryGauge = new RadialGauge("BATTERY %");
        voltageGauge = new RadialGauge("VOLTAGE (V)");
        row.add(batteryGauge);
        row.add(voltageGauge);
        return row;
    }

    private JPanel buildContactsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        c.gridx = 0;

        JTextField f1 = styledField(contactPrimary);
        JTextField f2 = styledField(contactSecondary);
        JTextField f3 = styledField(contactDoctor);

        c.gridy = 0; p.add(fieldLabel("1. Primary Family Contact"), c);
        c.gridy = 1; p.add(f1, c);
        c.gridy = 2; p.add(fieldLabel("2. Secondary Caregiver Contact"), c);
        c.gridy = 3; p.add(f2, c);
        c.gridy = 4; p.add(fieldLabel("3. Cardiologist / Doctor"), c);
        c.gridy = 5; p.add(f3, c);

        JButton save = new JButton("SAVE & LOCK CONTACTS");
        styleButton(save, EMERALD);
        save.addActionListener(e -> {
            contactPrimary   = f1.getText().trim();
            contactSecondary = f2.getText().trim();
            contactDoctor    = f3.getText().trim();
            appendLog("Emergency contacts updated and locked in memory.");
        });
        c.gridy = 6; c.insets = new Insets(10, 0, 0, 0);
        p.add(save, c);

        return p;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Consolas", Font.PLAIN, 11));
        l.setForeground(TEXT_DIM);
        return l;
    }

    private JTextField styledField(String initial) {
        JTextField f = new JTextField(initial);
        f.setFont(new Font("Consolas", Font.PLAIN, 13));
        f.setBackground(new Color(20, 24, 34));
        f.setForeground(CYAN);
        f.setCaretColor(CYAN);
        f.setBorder(new CompoundBorder(new LineBorder(new Color(0,229,255,60), 1), new EmptyBorder(4,6,4,6)));
        return f;
    }

    private JPanel buildLogPanel() {
        systemLog = new JTextArea();
        systemLog.setEditable(false);
        systemLog.setFont(new Font("Consolas", Font.PLAIN, 11));
        systemLog.setBackground(new Color(6, 8, 12));
        systemLog.setForeground(EMERALD);
        systemLog.setLineWrap(true);
        systemLog.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(systemLog);
        scroll.setPreferredSize(new Dimension(300, 220));
        scroll.setBorder(new LineBorder(new Color(255,255,255,20)));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(scroll, BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buildTestingPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(PANEL_BG);
        outer.setBorder(new CompoundBorder(new LineBorder(new Color(255,255,255,20)), new EmptyBorder(10,14,10,14)));

        JLabel header = new JLabel("HARDWARE-FREE TESTING & VIVA SIMULATION CONTROL PANEL");
        header.setFont(new Font("Consolas", Font.BOLD, 12));
        header.setForeground(AMBER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        controls.setOpaque(false);

        beltShiftToggle = new JToggleButton("Shift / Misalign Belt");
        chargerToggle   = new JToggleButton("Charger Breakdown");
        overheatToggle  = new JToggleButton("Overheat / Thermal Hotspot");
        noiseToggle     = new JToggleButton("Signal Disturbance / Noise");
        for (JToggleButton b : new JToggleButton[]{beltShiftToggle, chargerToggle, overheatToggle, noiseToggle}) {
            styleToggle(b);
        }

        beltShiftToggle.addActionListener(e -> {
            beltLocked = !beltShiftToggle.isSelected();
            appendLog("Belt alignment set to " + (beltLocked ? "LOCKED" : "DISPLACED") + ".");
            if (beltLocked) beltCriticalDispatched = false;
        });
        chargerToggle.addActionListener(e -> {
            chargerFail = chargerToggle.isSelected();
            appendLog("Charging circuit " + (chargerFail ? "FAILED (induction loss)." : "restored."));
        });
        overheatToggle.addActionListener(e -> {
            overheat = overheatToggle.isSelected();
            appendLog("Thermal hotspot simulation " + (overheat ? "ACTIVATED." : "cleared."));
        });
        noiseToggle.addActionListener(e -> {
            noiseDisturbance = noiseToggle.isSelected();
            appendLog("Signal disturbance " + (noiseDisturbance ? "injected." : "cleared."));
        });

        JPanel sliderPanel = new JPanel(new BorderLayout());
        sliderPanel.setOpaque(false);
        JLabel sliderLabel = fieldLabel("Battery Level Simulation");
        batterySlider = new JSlider(0, 100, batteryLevel);
        batterySlider.setOpaque(false);
        batterySlider.addChangeListener(e -> {
            batteryLevel = batterySlider.getValue();
            if (batteryLevel > 6) batteryCriticalDispatched = false;
        });
        sliderPanel.add(sliderLabel, BorderLayout.NORTH);
        sliderPanel.add(batterySlider, BorderLayout.CENTER);
        sliderPanel.setPreferredSize(new Dimension(220, 46));

        JButton sos = new JButton("SOS: MANUAL");
        styleButton(sos, CRIMSON);
        sos.addActionListener(e -> triggerEmergencyDispatch("MANUAL SOS ACTIVATION"));

        JButton severe = new JButton("TRIGGER SEVERE EMERGENCY FAULT");
        styleButton(severe, CRIMSON);
        severe.addActionListener(e -> {
            beltShiftToggle.setSelected(true);
            beltLocked = false;
            noiseToggle.setSelected(true);
            noiseDisturbance = true;
            flashRedAlert();
            triggerEmergencyDispatch("CATASTROPHIC MULTI-SYSTEM FAULT");
        });

        controls.add(beltShiftToggle);
        controls.add(chargerToggle);
        controls.add(overheatToggle);
        controls.add(noiseToggle);
        controls.add(sliderPanel);
        controls.add(sos);
        controls.add(severe);

        outer.add(header, BorderLayout.NORTH);
        outer.add(controls, BorderLayout.CENTER);
        return outer;
    }

    private void styleToggle(JToggleButton b) {
        b.setFont(new Font("Consolas", Font.BOLD, 11));
        b.setFocusPainted(false);
        b.setBackground(new Color(20, 24, 34));
        b.setForeground(CYAN);
        b.setBorder(new CompoundBorder(new LineBorder(CYAN, 1), new EmptyBorder(6, 10, 6, 10)));
    }

    private void styleButton(JButton b, Color color) {
        b.setFont(new Font("Consolas", Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBackground(new Color(20, 24, 34));
        b.setForeground(color);
        b.setBorder(new CompoundBorder(new LineBorder(color, 2), new EmptyBorder(8, 14, 8, 14)));
    }

    private void startClock() {
        javax.swing.Timer clock = new javax.swing.Timer(1000, e ->
                clockLabel.setText(new SimpleDateFormat("HH:mm:ss").format(new Date())));
        clock.start();
    }

    private void startTelemetryLoop() {
        javax.swing.Timer loop = new javax.swing.Timer(33, e -> {
            scope.setFlags(noiseDisturbance, !beltLocked, overheat);
            scope.pushSample();

            chargingVoltage = chargerFail ? 0.0 : 5.0 + Math.sin(System.currentTimeMillis()/500.0) * 0.15;

            Color batColor = batteryLevel < 5 ? CRIMSON : batteryLevel < 15 ? AMBER : EMERALD;
            batteryGauge.update(batteryLevel, batteryLevel + "%", batColor);

            Color voltColor = chargerFail ? CRIMSON : EMERALD;
            voltageGauge.update((chargingVoltage / 5.5) * 100, String.format("%.2fV", chargingVoltage), voltColor);

            beltLockLabel.setText("BELT: " + (beltLocked ? "LOCKED" : "DISPLACED"));
            beltLockLabel.setForeground(beltLocked ? EMERALD : CRIMSON);
            beltLockLabel.setBorder(new CompoundBorder(new LineBorder(beltLocked ? EMERALD : CRIMSON, 1, true), new EmptyBorder(4,10,4,10)));

            thermalLabel.setText("THERMAL: " + (overheat ? "HOTSPOT" : "NORMAL"));
            thermalLabel.setForeground(overheat ? CRIMSON : EMERALD);
            thermalLabel.setBorder(new CompoundBorder(new LineBorder(overheat ? CRIMSON : EMERALD, 1, true), new EmptyBorder(4,10,4,10)));

            connectionBadge.setText(chargerFail ? "INDUCTION LOSS" : "BLE LINKED");
            connectionBadge.setForeground(chargerFail ? AMBER : EMERALD);

            evaluateSystemStatus();
        });
        loop.start();
    }

    private void evaluateSystemStatus() {
        boolean critical = batteryLevel < 5 || !beltLocked || chargerFail;
        boolean warning  = batteryLevel < 15 || overheat || noiseDisturbance;

        if (critical) {
            systemStatusLabel.setText("SYSTEM: CRITICAL");
            systemStatusLabel.setForeground(CRIMSON);
        } else if (warning) {
            systemStatusLabel.setText("SYSTEM: WARNING");
            systemStatusLabel.setForeground(AMBER);
        } else {
            systemStatusLabel.setText("SYSTEM: NOMINAL");
            systemStatusLabel.setForeground(EMERALD);
        }
        systemStatusLabel.setBorder(new CompoundBorder(
                new LineBorder(systemStatusLabel.getForeground(), 1, true), new EmptyBorder(4,10,4,10)));

        if (batteryLevel < 5 && !batteryCriticalDispatched) {
            batteryCriticalDispatched = true;
            triggerEmergencyDispatch("Battery Critical (<5%)");
        }
        if (!beltLocked && !beltCriticalDispatched) {
            beltCriticalDispatched = true;
            triggerEmergencyDispatch("Severe Belt Dislocation");
        }
    }

    private void flashRedAlert() {
        Color original = getContentPane().getBackground();
        javax.swing.Timer flash = new javax.swing.Timer(150, null);
        int[] count = {0};
        flash.addActionListener(e -> {
            getContentPane().setBackground(count[0] % 2 == 0 ? CRIMSON.darker().darker() : original);
            count[0]++;
            if (count[0] > 5) {
                ((javax.swing.Timer) e.getSource()).stop();
                getContentPane().setBackground(original);
            }
        });
        flash.start();
    }

    private void triggerEmergencyDispatch(String faultType) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String gps = "GPS: 24.8607 N, 67.0011 E";

        String payload = "FAULT TYPE  : " + faultType + "\n"
                + "COORDINATES : " + gps + "\n"
                + "TIMESTAMP   : " + timestamp + "\n"
                + "BATTERY     : " + batteryLevel + "%";

        appendLog("FAULT DETECTED: " + faultType + " - INITIATING 3-NODE DISPATCH");

        JDialog dialog = buildDispatchDialog(payload);
        JTextArea dispatchLog = (JTextArea) dialog.getRootPane().getClientProperty("dispatchLog");

        String[] numbers = {contactPrimary, contactSecondary, contactDoctor};
        String[] labels  = {"PRIMARY FAMILY", "SECONDARY CAREGIVER", "CARDIOLOGIST / DOCTOR"};

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(400 + idx * 300L);
                } catch (InterruptedException ignored) {}
                String line = "[DISPATCHED] " + labels[idx] + " (" + numbers[idx] + ") - alert delivered.";
                SwingUtilities.invokeLater(() -> {
                    dispatchLog.append(line + "\n");
                    appendLog(line);
                });
            }, dispatchExecutor);
        }

        dialog.setVisible(true);
    }

    private JDialog buildDispatchDialog(String payload) {
        JDialog dialog = new JDialog(this, "EMERGENCY ALERT DISPATCHED", false);
        dialog.setSize(460, 380);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG);
        dialog.setLayout(new BorderLayout(10, 10));

        JLabel header = new JLabel("EMERGENCY ALERT PAYLOAD");
        header.setFont(new Font("Consolas", Font.BOLD, 15));
        header.setForeground(CRIMSON);
        header.setBorder(new EmptyBorder(12, 14, 0, 14));

        JTextArea payloadArea = new JTextArea(payload);
        payloadArea.setEditable(false);
        payloadArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        payloadArea.setBackground(new Color(20, 10, 12));
        payloadArea.setForeground(AMBER);
        payloadArea.setBorder(new CompoundBorder(new LineBorder(CRIMSON, 1), new EmptyBorder(8,8,8,8)));

        JTextArea dispatchLog = new JTextArea("Dispatching to 3 registered contacts concurrently...\n");
        dispatchLog.setEditable(false);
        dispatchLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        dispatchLog.setBackground(new Color(6, 8, 12));
        dispatchLog.setForeground(EMERALD);
        dialog.getRootPane().putClientProperty("dispatchLog", dispatchLog);

        JPanel center = new JPanel(new GridLayout(2, 1, 0, 8));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(0, 14, 0, 14));
        center.add(payloadArea);
        center.add(new JScrollPane(dispatchLog));

        JButton close = new JButton("ACKNOWLEDGE / CLOSE");
        styleButton(close, CYAN);
        close.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setBorder(new EmptyBorder(0,0,12,0));
        south.add(close);

        dialog.add(header, BorderLayout.NORTH);
        dialog.add(center, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        return dialog;
    }

    private void appendLog(String text) {
        String stamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            systemLog.append("[" + stamp + "] " + text + "\n");
            systemLog.setCaretPosition(systemLog.getDocument().getLength());
        });
    }

    static class OscilloscopePanel extends JPanel {
        private final double[] buffer;
        private int writeIndex = 0;
        private volatile boolean noisy = false;
        private volatile boolean displaced = false;
        private volatile boolean overheatFlag = false;
        private double phase = 0;

        OscilloscopePanel(int bufferSize) {
            buffer = new double[bufferSize];
            setBackground(WAVE_BG);
        }

        void setFlags(boolean noisy, boolean displaced, boolean overheat) {
            this.noisy = noisy;
            this.displaced = displaced;
            this.overheatFlag = overheat;
        }

        void pushSample() {
            phase += 0.25;
            double base = simulateCardiacInductionWaveform(phase);
            if (noisy) base += (Math.random() - 0.5) * 0.9;
            if (displaced) base += (Math.random() - 0.5) * 1.4;
            if (overheatFlag) base += Math.sin(phase * 3) * 0.15;
            buffer[writeIndex] = base;
            writeIndex = (writeIndex + 1) % buffer.length;
            repaint();
        }

        private double simulateCardiacInductionWaveform(double t) {
            double mod = t % (Math.PI * 2);
            double u = mod / (Math.PI * 2); // 0..1 fraction through one heartbeat cycle

            double p   = gaussianBump(u, 0.12, 0.020, 0.15);   // P wave
            double q   = gaussianBump(u, 0.27, 0.006, -0.15);  // Q dip
            double r   = gaussianBump(u, 0.30, 0.008, 1.6);    // R spike
            double s   = gaussianBump(u, 0.33, 0.008, -0.35);  // S dip
            double tWv = gaussianBump(u, 0.55, 0.045, 0.30);   // T wave

            return p + q + r + s + tWv;
        }

        private double gaussianBump(double x, double center, double width, double height) {
            return height * Math.exp(-Math.pow((x - center) / width, 2));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            g2.setColor(GRID);
            for (int gx = 0; gx < w; gx += 40) g2.drawLine(gx, 0, gx, h);
            for (int gy = 0; gy < h; gy += 40) g2.drawLine(0, gy, w, gy);

            Color waveColor = displaced ? CRIMSON : (noisy ? AMBER : CYAN);
            g2.setColor(waveColor);
            g2.setStroke(new BasicStroke(2f));

            int n = buffer.length;
            int midY = h / 2;
            double xScale = (double) w / n;
            Path2D path = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                int idx = (writeIndex + i) % n;
                double val = buffer[idx];
                float px = (float) (i * xScale);
                float py = (float) (midY - val * (h / 6.0));
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            g2.draw(path);
            g2.dispose();
        }
    }

    static class RadialGauge extends JPanel {
        private double percent = 0;
        private String label;
        private String valueText = "--";
        private Color color = CYAN;

        RadialGauge(String label) {
            this.label = label;
            setOpaque(false);
            setPreferredSize(new Dimension(150, 170));
        }

        void update(double percent, String valueText, Color color) {
            this.percent = Math.max(0, Math.min(100, percent));
            this.valueText = valueText;
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int size = Math.min(w, h - 26) - 16;
            int x = (w - size) / 2, y = 6;

            g2.setStroke(new BasicStroke(9, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 25));
            g2.drawArc(x, y, size, size, -30, -240);

            int sweep = (int) Math.round(-240 * (percent / 100.0));
            g2.setColor(color);
            g2.drawArc(x, y, size, size, -30, sweep);

            g2.setFont(new Font("Consolas", Font.BOLD, 20));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(valueText);
            g2.drawString(valueText, x + size / 2 - tw / 2, y + size / 2 + 8);

            g2.setColor(TEXT_DIM);
            g2.setFont(new Font("Consolas", Font.PLAIN, 12));
            fm = g2.getFontMetrics();
            tw = fm.stringWidth(label);
            g2.drawString(label, x + size / 2 - tw / 2, y + size + 16);

            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new NexusHeartGridHUD().setVisible(true));
    }
}