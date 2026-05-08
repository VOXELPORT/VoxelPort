package org.localm;

import org.localm.model.ModrinthProject;
import org.localm.model.RamPreset;
import org.localm.model.ServerVersion;
import org.localm.service.BackupService;
import org.localm.service.ConfigService;
import org.localm.service.ModrinthService;
import org.localm.service.ServerProcessManager;
import org.localm.service.ServerStore;
import org.localm.service.TunnelService;
import org.localm.service.VersionService;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.localm.ui.SparklineGraph;
import org.localm.util.Logger;

public class LocalMJava extends JFrame {
    private static final Color CONSOLE_BG = new Color(12, 17, 23);
    private static final Color CONSOLE_FG = new Color(229, 236, 246);
    private static final Color CONSOLE_MUTED = new Color(150, 163, 184);

    private final ServerStore store;
    private final VersionService versionService = new VersionService();
    private final ServerProcessManager processManager;
    private final TunnelService tunnelService = new TunnelService();
    private final BackupService backupService = new BackupService();
    private final ConfigService configService = new ConfigService();
    private final ModrinthService modrinthService = new ModrinthService();
    private final org.localm.service.UpdateService updateService = new org.localm.service.UpdateService();

    private final DefaultListModel<String> serverModel = new DefaultListModel<>();
    private final DefaultListModel<String> playerModel = new DefaultListModel<>();
    private final JList<String> serverList = new JList<>(serverModel);
    private final JComboBox<ServerVersion> versionBox = new JComboBox<>();
    private final JTextPane console = new JTextPane();
    private final JTextField consoleInput = new JTextField();
    private final JTextField serverName = new JTextField("My Server");
    private final JTextField serverPort = new JTextField("25565");
    private final JSlider ramSlider = new JSlider(JSlider.HORIZONTAL, 1024, getSystemRamMb(), 4096);
    private final JLabel ramLabel = new JLabel("4096 MB");
    private final JCheckBox autoBackup = new JCheckBox("Auto-backup on stop");
    private final JLabel status = new JLabel("Ready");
    private final JTextField roomCode = new JTextField();
    private final JTextField joinCode = new JTextField();
    private final JTextField joinAddress = new JTextField("localhost:25565");
    private final JLabel joinState = new JLabel("Disconnected");

    private final Map<String, DefaultStyledDocument> consoleDocs = new ConcurrentHashMap<>();
    private final Map<String, SparklineGraph> ramGraphs = new ConcurrentHashMap<>();
    private final CardLayout mainLayout = new CardLayout();
    private final JPanel mainContainer = new JPanel(mainLayout);
    private final Map<String, JButton> sidebarButtons = new HashMap<>();

    private final JTextField webhookField = new JTextField(30);
    private final JPanel graphContainer = new JPanel(new BorderLayout());
    private final JCheckBox crackedToggle = new JCheckBox("Cracked Server (Disable Online Mode)");
    private final JComboBox<String> profileBox = new JComboBox<>(new String[]{"Stable (G1GC)", "Aggressive (Parallel)", "Custom"});
    private final JSpinner backupInterval = new JSpinner(new SpinnerNumberModel(0, 0, 168, 1));

    public static void main(String[] args) {
        if (args.length > 0) {
            runCliMain(args);
            return;
        }

        // Apply FlatLaf dark theme before any Swing component is created
        try { FlatDarkLaf.setup(); } catch (Exception ignored) {}
        UIManager.put("Button.arc", 6);
        UIManager.put("Component.arc", 6);
        UIManager.put("TabbedPane.tabHeight", 32);

        LocalMJava app;
        try {
            app = new LocalMJava();
            org.localm.util.Logger.init(app.store.getDataDir());
            org.localm.util.Logger.info("VoxelPort started");
        } catch (IOException e) {
            System.err.println("Failed to initialize store: " + e.getMessage());
            return;
        }

        SwingUtilities.invokeLater(() -> app.setVisible(true));
    }

    private static void runCliMain(String[] args) {
        try {
            ServerStore cliStore = new ServerStore();
            org.localm.util.Logger.init(cliStore.getDataDir());
            ServerProcessManager cliProcessManager = new ServerProcessManager(cliStore.getDataDir());
            new CliRunner(cliStore, cliProcessManager).run(args);
        } catch (IOException e) {
            System.err.println("Failed to initialize store: " + e.getMessage());
        }
    }

    private static final class CliRunner {
        private final ServerStore store;
        private final ServerProcessManager processManager;
        private org.localm.service.HeadlessWebServer webServer;

        private CliRunner(ServerStore store, ServerProcessManager processManager) {
            this.store = store;
            this.processManager = processManager;
        }

        private void run(String[] args) {
            String cmd = args[0].toLowerCase(Locale.ROOT);
            try {
                switch (cmd) {
                    case "--list" -> {
                        System.out.println("Installed Servers:");
                        store.stringPropertyNames().stream()
                                .filter(k -> k.endsWith(".dir"))
                                .map(k -> k.substring(0, k.length() - 4))
                                .sorted()
                                .forEach(name -> {
                                    String port = store.getProperty(name + ".port", "25565");
                                    String version = store.getProperty(name + ".version", "unknown");
                                    System.out.printf("- %s (%s, Port: %s)\n", name, version, port);
                                });
                    }
                    case "--start" -> {
                        if (args.length < 2) throw new IllegalArgumentException("Usage: --start <server-name>");
                        String name = args[1];
                        if (!store.containsKey(name + ".dir")) throw new IllegalArgumentException("Server not found: " + name);
                        System.out.println("Starting " + name + "...");
                        startServerCli(name, false);
                    }
                    case "--headless" -> {
                        if (args.length < 2) throw new IllegalArgumentException("Usage: --headless <server-name>");
                        String name = args[1];
                        if (!store.containsKey(name + ".dir")) throw new IllegalArgumentException("Server not found: " + name);
                        System.out.println("Starting " + name + " in Headless Mode...");
                        webServer = new org.localm.service.HeadlessWebServer(processManager);
                        webServer.start(8080);
                        System.out.println("Web console available at http://localhost:8080");
                        startServerCli(name, true);
                    }
                    case "--stop" -> {
                        if (args.length < 2) throw new IllegalArgumentException("Usage: --stop <server-name>");
                        String name = args[1];
                        try (Socket s = new Socket("127.0.0.1", 42851);
                             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                            out.println("STOP " + name);
                            String response = in.readLine();
                            if ("OK".equals(response)) {
                                System.out.println("Stop command sent to " + name + " via GUI.");
                            } else {
                                System.out.println("Server " + name + " is not running or GUI rejected stop.");
                            }
                        } catch (IOException ex) {
                            System.out.println("Could not connect to GUI. Is the VoxelPort GUI running?");
                        }
                    }
                    default -> System.out.println("Unknown command. Available: --list, --start <name>, --headless <name>");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        private void startServerCli(String name, boolean headless) throws IOException {
            Path dir = store.getServerDir(name);
            int port = Integer.parseInt(store.getProperty(name + ".port", "25565"));
            Path propsFile = dir.resolve("server.properties");
            if (Files.exists(propsFile)) {
                String content = Files.readString(propsFile);
                content = content.replaceAll("server-port=\\d+", "server-port=" + port);
                Files.writeString(propsFile, content);
            }

            String mcVersion = store.getProperty(name + ".version", "1.21");
            int ram = Integer.parseInt(store.getProperty(name + ".ram", "4096"));
            String webhook = store.getProperty(name + ".webhookUrl", null);

            processManager.startServer(name, dir, mcVersion, ram, null, webhook, (n, text) -> {
                if (headless) {
                    webServer.addLog(n, text);
                }
                System.out.println("[" + n + "] " + text);
            }, () -> {
                System.out.println("Server " + name + " stopped.");
                if (headless) {
                    System.exit(0);
                }
            });

            while (processManager.isAlive(name)) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    public LocalMJava() throws IOException {
        super("VoxelPort  v1.1.0");
        this.store = new ServerStore();
        this.processManager = new ServerProcessManager(store.getDataDir());

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1000, 700));
        setLocationRelativeTo(null);

        roomCode.setEditable(false);
        joinAddress.setEditable(false);

        // RAM slider - tick labels
        int sysRam = getSystemRamMb();
        ramSlider.setMajorTickSpacing(Math.max(1024, sysRam / 8));
        ramSlider.setPaintTicks(true);
        ramSlider.setPaintLabels(true);
        ramSlider.setSnapToTicks(false);
        java.util.Hashtable<Integer, JLabel> lblMap = new java.util.Hashtable<>();
        for (int mb = 1024; mb <= sysRam; mb += Math.max(1024, sysRam / 8)) {
            lblMap.put(mb, new JLabel(mb >= 1024 ? (mb / 1024) + "G" : mb + "M"));
        }
        ramSlider.setLabelTable(lblMap);
        ramSlider.addChangeListener(e -> ramLabel.setText(ramSlider.getValue() + " MB"));

        setContentPane(buildUi());
        refreshServerList();
        loadVersions();
        initSystemTray();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                processManager.stopAll();
                stopRoom();
                stopJoinProxy();
            }
        });
        
        startCliListener();

        // Timer to update graphs
        new javax.swing.Timer(2000, e -> {
            for (int i = 0; i < serverModel.size(); i++) {
                String name = serverModel.getElementAt(i);
                if (processManager.isAlive(name)) {
                    int ram = processManager.getRamUsageMb(name);
                    ramGraphs.computeIfAbsent(name, k -> new SparklineGraph("RAM", new Color(100, 200, 255))).addValue(ram);
                }
            }
        }).start();

        // Timer for Scheduled Backups (checks every minute)
        new javax.swing.Timer(60000, e -> {
            for (int i = 0; i < serverModel.size(); i++) {
                String name = serverModel.getElementAt(i);
                if (processManager.isAlive(name)) {
                    int interval = Integer.parseInt(store.getProperty(name + ".backupInterval", "0"));
                    if (interval > 0) {
                        long lastBackup = Long.parseLong(store.getProperty(name + ".lastBackupTime", "0"));
                        if (System.currentTimeMillis() - lastBackup > (long) interval * 3600000) {
                            doBackup(name, false);
                            store.setProperty(name + ".lastBackupTime", String.valueOf(System.currentTimeMillis()));
                            try { store.save(); } catch (IOException ignored) {}
                        }
                    }
                }
            }
        }).start();
    }

    private void initSystemTray() {
        if (!SystemTray.isSupported()) return;

        try {
            SystemTray tray = SystemTray.getSystemTray();
            // Use a simple colored square as placeholder icon if no icon file exists
            BufferedImage iconImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = iconImg.createGraphics();
            g.setColor(new Color(100, 200, 255));
            g.fillRoundRect(2, 2, 12, 12, 4, 4);
            g.dispose();

            TrayIcon trayIcon = new TrayIcon(iconImg, "VoxelPort");
            trayIcon.setImageAutoSize(true);

            PopupMenu menu = new PopupMenu();
            MenuItem showItem = new MenuItem("Show VoxelPort");
            showItem.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
            });
            menu.add(showItem);

            MenuItem stopAllItem = new MenuItem("Stop All Servers");
            stopAllItem.addActionListener(e -> processManager.stopAll());
            menu.add(stopAllItem);

            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                processManager.stopAll();
                System.exit(0);
            });
            menu.add(exitItem);

            trayIcon.setPopupMenu(menu);
            trayIcon.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
            });

            tray.add(trayIcon);
        } catch (Exception e) {
            Logger.error("Failed to initialize system tray", e);
        }
    }

    private void startCliListener() {
        Thread t = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(42851, 50, InetAddress.getByName("127.0.0.1"))) {
                while (!Thread.currentThread().isInterrupted()) {
                    try (Socket s = server.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                         PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
                        
                        String line = in.readLine();
                        if (line != null && line.startsWith("STOP ")) {
                            String name = line.substring(5).trim();
                            if (processManager.isAlive(name)) {
                                stopServerByName(name);
                                out.println("OK");
                            } else {
                                out.println("NOT_RUNNING");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                // Ignore port binding issues
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private JComponent buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 0));

        // -- Header ------------------------------------------------------------
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(new EmptyBorder(10, 16, 10, 16));
        header.setBackground(new Color(30, 32, 40));

        JLabel title = new JLabel("VoxelPort");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(new Color(100, 200, 255));
        header.add(title, BorderLayout.WEST);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightHeader.setOpaque(false);
        
        JButton sponsorHeaderBtn = new JButton("Sponsor");
        sponsorHeaderBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sponsorHeaderBtn.setForeground(new Color(216, 74, 123));
        sponsorHeaderBtn.setFocusPainted(false);
        sponsorHeaderBtn.setContentAreaFilled(false);
        sponsorHeaderBtn.setBorderPainted(false);
        sponsorHeaderBtn.setFont(sponsorHeaderBtn.getFont().deriveFont(Font.BOLD, 12f));
        sponsorHeaderBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/sponsors/trazhub"));
            } catch (Exception ex) {
                showError(ex);
            }
        });
        rightHeader.add(sponsorHeaderBtn);

        JLabel badge = new JLabel("v1.1.0");
        badge.setFont(badge.getFont().deriveFont(Font.PLAIN, 11f));
        badge.setForeground(new Color(120, 120, 140));
        badge.setBorder(new EmptyBorder(4, 0, 0, 0));
        rightHeader.add(badge);

        header.add(rightHeader, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // -- Tabs --------------------------------------------------------------
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Host",       hostPanel());
        tabs.addTab("Join Room",  joinPanel());
        tabs.addTab("Settings",   settingsPanel());
        root.add(tabs, BorderLayout.CENTER);

        // -- Status bar --------------------------------------------------------
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(6, 12, 7, 12));
        statusBar.setBackground(new Color(18, 24, 32));
        status.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        status.setForeground(new Color(195, 216, 245));
        statusBar.add(status, BorderLayout.WEST);
        root.add(statusBar, BorderLayout.SOUTH);

        // Stats refresh timer
        new javax.swing.Timer(3000, e -> serverList.repaint()).start();

        return root;
    }

    private JComponent hostPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));

        // -- LEFT sidebar ------------------------------------------------------
        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.setPreferredSize(new Dimension(215, 0));

        JLabel serverHeader = new JLabel("Servers (0)");
        serverHeader.setFont(serverHeader.getFont().deriveFont(Font.BOLD, 11f));
        serverHeader.setForeground(new Color(120, 140, 180));
        serverHeader.setBorder(new EmptyBorder(0, 4, 2, 0));
        left.add(serverHeader, BorderLayout.NORTH);
        
        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateConfigUi();
        });
        serverList.setCellRenderer(new StatusRenderer());
        serverList.setFixedCellHeight(36);

        // Empty-state placeholder
        JPanel listWrapper = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (serverModel.isEmpty()) {
                    g.setFont(g.getFont().deriveFont(Font.ITALIC, 11f));
                    g.setColor(new Color(120, 120, 140));
                    String msg = "No servers - click Install";
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
                }
            }
        };
        listWrapper.add(new JScrollPane(serverList), BorderLayout.CENTER);
        left.add(listWrapper, BorderLayout.CENTER);

        // Keep count label updated
        serverModel.addListDataListener(new javax.swing.event.ListDataListener() {
            void sync() { serverHeader.setText("Servers (" + serverModel.size() + ")"); }
            public void intervalAdded(javax.swing.event.ListDataEvent e)   { sync(); }
            public void intervalRemoved(javax.swing.event.ListDataEvent e) { sync(); }
            public void contentsChanged(javax.swing.event.ListDataEvent e) { sync(); }
        });

        // Right-click context menu
        JPopupMenu listCtx = new JPopupMenu();
        JMenuItem ctxStart  = new JMenuItem("Start");
        JMenuItem ctxStop   = new JMenuItem("Stop");
        JMenuItem ctxFolder = new JMenuItem("Open Folder");
        JMenuItem ctxMods   = new JMenuItem("Plugins & Mods");
        JMenuItem ctxDelete = new JMenuItem("Delete");
        ctxStart.addActionListener(e  -> CompletableFuture.runAsync(() -> { try { startServer(); } catch(Exception x){ SwingUtilities.invokeLater(()->showError(x)); } }));
        ctxStop.addActionListener(e   -> CompletableFuture.runAsync(() -> { try { stopServer();  } catch(Exception x){ SwingUtilities.invokeLater(()->showError(x)); } }));
        ctxFolder.addActionListener(e -> CompletableFuture.runAsync(this::openServerFolder));
        ctxMods.addActionListener(e   -> openModManager());
        ctxDelete.addActionListener(e -> runAsyncUi(this::deleteServer));
        listCtx.add(ctxStart); listCtx.add(ctxStop); listCtx.addSeparator();
        listCtx.add(ctxFolder); listCtx.add(ctxMods); listCtx.addSeparator();
        listCtx.add(ctxDelete);
        serverList.setComponentPopupMenu(listCtx);

        JButton create = colorBtn("+ Install New Server", new Color(40, 110, 180), Color.WHITE);
        create.addActionListener(e -> runAsyncUi(() -> installServer(crackedToggle.isSelected())));
        left.add(create, BorderLayout.SOUTH);
        panel.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(10, 10));

        JPanel config = new JPanel();
        config.setLayout(new BoxLayout(config, BoxLayout.Y_AXIS));
        config.setBorder(BorderFactory.createTitledBorder("Configuration"));
        
        // Add cracked toggle inside config so it's accessible
        crackedToggle.setFont(crackedToggle.getFont().deriveFont(Font.BOLD));
        crackedToggle.setToolTipText("Check before installing a new server if you want it cracked.");
        config.add(formRow("Install Setting", crackedToggle));

        config.add(formRow("Name", serverName));
        config.add(formRow("Version", versionBox));
        
        JPanel ramPanel = new JPanel(new BorderLayout(5, 5));
        ramPanel.add(ramSlider, BorderLayout.CENTER);
        ramPanel.add(ramLabel, BorderLayout.EAST);
        config.add(formRow("RAM Allocation", ramPanel));
        
        config.add(formRow("Server Port", serverPort));
        
        // Advanced Configuration
        JTabbedPane advancedTabs = new JTabbedPane();
        
        JPanel advOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        advOptions.add(autoBackup);
        advOptions.add(new JLabel("Java Profile:"));
        advOptions.add(profileBox);
        advOptions.add(new JLabel("Auto-Backup (hours, 0=off):"));
        advOptions.add(backupInterval);
        advancedTabs.addTab("Settings", advOptions);

        JPanel webhookTab = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        webhookTab.add(new JLabel("Discord Webhook URL:"));
        webhookTab.add(webhookField);
        advancedTabs.addTab("Webhooks", webhookTab);

        config.add(advancedTabs);
        
        // Primary: Start / Stop
        JPanel primary = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton startBtn = colorBtn("Start Server", new Color(34, 140, 60), Color.WHITE);
        JButton stopBtn  = colorBtn("Stop Server",  new Color(180, 40, 40), Color.WHITE);
        startBtn.addActionListener(e -> CompletableFuture.runAsync(() -> { try { startServer(); } catch(Exception x){ SwingUtilities.invokeLater(()->showError(x)); } }));
        stopBtn.addActionListener(e  -> CompletableFuture.runAsync(() -> { try { stopServer();  } catch(Exception x){ SwingUtilities.invokeLater(()->showError(x)); } }));
        primary.add(startBtn); primary.add(stopBtn);
        
        // Secondary: tools
        JPanel secondary = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        secondary.add(button("Updates",    this::checkUpdates));
        secondary.add(button("Folder",     this::openServerFolder));

        JButton modBtn = new JButton("Plugins & Mods");
        modBtn.addActionListener(e -> openModManager());
        secondary.add(modBtn);

        JButton propBtn = new JButton("Properties");
        propBtn.addActionListener(e -> openPropertiesEditor());
        secondary.add(propBtn);

        JButton backupBtn = new JButton("Backup...");
        backupBtn.addActionListener(e -> showBackupMenu(backupBtn));
        secondary.add(backupBtn);

        JButton delBtn = colorBtn("Delete", new Color(120, 30, 30), Color.WHITE);
        delBtn.addActionListener(e -> runAsyncUi(this::deleteServer));
        secondary.add(delBtn);

        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.add(primary); actions.add(secondary);
        config.add(actions);
        right.add(config, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(5, 5));
        
        JPanel room = new JPanel();
        room.setLayout(new BoxLayout(room, BoxLayout.Y_AXIS));
        room.setBorder(BorderFactory.createTitledBorder("Public Room"));
        
        JPanel roomButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        roomButtons.add(button("Start Room", this::startRoom));
        roomButtons.add(button("Stop Room", this::stopRoom));
        roomButtons.add(button("Copy Code", () -> copy(roomCode.getText())));
        room.add(roomButtons);
        
        roomCode.setPreferredSize(new Dimension(100, 25));
        room.add(roomCode);
        center.add(room, BorderLayout.NORTH);

        JPanel consolePanel = new JPanel(new BorderLayout(5, 5));
        consolePanel.setBorder(BorderFactory.createTitledBorder("Console"));
        
        graphContainer.setPreferredSize(new Dimension(0, 60));
        consolePanel.add(graphContainer, BorderLayout.NORTH);

        console.setEditable(false);
        console.setBackground(CONSOLE_BG);
        console.setForeground(CONSOLE_FG);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        console.setCaretColor(new Color(125, 211, 252));
        console.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane consoleScroll = new JScrollPane(console);
        consoleScroll.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        consoleScroll.getViewport().setBackground(CONSOLE_BG);
        consolePanel.add(consoleScroll, BorderLayout.CENTER);

        // Console Toolbar (Search & Filter)
        JPanel consoleToolbar = new JPanel(new BorderLayout(5, 0));
        consoleInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        consoleInput.setBackground(new Color(15, 23, 42));
        consoleInput.setForeground(CONSOLE_FG);
        consoleInput.setCaretColor(new Color(125, 211, 252));
        consoleInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                new EmptyBorder(5, 8, 5, 8)));
        consoleInput.setToolTipText("Type a command and press Enter");
        consoleInput.addActionListener(e -> {
            String cmd = consoleInput.getText().trim();
            if (!cmd.isEmpty()) {
                try {
                    processManager.sendCommand(selectedServer(), cmd);
                    consoleInput.setText("");
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        });
        
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JTextField searchField = new JTextField(10);
        searchField.setToolTipText("Search console...");
        JCheckBox filterToggle = new JCheckBox("Filter Noise");
        searchField.addActionListener(e -> highlightSearch(searchField.getText()));
        filterToggle.addActionListener(e -> {
            String name = serverList.getSelectedValue();
            if (name != null) {
                applyConsoleFilter(name, filterToggle.isSelected());
            }
        });
        filterPanel.add(new JLabel("Search:"));
        filterPanel.add(searchField);
        filterPanel.add(filterToggle);
        
        consoleToolbar.add(consoleInput, BorderLayout.CENTER);
        consoleToolbar.add(filterPanel, BorderLayout.EAST);
        consolePanel.add(consoleToolbar, BorderLayout.SOUTH);
        
        // Player List Panel
        JList<String> playerList = new JList<>(playerModel);
        playerList.setPreferredSize(new Dimension(150, 0));
        playerList.setBorder(BorderFactory.createTitledBorder("Online Players"));
        JPopupMenu playerCtx = new JPopupMenu();
        JMenuItem kick = new JMenuItem("Kick");
        kick.addActionListener(e -> {
            String p = playerList.getSelectedValue();
            if (p != null) processManager.sendCommand(selectedServer(), "kick " + p);
        });
        playerCtx.add(kick);
        playerList.setComponentPopupMenu(playerCtx);
        
        center.add(consolePanel, BorderLayout.CENTER);
        center.add(new JScrollPane(playerList), BorderLayout.EAST);
        
        right.add(center, BorderLayout.CENTER);
        panel.add(right, BorderLayout.CENTER);
        return panel;
    }

    private void showBackupMenu(JButton source) {
        String name = selectedServer();
        JPopupMenu menu = new JPopupMenu();
        JMenuItem world = new JMenuItem("World Only");
        world.addActionListener(e -> CompletableFuture.runAsync(() -> doBackup(name, false)));
        JMenuItem full = new JMenuItem("Full Server");
        full.addActionListener(e -> CompletableFuture.runAsync(() -> doBackup(name, true)));
        menu.add(world);
        menu.add(full);
        menu.show(source, 0, source.getHeight());
    }

    private void doBackup(String name, boolean full) {
        try {
            setStatus("Creating " + (full ? "full" : "world") + " backup...");
            Path out = backupService.backup(name, store.getServerDir(name), full);
            setStatus("Backup created: " + out.getFileName());
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> showError(e));
        }
    }

    private void openPropertiesEditor() {
        try {
            String name = selectedServer();
            Path dir = store.getServerDir(name);
            Properties props = configService.loadProperties(dir);
            
            // Convert properties to a 2D array for the table
            List<String[]> dataList = new ArrayList<>();
            props.forEach((k, v) -> dataList.add(new String[]{k.toString(), v.toString()}));
            dataList.sort(Comparator.comparing(a -> a[0]));
            
            String[][] data = dataList.toArray(new String[0][]);
            String[] columnNames = {"Property", "Value"};
            
            JTable table = new JTable(data, columnNames);
            table.setFillsViewportHeight(true);
            
            int result = JOptionPane.showConfirmDialog(this, new JScrollPane(table), "Server Properties: " + name, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                // Read values back from the table
                Properties newProps = new Properties();
                for (int i = 0; i < table.getRowCount(); i++) {
                    String key = table.getValueAt(i, 0).toString();
                    String value = table.getValueAt(i, 1).toString();
                    newProps.setProperty(key, value);
                }
                configService.saveProperties(dir, newProps);
                setStatus("Saved properties for " + name);
            }
        } catch (Exception e) {
            showError(e);
        }
    }

    // -------------------------------------------------------------------------
    //  Mod & Plugin Manager
    // -------------------------------------------------------------------------

    private void openModManager() {
        String name;
        try {
            name = selectedServer();
        } catch (IllegalStateException e) {
            showError(e);
            return;
        }

        Path serverDir  = store.getServerDir(name);
        String mcVersion = store.getProperty(name + ".version", "1.20.1");
        org.localm.service.ModrinthService.LoaderType loader =
                org.localm.service.ModrinthService.detectLoader(serverDir);

        // -- Dialog shell ---------------------------------------------------
        JDialog dialog = new JDialog(this,
                "Plugins & Mods  -  " + name + "  [" + loader.displayName() + "]", true);
        dialog.setSize(820, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(8, 8));

        // -- Header bar ----------------------------------------------------
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setBorder(new EmptyBorder(10, 12, 6, 12));

        JLabel loaderBadge = new JLabel("Loader: " + loader.displayName() + "  |  MC " + mcVersion);
        loaderBadge.setFont(loaderBadge.getFont().deriveFont(Font.BOLD));
        loaderBadge.setForeground(new Color(60, 120, 200));
        header.add(loaderBadge, BorderLayout.WEST);

        JTextField searchField = new JTextField();
        searchField.setToolTipText("Search Modrinth...");
        JButton searchBtn = new JButton("Search");

        JPanel searchBar = new JPanel(new BorderLayout(5, 0));
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchBtn, BorderLayout.EAST);
        header.add(searchBar, BorderLayout.CENTER);

        dialog.add(header, BorderLayout.NORTH);

        // -- Split pane: left = search results, right = installed -----------
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(480);
        split.setResizeWeight(0.6);

        // LEFT - Search results --------------------------------------------
        DefaultListModel<ModrinthProject> resultModel = new DefaultListModel<>();
        JList<ModrinthProject> resultList = new JList<>(resultModel);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new ModrinthCellRenderer());
        resultList.setFixedCellHeight(60);

        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Modrinth Results"));
        leftPanel.add(new JScrollPane(resultList), BorderLayout.CENTER);

        // Description pane below results
        JTextArea descArea = new JTextArea(3, 40);
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setFont(descArea.getFont().deriveFont(11f));
        descArea.setBackground(new Color(250, 250, 250));
        descArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        leftPanel.add(new JScrollPane(descArea), BorderLayout.SOUTH);

        resultList.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                ModrinthProject p = resultList.getSelectedValue();
                if (p != null) {
                    descArea.setText(p.title() + " by " + p.author()
                            + "\n" + formatDownloads(p.downloads()) + " downloads\n\n"
                            + p.description());
                }
            }
        });

        split.setLeftComponent(leftPanel);

        // RIGHT - Installed jars -------------------------------------------
        DefaultListModel<Path> installedModel = new DefaultListModel<>();
        JList<Path> installedList = new JList<>(installedModel);
        installedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installedList.setCellRenderer(new JarFileCellRenderer());

        JPanel rightPanel = new JPanel(new BorderLayout(4, 4));
        rightPanel.setBorder(BorderFactory.createTitledBorder(
                "Installed " + capitalize(loader.folder)));
        rightPanel.add(new JScrollPane(installedList), BorderLayout.CENTER);

        JButton deleteBtn  = new JButton("Delete");
        JButton openDirBtn = new JButton("Open Folder");
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setToolTipText("Refresh installed list");

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        rightBtns.add(deleteBtn);
        rightBtns.add(openDirBtn);
        rightBtns.add(refreshBtn);
        rightPanel.add(rightBtns, BorderLayout.SOUTH);
        split.setRightComponent(rightPanel);

        dialog.add(split, BorderLayout.CENTER);

        // -- Bottom status / progress bar -----------------------------------
        JProgressBar progress = new JProgressBar();
        progress.setStringPainted(true);
        progress.setString("Ready");
        progress.setVisible(false);

        JButton installBtn = new JButton("Install Selected");
        installBtn.setFont(installBtn.getFont().deriveFont(Font.BOLD));
        installBtn.setBackground(new Color(50, 150, 80));
        installBtn.setForeground(Color.WHITE);
        installBtn.setOpaque(true);

        JPanel bottom = new JPanel(new BorderLayout(8, 4));
        bottom.setBorder(new EmptyBorder(4, 10, 10, 10));
        bottom.add(progress,    BorderLayout.CENTER);
        bottom.add(installBtn,  BorderLayout.EAST);
        dialog.add(bottom, BorderLayout.SOUTH);

        // -- Helpers -------------------------------------------------------
        Runnable reloadInstalled = () -> {
            List<Path> jars = modrinthService.listInstalled(serverDir, loader);
            SwingUtilities.invokeLater(() -> {
                installedModel.clear();
                jars.forEach(installedModel::addElement);
            });
        };
        reloadInstalled.run();

        // -- Search action -------------------------------------------------
        Runnable doSearch = () -> {
            String query = searchField.getText().trim();
            if (query.isEmpty()) return;
            progress.setVisible(true);
            progress.setIndeterminate(true);
            progress.setString("Searching Modrinth...");
            resultModel.clear();
            descArea.setText("");
            CompletableFuture.runAsync(() -> {
                try {
                    List<ModrinthProject> results =
                            modrinthService.search(query, loader, mcVersion);
                    SwingUtilities.invokeLater(() -> {
                        resultModel.clear();
                        results.forEach(resultModel::addElement);
                        progress.setIndeterminate(false);
                        progress.setString("Found " + results.size() + " results");
                        if (!results.isEmpty()) resultList.setSelectedIndex(0);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        progress.setVisible(false);
                        showError(ex);
                    });
                }
            });
        };

        searchField.addActionListener(e -> doSearch.run());
        searchBtn.addActionListener(e -> doSearch.run());

        // -- Install action ------------------------------------------------
        installBtn.addActionListener(e -> {
            ModrinthProject selected = resultList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog,
                        "Select a project from the search results first.",
                        "No selection", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            installBtn.setEnabled(false);
            progress.setVisible(true);
            progress.setIndeterminate(true);
            progress.setString("Resolving download for " + selected.title() + "...");

            CompletableFuture.runAsync(() -> {
                try {
                    org.localm.service.ModrinthService.VersionResult res = modrinthService.getLatestVersion(
                            selected.id(), mcVersion, loader);
                    if (res == null) {
                        throw new Exception("No compatible .jar found for "
                                + selected.title() + " on " + mcVersion
                                + " [" + loader.displayName() + "]");
                    }

                    // Check dependencies
                    List<String> deps = modrinthService.getDependencies(res.id());
                    if (!deps.isEmpty()) {
                        StringBuilder depList = new StringBuilder();
                        for (String did : deps) {
                            depList.append("- ").append(modrinthService.getProjectTitle(did)).append("\n");
                        }
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialog,
                                "This project requires the following dependencies. Please install them as well:\n" + depList,
                                "Dependencies Required", JOptionPane.WARNING_MESSAGE));
                    }

                    Path targetDir = serverDir.resolve(loader.folder);
                    Files.createDirectories(targetDir);
                    Path targetFile = targetDir.resolve(selected.slug() + ".jar");

                    SwingUtilities.invokeLater(() ->
                            progress.setString("Downloading " + selected.title() + "..."));

                    versionService.download(res.url(), targetFile);

                    SwingUtilities.invokeLater(() -> {
                        progress.setIndeterminate(false);
                        progress.setString("Installed " + selected.title() + " OK");
                        installBtn.setEnabled(true);
                        setStatus("Installed " + selected.title());
                        reloadInstalled.run();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        progress.setVisible(false);
                        installBtn.setEnabled(true);
                        showError(ex);
                    });
                }
            });
        });

        // -- Delete installed jar ------------------------------------------
        deleteBtn.addActionListener(e -> {
            Path jar = installedList.getSelectedValue();
            if (jar == null) return;
            int ok = JOptionPane.showConfirmDialog(dialog,
                    "Delete " + jar.getFileName() + "?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
            try {
                Files.deleteIfExists(jar);
                reloadInstalled.run();
                setStatus("Deleted " + jar.getFileName());
            } catch (IOException ex) {
                showError(ex);
            }
        });

        // -- Open installed folder -----------------------------------------
        openDirBtn.addActionListener(e -> {
            Path dir = serverDir.resolve(loader.folder);
            try { Files.createDirectories(dir); } catch (IOException ignored) {}
            CompletableFuture.runAsync(() -> open(dir));
        });

        refreshBtn.addActionListener(e -> reloadInstalled.run());

        dialog.setVisible(true);
    }

    /** Format large download counts nicely (e.g. 1.2M, 340K) */
    private String formatDownloads(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Cell renderer for Modrinth search results */
    private class ModrinthCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof ModrinthProject p) {
                label.setText("<html><b>" + escHtml(p.title())
                        + "</b>  <font color='#888888'>by " + escHtml(p.author()) + "</font>"
                        + "<br/><font color='#555555' size='-1'>" + escHtml(p.description())
                        + "</font></html>");
                label.setToolTipText(formatDownloads(p.downloads()) + " downloads");
                label.setBorder(new EmptyBorder(4, 8, 4, 8));
            }
            return label;
        }
    }

    /** Cell renderer for installed .jar files */
    private class JarFileCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof Path p) {
                long size = 0;
                try { size = Files.size(p); } catch (IOException ignored) {}
                label.setText("<html>" + escHtml(p.getFileName().toString())
                        + " <font color='#888888'>" + formatBytes(size) + "</font></html>");
                label.setBorder(new EmptyBorder(2, 8, 2, 8));
            }
            return label;
        }
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JComponent joinPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setPreferredSize(new Dimension(650, 360));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        JLabel title = new JLabel("Join Room");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(new Color(100, 200, 255));
        header.add(title, BorderLayout.WEST);

        joinState.setOpaque(true);
        joinState.setHorizontalAlignment(SwingConstants.CENTER);
        joinState.setBorder(new EmptyBorder(5, 10, 5, 10));
        setJoinState("Disconnected", new Color(64, 70, 82), new Color(220, 225, 235));
        header.add(joinState, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 78, 90)),
                new EmptyBorder(18, 18, 18, 18)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 8, 0);

        JLabel codeLabel = new JLabel("Room Code");
        codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, 12f));
        form.add(codeLabel, gbc);

        gbc.gridy++;
        joinCode.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        joinCode.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 120, 170)),
                new EmptyBorder(7, 8, 7, 8)));
        joinCode.addActionListener(e -> runAsyncUi(this::startJoinProxy));
        form.add(joinCode, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(8, 0, 18, 0);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(colorBtn("Connect", new Color(34, 140, 60), Color.WHITE, this::startJoinProxy));
        buttons.add(colorBtn("Disconnect", new Color(85, 92, 105), Color.WHITE, this::stopJoinProxy));
        buttons.add(button("Copy Address", () -> copy(joinAddress.getText())));
        form.add(buttons, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        JLabel addressLabel = new JLabel("Minecraft Address");
        addressLabel.setFont(addressLabel.getFont().deriveFont(Font.BOLD, 12f));
        form.add(addressLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        joinAddress.setEditable(false);
        joinAddress.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        joinAddress.setBackground(new Color(15, 23, 42));
        joinAddress.setForeground(new Color(187, 247, 208));
        joinAddress.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                new EmptyBorder(10, 10, 10, 10)));
        form.add(joinAddress, gbc);

        panel.add(form, BorderLayout.CENTER);
        wrapper.add(panel);
        return wrapper;
    }

    private JComponent settingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("System & App Info"));

        // -- App Info ----------------------------------------------------------
        String appVersion = "1.1.0";
        Path dataDir = store.getDataDir();

        // -- JVM heap snapshot ------------------------------------------------
        long heapUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long heapMax  = Runtime.getRuntime().maxMemory();

        // -- Physical system memory via OperatingSystemMXBean -----------------
        long totalPhysicalRam = -1;
        long freePhysicalRam  = -1;
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            totalPhysicalRam = osBean.getTotalMemorySize();
            freePhysicalRam  = osBean.getFreeMemorySize();
        } catch (Exception ignored) {}

        String ramLine = (totalPhysicalRam > 0)
            ? formatBytes(totalPhysicalRam - freePhysicalRam)
              + " used / " + formatBytes(totalPhysicalRam) + " total"
            : "unavailable";

        // -- Disk space for data folder ----------------------------------------
        String diskLine;
        try {
            java.io.File root = dataDir.toFile();
            long usable = root.getUsableSpace();
            long total  = root.getTotalSpace();
            diskLine = formatBytes(total - usable) + " used / " + formatBytes(total) + " total";
        } catch (Exception ignored) {
            diskLine = "unavailable";
        }

        // -- Hostname ----------------------------------------------------------
        String hostname;
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            hostname = "unknown";
        }

        String infoText = """
                +======================================+
                  VoxelPort  v%s
                +======================================+

                -- APP INFO ---------------------------
                Version      : %s
                Data Folder  : %s

                -- SYSTEM INFO ------------------------
                Hostname     : %s
                OS           : %s  %s
                Architecture : %s
                CPU Cores    : %d logical processors
                System RAM   : %s
                JVM Heap     : %s used / %s max
                Disk (data)  : %s

                -- JAVA RUNTIME -----------------------
                Java Version : %s
                Vendor       : %s
                JVM Home     : %s

                VoxelPort is a standalone Minecraft server
                management tool focused on performance
                and simplicity.
                """.formatted(
                appVersion,
                appVersion,
                dataDir,
                hostname,
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                ramLine,
                formatBytes(heapUsed), formatBytes(heapMax),
                diskLine,
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.home")
        );

        JTextArea info = new JTextArea(infoText);
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        info.setBackground(new Color(30, 30, 30));
        info.setForeground(new Color(200, 255, 200));
        info.setBorder(new EmptyBorder(8, 10, 8, 10));

        panel.add(new JScrollPane(info), BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        JButton openData = new JButton("Open Data Folder");
        openData.addActionListener(e -> CompletableFuture.runAsync(() -> open(store.getDataDir())));
        
        JButton sponsorBtn = new JButton("Sponsor");
        sponsorBtn.setForeground(new Color(216, 74, 123));
        sponsorBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/sponsors/trazhub"));
            } catch (Exception ex) {
                showError(ex);
            }
        });

        JButton updateBtn = new JButton("Check for Updates");
        updateBtn.addActionListener(e -> checkForAppUpdates());
        
        bottomBar.add(openData);
        bottomBar.add(sponsorBtn);
        bottomBar.add(updateBtn);
        
        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    private void checkForAppUpdates() {
        setStatus("Checking for app updates...");
        updateService.checkForUpdates().thenAccept(info -> {
            if (info == null) {
                setStatus("VoxelPort is up to date");
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "VoxelPort is already at the latest version (v" + org.localm.service.UpdateService.CURRENT_VERSION + ").", "Up to Date", JOptionPane.INFORMATION_MESSAGE));
                return;
            }

            SwingUtilities.invokeLater(() -> {
                int ok = JOptionPane.showConfirmDialog(this,
                        "A new version of VoxelPort is available: v" + info.version() + "\n\n" +
                        "Changes:\n" + info.changelog() + "\n\n" +
                        "Would you like to download and install it now?",
                        "Update Available", JOptionPane.YES_NO_OPTION);
                
                if (ok == JOptionPane.YES_OPTION) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            Path currentJar = updateService.getCurrentJarPath();
                            if (currentJar == null) throw new Exception("Could not locate running JAR file");
                            updateService.applyUpdate(info, currentJar, this::setStatus);
                        } catch (Exception e) {
                            showError(e);
                        }
                    });
                }
            });
        });
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private JPanel formRow(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(5, 5));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        row.setBorder(new EmptyBorder(0, 0, 10, 0));
        JLabel l = new JLabel(label);
        row.add(l, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> runAsyncUi(action));
        return b;
    }

    /** Button with explicit background/foreground (accent colors). */
    private JButton colorBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setOpaque(true);
        b.setFocusPainted(false);
        return b;
    }

    private JButton colorBtn(String text, Color bg, Color fg, Runnable action) {
        JButton b = colorBtn(text, bg, fg);
        b.addActionListener(e -> runAsyncUi(action));
        return b;
    }

    private void runAsyncUi(Runnable action) {
        CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> showError(ex));
            }
        });
    }

    private void checkUpdates() {
        String name = selectedServer();
        String mcVersion = store.getProperty(name + ".version");
        Path dir = store.getServerDir(name);
        setStatus("Checking for updates...");

        CompletableFuture.runAsync(() -> {
            try {
                String latestUrl = null;
                if (mcVersion != null) {
                    try {
                        latestUrl = versionService.latestPaperDownloadUrl(mcVersion);
                    } catch (Exception ignored) {}

                    if (latestUrl == null) {
                        try {
                            latestUrl = "https://api.purpurmc.org/v2/purpur/" + mcVersion + "/latest/download";
                        } catch (Exception ignored) {}
                    }
                }

                if (latestUrl == null) {
                    setStatus("Could not find update info");
                    return;
                }

                String finalUrl = latestUrl;
                SwingUtilities.invokeLater(() -> {
                    int ok = JOptionPane.showConfirmDialog(this, "Found latest build for " + mcVersion + ".\nDo you want to redownload server.jar to ensure it's up to date?", "Check Updates", JOptionPane.YES_NO_OPTION);
                    if (ok == JOptionPane.YES_OPTION) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                setStatus("Updating " + name + "...");
                                versionService.download(finalUrl, dir.resolve("server.jar"));
                                setStatus("Updated " + name);
                            } catch (Exception e) {
                                showError(e);
                            }
                        });
                    } else {
                        setStatus("Ready");
                    }
                });
            } catch (Exception e) {
                showError(e);
            }
        });
    }

    private void loadVersions() {
        CompletableFuture.runAsync(() -> {
            setStatus("Loading versions...");
            List<ServerVersion> versions = versionService.fetchVersions();
            SwingUtilities.invokeLater(() -> {
                versionBox.removeAllItems();
                versions.forEach(versionBox::addItem);
                setStatus(versions.isEmpty() ? "Could not load versions" : "Ready");
            });
        });
    }

    private void installServer(boolean isCracked) {
        ServerVersion version = (ServerVersion) versionBox.getSelectedItem();
        if (version == null) {
            throw new IllegalStateException("No server version selected. Wait for versions to load, then try again.");
        }
        String name = cleanName(serverName.getText());

        setStatus("Choose an install folder for " + name + "...");
        Path parentDir = chooseParentInstallDirectory(name);
        if (parentDir == null) {
            setStatus("Install cancelled");
            return;
        }
        Path dir = parentDir.resolve(name);
        try {
            Files.createDirectories(dir);
            int port = findNextFreeServerPort();
            Files.writeString(dir.resolve("eula.txt"), "eula=true\n");
            Files.writeString(dir.resolve("server.properties"), "server-port=" + port + "\nonline-mode=" + (!isCracked) + "\nmax-players=20\nmotd=VoxelPort Server\n");

            if (version.label().startsWith("Forge")) {
                setStatus("Downloading Forge installer...");
                Path installer = dir.resolve("forge-installer.jar");
                versionService.download(version.url(), installer);
                setStatus("Installing Forge (this may take a few minutes)...");
                String javaBin = processManager.detectJava(version.mcVersion());
                Process p = new ProcessBuilder(javaBin, "-jar", "forge-installer.jar", "--installServer")
                        .directory(dir.toFile())
                        .start();
                p.waitFor();
                Files.deleteIfExists(installer);
                Files.deleteIfExists(dir.resolve("forge-installer.jar.log"));

                if (!Files.exists(dir.resolve("user_jvm_args.txt"))) {
                    try (var stream = Files.list(dir)) {
                        Path forgeJar = stream.filter(f -> f.getFileName().toString().startsWith("forge-") && f.toString().endsWith(".jar"))
                                .findFirst().orElse(null);
                        if (forgeJar != null) {
                            Files.move(forgeJar, dir.resolve("server.jar"), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            } else {
                setStatus("Downloading server jar...");
                versionService.download(version.url(), dir.resolve("server.jar"));
            }

            store.setProperty(name + ".dir", dir.toString());
            store.setProperty(name + ".version", version.mcVersion());
            store.setProperty(name + ".port", String.valueOf(port));
            store.setProperty(name + ".ram", String.valueOf(getSelectedRam()));
            store.setProperty(name + ".autoBackup", String.valueOf(autoBackup.isSelected()));
            store.setProperty(name + ".onlineMode", String.valueOf(!isCracked));
            store.save();
            refreshServerList();
            setStatus("Installed " + name + " on port " + port);
        } catch (Exception e) {
            org.localm.util.Logger.error("Install failed", e);
            throw new RuntimeException(e);
        }
    }

    private Path chooseParentInstallDirectory(String serverName) {
        if (SwingUtilities.isEventDispatchThread()) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Choose parent folder for " + serverName);
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
            return chooser.getSelectedFile().toPath();
        }

        final Path[] selectedPath = new Path[1];
        final RuntimeException[] failure = new RuntimeException[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setDialogTitle("Choose parent folder for " + serverName);
                    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                        selectedPath[0] = chooser.getSelectedFile().toPath();
                    }
                } catch (RuntimeException e) {
                    failure[0] = e;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to open install directory chooser", e);
        }

        if (failure[0] != null) throw failure[0];
        return selectedPath[0];
    }

    private int getSelectedRam() {
        return ramSlider.getValue();
    }
    
    private int getSystemRamMb() {
        try {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return (int) (osBean.getTotalMemorySize() / 1024 / 1024);
        } catch (Exception e) {
            return 16384;
        }
    }

    private int findNextFreeServerPort() {
        int port = 25565;
        Set<Integer> usedPorts = new HashSet<>();
        for (String key : store.stringPropertyNames()) {
            if (key.endsWith(".port")) {
                String name = key.substring(0, key.length() - 5);
                if (store.containsKey(name + ".dir")) {
                    try {
                        usedPorts.add(Integer.parseInt(store.getProperty(key)));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        while (usedPorts.contains(port)) {
            port++;
        }
        return port;
    }

    private void startServer() {
        String name = selectedServer();
        Path dir = store.getServerDir(name);
        if (processManager.isAlive(name)) throw new IllegalStateException("Server already running");
        
        store.setProperty(name + ".ram", String.valueOf(getSelectedRam()));
        store.setProperty(name + ".port", serverPort.getText());
        store.setProperty(name + ".autoBackup", String.valueOf(autoBackup.isSelected()));
        try { store.save(); } catch (IOException ignored) {}

        try {
            int port = Integer.parseInt(store.getProperty(name + ".port", "25565"));
            Path propsFile = dir.resolve("server.properties");
            if (Files.exists(propsFile)) {
                String content = Files.readString(propsFile);
                content = content.replaceAll("server-port=\\d+", "server-port=" + port);
                boolean onlineMode = store.getBoolean(name + ".onlineMode", true);
                content = content.replaceAll("online-mode=(true|false)", "online-mode=" + onlineMode);
                Files.writeString(propsFile, content);
            }

            String mcVersion = store.getProperty(name + ".version", "1.21");
            int ram = Integer.parseInt(store.getProperty(name + ".ram", String.valueOf(getSelectedRam())));
            String webhook = store.getProperty(name + ".webhookUrl", "");
            
            String profile = store.getProperty(name + ".javaProfile", "Stable (G1GC)");
            List<String> flags = switch (profile) {
                case "Aggressive (Parallel)" -> RamPreset.AGGRESSIVE;
                default -> RamPreset.STABLE;
            };

            processManager.startServer(name, dir, mcVersion, ram, flags, webhook, (n, text) -> {
                SwingUtilities.invokeLater(() -> appendConsole(n, text));
            }, () -> {
                if (store.getBoolean(name + ".autoBackup", false)) {
                    doBackup(name, false);
                }
                SwingUtilities.invokeLater(() -> {
                    setStatus(name + " stopped");
                    serverList.repaint();
                    playerModel.clear();
                });
            });
            setStatus("Server running: " + name);
            serverList.repaint();
        } catch (IOException e) {
            org.localm.util.Logger.error("Start failed", e);
            throw new RuntimeException(e);
        }
    }

    private void stopServer() {
        String name = selectedServer();
        stopServerByName(name);
    }

    private void stopServerByName(String name) {
        processManager.stopServer(name);
        if (processManager.isAlive(name)) {
            setStatus("Stopping " + name + "...");
        }
    }

    private void appendConsole(String name, String text) {
        DefaultStyledDocument doc = consoleDocs.computeIfAbsent(name, k -> new DefaultStyledDocument());

        // Simple player list parsing for 'list' command
        if (text.contains("There are ") && text.contains("players online:")) {
            String[] parts = text.split("players online:");
            if (parts.length > 1) {
                String list = parts[1].trim();
                SwingUtilities.invokeLater(() -> {
                    playerModel.clear();
                    if (!list.isEmpty()) {
                        for (String p : list.split(",")) playerModel.addElement(p.trim());
                    }
                });
            }
        } else if (text.contains("joined the game")) {
             String p = text.split(" joined the game")[0];
             if (p.contains("] ")) p = p.split("\\] ")[1];
             final String player = p.trim();
             SwingUtilities.invokeLater(() -> { if (!playerModel.contains(player)) playerModel.addElement(player); });
        } else if (text.contains("left the game")) {
             String p = text.split(" left the game")[0];
             if (p.contains("] ")) p = p.split("\\] ")[1];
             final String player = p.trim();
             SwingUtilities.invokeLater(() -> playerModel.removeElement(player));
        }

        try {
            int lastIdx = 0;
            Matcher m = Pattern.compile("\u001B\\[([;\\d]*)m").matcher(text);
            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setForeground(style, CONSOLE_FG);
            StyleConstants.setFontFamily(style, Font.MONOSPACED);
            StyleConstants.setFontSize(style, 13);

            while (m.find()) {
                String segment = text.substring(lastIdx, m.start());
                if (!segment.isEmpty()) {
                    doc.insertString(doc.getLength(), segment, style);
                }
                
                String params = m.group(1);
                if (params == null || params.isEmpty() || "0".equals(params)) {
                    style = new SimpleAttributeSet();
                    StyleConstants.setForeground(style, CONSOLE_FG);
                    StyleConstants.setFontFamily(style, Font.MONOSPACED);
                    StyleConstants.setFontSize(style, 13);
                } else {
                    for (String part : params.split(";")) {
                        if (part.isEmpty()) continue;
                        int code = Integer.parseInt(part);
                        if (code == 0) {
                            StyleConstants.setForeground(style, CONSOLE_FG);
                            StyleConstants.setBold(style, false);
                        } else if (code == 1) {
                            StyleConstants.setBold(style, true);
                        } else if ((code >= 30 && code <= 37) || (code >= 90 && code <= 97)) {
                            StyleConstants.setForeground(style, getAnsiColor(code));
                        }
                    }
                }
                lastIdx = m.end();
            }
            String remaining = text.substring(lastIdx);
            if (!remaining.isEmpty()) {
                doc.insertString(doc.getLength(), remaining, style);
            }
            doc.insertString(doc.getLength(), "\n", style);

            if (name.equals(serverList.getSelectedValue())) {
                console.setCaretPosition(doc.getLength());
            }
        } catch (BadLocationException ignored) {}
    }

    private Color getAnsiColor(int code) {
        return switch (code) {
            case 30 -> CONSOLE_MUTED;
            case 31 -> new Color(248, 113, 113);
            case 32 -> new Color(134, 239, 172);
            case 33 -> new Color(253, 224, 71);
            case 34 -> new Color(147, 197, 253);
            case 35 -> new Color(216, 180, 254);
            case 36 -> new Color(103, 232, 249);
            case 37 -> CONSOLE_FG;
            case 90 -> new Color(148, 163, 184);
            case 91 -> new Color(252, 165, 165);
            case 92 -> new Color(187, 247, 208);
            case 93 -> new Color(254, 240, 138);
            case 94 -> new Color(191, 219, 254);
            case 95 -> new Color(233, 213, 255);
            case 96 -> new Color(165, 243, 252);
            case 97 -> Color.WHITE;
            default -> CONSOLE_FG;
        };
    }

    private void openServerFolder() {
        open(store.getServerDir(selectedServer()));
    }

    private void openPluginsFolder() {
        Path plugins = store.getServerDir(selectedServer()).resolve("plugins");
        try {
            Files.createDirectories(plugins);
            open(plugins);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteServer() {
        String name = selectedServer();
        Path dir = store.getServerDir(name);
        int ok = JOptionPane.showConfirmDialog(this, "Delete " + name + " from VoxelPort and from this PC?\nThis removes worlds, plugins, and backups.", "Delete Server", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        if (processManager.isAlive(name)) throw new IllegalStateException("Stop the server before deleting it");
        try {
            deleteRecursive(dir);
            store.remove(name + ".dir");
            store.remove(name + ".version");
            store.remove(name + ".port");
            store.remove(name + ".ram");
            store.remove(name + ".autoBackup");
            store.save();
            refreshServerList();
            setStatus("Deleted " + name);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class StatusRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String name = (String) value;
            boolean online = processManager.isAlive(name);
            String stats = processManager.getProcessStats(name);

            label.setText(online && !stats.isEmpty() ? name + " (" + stats + ")" : name);
            label.setBorder(new EmptyBorder(2, 5, 2, 5));

            label.setIcon(new Icon() {
                @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                    g.setColor(online ? Color.GREEN : Color.GRAY);
                    g.fillOval(x, y + 2, 8, 8);
                }
                @Override public int getIconWidth() { return 12; }
                @Override public int getIconHeight() { return 12; }
            });
            return label;
        }
    }

    private void startRoom() {
        try {
            String name = selectedServer();
            int serverPort = Integer.parseInt(store.getProperty(name + ".port", "25565"));
            tunnelService.startRoom(serverPort, code -> {
                roomCode.setText(code);
                store.setProperty(name + ".roomCode", code);
                try { store.save(); } catch (IOException ignored) {}
            }, this::setStatus);
        } catch (Exception e) {
            org.localm.util.Logger.error("Room start failed", e);
            throw new RuntimeException(e);
        }
    }

    private void stopRoom() {
        tunnelService.stopRoom();
    }

    private void startJoinProxy() {
        try {
            if (joinCode.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("Enter a room code first");
            }
            setJoinState("Connecting", new Color(120, 85, 25), new Color(255, 235, 180));
            tunnelService.startJoinProxy(joinCode.getText().trim(), 25565, this::setStatus);
            joinAddress.setText("localhost:25565");
            setJoinState("Connected", new Color(25, 110, 65), new Color(220, 255, 235));
        } catch (Exception e) {
            setJoinState("Disconnected", new Color(64, 70, 82), new Color(220, 225, 235));
            throw new RuntimeException(e);
        }
    }

    private void stopJoinProxy() {
        tunnelService.stopJoinProxy();
        setJoinState("Disconnected", new Color(64, 70, 82), new Color(220, 225, 235));
        setStatus("Join proxy stopped");
    }

    private void refreshServerList() {
        store.cleanupOrphans();
        serverModel.clear();
        store.stringPropertyNames().stream()
                .filter(k -> k.endsWith(".dir"))
                .map(k -> k.substring(0, k.length() - 4))
                .sorted()
                .forEach(serverModel::addElement);
        if (!serverModel.isEmpty()) {
            serverList.setSelectedIndex(0);
            updateConfigUi();
        }
    }

    private void updateConfigUi() {
        String name = serverList.getSelectedValue();
        if (name == null) return;
        serverName.setText(name);
        serverPort.setText(store.getProperty(name + ".port", "25565"));
        autoBackup.setSelected(store.getBoolean(name + ".autoBackup", false));
        roomCode.setText(store.getProperty(name + ".roomCode", ""));
        webhookField.setText(store.getProperty(name + ".webhookUrl", ""));

        console.setDocument(consoleDocs.computeIfAbsent(name, k -> new DefaultStyledDocument()));

        // Update Graph
        graphContainer.removeAll();
        graphContainer.add(ramGraphs.computeIfAbsent(name, k -> new SparklineGraph("RAM", new Color(100, 200, 255))));
        graphContainer.revalidate();
        graphContainer.repaint();

        String ramStr = store.getProperty(name + ".ram", "4096");
        try {
            int ram = Integer.parseInt(ramStr);
            ramSlider.setValue(ram);
        } catch (Exception ignored) {}
        }

    private String selectedServer() {
        String selected = serverList.getSelectedValue();
        if (selected == null) throw new IllegalStateException("Select a server first");
        return selected;
    }

    private void highlightSearch(String query) {
        if (query == null || query.isBlank()) {
            console.getHighlighter().removeAllHighlights();
            return;
        }

        try {
            Highlighter highlighter = console.getHighlighter();
            highlighter.removeAllHighlights();
            Document doc = console.getDocument();
            String text = doc.getText(0, doc.getLength());
            int pos = 0;
            while ((pos = text.toLowerCase().indexOf(query.toLowerCase(), pos)) >= 0) {
                highlighter.addHighlight(pos, pos + query.length(), new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 0, 100)));
                pos += query.length();
            }
        } catch (Exception ignored) {}
    }

    private void applyConsoleFilter(String name, boolean filter) {
        // In a real app, we'd maintain a raw log and a filtered view.
        // For simplicity here, we just notify the user it's a future feature or do a simple hide.
        setStatus(filter ? "Noise filter active (Experimental)" : "Noise filter inactive");
    }

    private String cleanName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.matches(".*[<>:\"/\\\\|?*].*")) throw new IllegalArgumentException("Invalid server name");
        return value;
    }

    private void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        }
    }

    private void copy(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        setStatus("Copied");
    }

    private void open(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private void setStatus(String message) {
        SwingUtilities.invokeLater(() -> status.setText(message));
    }

    private void setJoinState(String message, Color bg, Color fg) {
        SwingUtilities.invokeLater(() -> {
            joinState.setText(message);
            joinState.setBackground(bg);
            joinState.setForeground(fg);
        });
    }

    private void showError(Throwable error) {
        String text = errorText(error);
        JTextArea details = new JTextArea(text, 6, 64);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        details.setBorder(new EmptyBorder(8, 8, 8, 8));

        JButton copy = new JButton("Copy Error");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            setStatus("Error copied");
        });

        Object[] options = {copy, "OK"};
        JOptionPane.showOptionDialog(
                this,
                new JScrollPane(details),
                "VoxelPort Error",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                options,
                "OK");
        setStatus("Error: " + error.getMessage());
    }

    private String errorText(Throwable error) {
        StringWriter out = new StringWriter();
        error.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}

