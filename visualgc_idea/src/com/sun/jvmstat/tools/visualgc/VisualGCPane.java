package com.sun.jvmstat.tools.visualgc;

import beansoft.swing.OptionPane;
import com.github.beansoft.jvm.ApplicationSettingsService;
import com.github.beansoft.jvm.PluginSettings;
import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.*;
import com.sun.jvmstat.graph.FIFOList;
import com.sun.jvmstat.graph.GridDrawer;
import com.sun.jvmstat.graph.Level;
import com.sun.jvmstat.graph.Line;
import com.sun.jvmstat.tools.visualgc.resource.Res;
import github.beansoftapp.visualgc.Exceptions;
import github.beansoftapp.visualgc.JpsHelper;
import github.beansoftapp.visualgc.MonitoredHostHelper;
import org.graalvm.visualvm.core.ui.components.DataViewComponent;
import org.graalvm.visualvm.core.ui.components.NotSupportedDisplayer;
import sun.jvmstat.monitor.*;
import sun.jvmstat.monitor.event.HostEvent;
import sun.jvmstat.monitor.event.HostListener;
import sun.jvmstat.monitor.event.VmStatusChangeEvent;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 单面板模式
public class VisualGCPane implements ActionListener {
    private static final com.intellij.openapi.diagnostic.Logger LOGGER = com.intellij.openapi.diagnostic.Logger.getInstance(VisualGCPane.class.getName());
    // Begin >>>
    private static final boolean ORIGINAL_UI = Boolean.getBoolean("org.graalvm.visualvm.modules.visualgc.originalUI");
    private static final Color NORMAL_GRAY_ALPHA = new Color(165, 165, 165, 255 / 2);// Half transparent 50% 透明度图表
    private static final Color LIGHTER_GRAY = new Color(220, 220, 220);
    private static final Color EVEN_LIGHTER_GRAY = new Color(242, 242, 242);

    static {
        customizeColors();
    }

    protected Project myProject;
    private volatile boolean terminated = false;
    private Timer timer;
    private boolean modelAvailable = false;
    private MasterViewSupport masterViewSupport;
    private GraphGCViewSupport graphGCViewSupport;
    private HistogramViewSupport histogramViewSupport;
    private SpacesViewSupport spacesViewSupport;
    private boolean histogramSupported = true;
    private MonitoredVmModel model;
    private boolean hasMetaspace;
    private Executor executor = Executors.newSingleThreadExecutor();
    private String vmIdString;
    private VmIdentifier vmId;

    private Container contentPane;

    /**
     * 进程名
     */
    private String pName = "";
    private PsListModel psListModel = new PsListModel();

    /**
     * Process list
     */
    private JBList<String> psList = new JBList<>(psListModel);

    private PSListViewSupport psListViewSupport;

    /**
     * 是否隐藏 JVM 列表, 用于关联运行JVM进程时使用.
     */
    private boolean hideJvmBrowser = false;
    private ActionListener stopMonitorAction = e -> stopMonitor();

    public VisualGCPane() {
    }

    public VisualGCPane(Project project) {
        this.myProject = project;
    }

    /**
     * 自定义图标颜色
     */
    public static void customizeColors() {
        if (ORIGINAL_UI)
            return;
        System.setProperty("graphgc.gc.color", colorString(152, 178, 0));
        System.setProperty("graphgc.class.color", "" + colorString(81, 131, 160));
        System.setProperty("graphgc.compile.color", colorString(52, 87, 106));
        System.setProperty("eden.color", "" + colorString(200, 145, 1));
        System.setProperty("survivor.color", colorString(193, 101, 0));
        System.setProperty("old.color", "" + colorString(127, 122, 2));
        System.setProperty("perm.color", "" + colorString(235, 156, 8));
    }

    private static String colorString(int r, int g, int b) {
        return Integer.toString((new Color(r, g, b)).getRGB());
    }

    // Fix by beansoft
    private static void customizeVisualHeap(VisualHeap heap) {
        try {
            Field fieldLivenessIndicator = VisualHeap.class.getDeclaredField("livenessIndicator");
            fieldLivenessIndicator.setAccessible(true);
            JLabel livenessIndicator = (JLabel) fieldLivenessIndicator.get(heap);

            if (livenessIndicator.getForeground() == Color.white) {
                livenessIndicator.setForeground(Color.black);
            }
        } catch (NoSuchFieldException ex) {
            Exceptions.printStackTrace(ex);
        } catch (SecurityException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalArgumentException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IllegalAccessException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    // 自定义所有图表网格和文本框的颜色
    private static void customizeComponents(Component component, List<GridDrawer> gridDrawers) {
        if (ORIGINAL_UI)
            return;
        if (component == null)
            return;
        if (!(component instanceof JComponent))
            return;
        JComponent jComponent = (JComponent) component;
        if (jComponent.getBorder() instanceof TitledBorder) {
            TitledBorder titledBorder = (TitledBorder) jComponent.getBorder();
            TitledBorder newBorder = new TitledBorder(titledBorder.getTitle());
            newBorder.setBorder(BorderFactory.createLineBorder(NORMAL_GRAY_ALPHA, 1));
            newBorder.setTitleColor(titledBorder.getTitleColor());
            // Fix title border
            if (titledBorder.getTitleColor() == Color.white) {
                newBorder.setTitleColor(Color.black);
            }
            Font titleFont = newBorder.getTitleFont();
            if (titleFont == null)
                titleFont = UIManager.getFont("TitledBorder.font");
            if (titleFont == null)
                titleFont = UIManager.getFont("Label.font");
            newBorder.setTitleFont(titleFont.deriveFont(Font.BOLD));
            jComponent.setBorder(newBorder);
        } else if (jComponent instanceof Line) {
            Line line = (Line) jComponent;
//      line.setGridPrimaryColor(LIGHTER_GRAY);
//      line.setGridSecondaryColor(EVEN_LIGHTER_GRAY);
            line.setGridPrimaryColor(NORMAL_GRAY_ALPHA);
            line.setGridSecondaryColor(NORMAL_GRAY_ALPHA);
        } else if (jComponent instanceof Level) {
            Level level = (Level) jComponent;
            level.setPreferredSize(new Dimension(25, 15));
            level.setMinimumSize(level.getPreferredSize());
            try {
                Class<?> levelClass = level.getClass();
                Field gridDrawerField = levelClass.getDeclaredField("gridDrawer");
                gridDrawerField.setAccessible(true);
                GridDrawer gridDrawer = (GridDrawer) gridDrawerField.get(level);
//        gridDrawer.setPrimaryColor(LIGHTER_GRAY);
                gridDrawer.setPrimaryColor(NORMAL_GRAY_ALPHA);
                if (gridDrawers != null)
                    gridDrawers.add(gridDrawer);
            } catch (Exception e) {
                Exceptions.printStackTrace(e);
            }
        } else if (jComponent instanceof JLabel) {
            JLabel label = (JLabel) jComponent;
            label.setFont(UIManager.getFont("Label.font"));
            if (label.getText().endsWith(": "))
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

            if (label.getForeground() == Color.white) {
                label.setForeground(Color.black);
            }
        } else if (jComponent instanceof JTextArea) { // 覆盖 JTextArea 的颜色
            JTextArea label = (JTextArea) jComponent;
            label.setFont(UIManager.getFont("Label.font"));
            if (label.getText().endsWith(": "))
                label.setFont(label.getFont().deriveFont(Font.BOLD));
//      label.setForeground(Color.BLACK);
        }
        jComponent.setOpaque(false);
        for (Component child : jComponent.getComponents())
            customizeComponents(child, gridDrawers);
    }

    public static void main(String args[]) {
//		args = new String[]{"412"};
//		args = new String[]{ GetProcessID.getPid() + ""};
//    Locale.setDefault(Locale.ENGLISH);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        }

        // -XX:MaxDirectMemorySize=2048m
//    long mdm = VM.maxDirectMemory();
//    System.out.println("Max direct memory size is:" + Converter.longToKMGString(mdm));

        customizeColors();

        JFrame frame = new JFrame();
        frame.setIconImage(new ImageIcon(VisualGCPane.class.getResource("/visualgc.png")).getImage());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
//    frame.setTitle(Res.getString("visualgc.3.0.loading.jvm.process.list"));
        frame.setTitle("VisualGC 3.0");
//    frame.getContentPane().add(new JLabel("Loading JVM process list on your machine...."));


//    String pName = "";
//    if (args == null || args.length == 0) {
//
//      JList list = new JList(JpsHelper.getJvmPSList().toArray());
//      String processInput = JOptionPane.showInputDialog(frame, list,
//          Res.getString("please.choose.a.process"), JOptionPane.QUESTION_MESSAGE);
//      if (processInput != null && processInput.length() != 0) {
//        args = new String[]{processInput};
//        String info = JpsHelper.getVmInfo(processInput);
//        pName = " - " + info;
//      } else {
//        String val = (list.getSelectedValue() != null) ? list.getSelectedValue().toString() : null;
//        if (val == null) {
//          int pid = GetProcessID.getPid();
//          args = new String[]{String.valueOf(GetProcessID.getPid())};
//          String info = JpsHelper.getVmInfo(pid + "");
//          pName = " - " + info;
//        } else {
//          processInput = val.substring(0, val.indexOf(' '));
//          pName = " - " + val;
//          args = new String[]{processInput};
//        }
//      }
////      System.out.println(s);
//    }
//
//
//    try {
//      arguments = new Arguments(args);
//    } catch (IllegalArgumentException var29) {
//      System.err.println(var29.getMessage());
//      Arguments.printUsage(System.err);
//      System.exit(1);
//    }
//
//    if (arguments.isHelp()) {
//      Arguments.printUsage(System.out);
//      System.exit(1);
//    }
//
//    if (arguments.isVersion()) {
//      Arguments.printVersion(System.out);
//      System.exit(1);
//    }

        VisualGCPane gcPane = new VisualGCPane();

//    frame.setTitle("VisualGC 3.0" + pName);
//    gcPane.startMonitor(arguments.vmIdString());
        frame.getContentPane().add(gcPane.createComponent(frame.getContentPane()), BorderLayout.CENTER);
        frame.setSize(1024, 768);
        frame.setVisible(true);
    }

    public void startMonitor(String vmIdString) {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        if (vmIdString == null || vmIdString.length() == 0) {
            return;
        }

        this.vmIdString = vmIdString;
        try {
            this.vmId = new VmIdentifier(this.vmIdString);
        } catch (URISyntaxException e) {
            OptionPane.showErrorMessageDialog(null, Res.getString("malformed.vm.identifier.0", this.vmIdString), "Error");
            e.printStackTrace();
        }

        this.modelAvailable = initializeModel();
        if (this.modelAvailable) {
            if (timer == null) {
                this.timer = new Timer(1000, this);
            }
        }

//    MonitoredVmModel monitoredvmmodel = null;
//    MonitoredHost monitoredhost = null;
//    MonitoredVm monitoredvm = null;
//    try {
//      VmIdentifier vmidentifier = arguments.vmId();
//      monitoredhost = MonitoredHost.getMonitoredHost(vmidentifier);
//      monitoredvm = monitoredhost.getMonitoredVm(vmidentifier, 500);// Refresh rate?
//      monitoredvmmodel = new MonitoredVmModel(monitoredvm);
//      ModelFixer.fixMetaspace(monitoredvmmodel, monitoredvm);
//
//
//
//      if (vmidentifier.getLocalVmId() != 0)
//        monitoredhost.addHostListener(new TerminationHandler(vmidentifier.getLocalVmId(), monitoredhost));
//    } catch (MonitorException monitorexception) {
//      if (monitorexception.getMessage() != null) {
//        System.err.println(monitorexception.getMessage());
//      } else {
//        Throwable throwable = monitorexception.getCause();
//        if (throwable != null && throwable.getMessage() != null)
//          System.err.println(throwable.getMessage());
//        else
//          monitorexception.printStackTrace();
//      }
//      if (monitoredhost != null && monitoredvm != null) {
//        try {
//          monitoredhost.detach(monitoredvm);
//        } catch (Exception ignored) {
//        }
//      }
//    }
    }

    private boolean initializeModel() {
        MonitoredVm monitoredVm = null;
        try {
            monitoredVm = getMonitoredVm();
            if (monitoredVm != null) {
                MonitoredVmModel testModel = new MonitoredVmModel(monitoredVm);
                List<Monitor> monitors = monitoredVm.findByPattern(".*");
                if (monitors != null) {
                    for (Monitor m : monitors) {
//            System.out.println(m.getName());
                    }
                }

                StringMonitor gen1name = (StringMonitor) monitoredVm.findByName("sun.gc.generation.1.name");
                System.out.println("sun.gc.generation.1.name = " + gen1name.stringValue());//ZGC is old
                this.hasMetaspace = ModelFixer.fixMetaspace(testModel, monitoredVm);
                GCSample gcsample = new GCSample(testModel);
                this.histogramSupported = gcsample.ageTableSizes != null;
                this.model = testModel;
                terminated = false;
                return true;
            }
        } catch (MonitorException ex) {
            LOGGER.debug( "Could not get MonitoredVM", (Throwable) ex);
        } catch (Exception ex) {
            LOGGER.debug( "Could not create GCSample", ex);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        if (monitoredVm != null)
            monitoredVm.detach();
        return false;
    }

    private MonitoredVm getMonitoredVm() throws MonitorException {
        if (this.vmIdString == null || this.vmId == null)
            return null;

        try {
            MonitoredHost monitoredHost = MonitoredHostHelper.getMonitoredHost(this.vmId);
            int refreshInterval = 1000;
            monitoredHost.addHostListener(new TerminationHandler(this.vmId.getLocalVmId(), monitoredHost));
            return monitoredHost.getMonitoredVm(this.vmId, refreshInterval);
        } catch (Throwable ex) {
            String message = "Error: getMonitoredVm failed, please read https://plugins.jetbrains.com/plugin/14557-jdk-visualgc/idea-2022-2-fix for how to fix this issue";
            boolean sureOpen = Messages.showYesNoDialog(message, "Open Help Doc?",
                    CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(),
                    Messages.getErrorIcon()) ==
                    Messages.YES;

            if (sureOpen) {
                BrowserUtil.browse("https://plugins.jetbrains.com/plugin/14557-jdk-visualgc/idea-2022-2-fix");
            }

            // LOGGER.error(", please read https://plugins.jetbrains.com/plugin/14557-jdk-visualgc/idea-2022-2-fix for how to fix this issue");
            // LOGGER.log(java.util.logging.Level.INFO, "getMonitoredVm failed", ex);
            return null;
        }
    }

    // 停止监控并转向选择进程列表
    public void stopMonitor() {
        if (timer != null) timer.stop();
        this.vmIdString = null;
        this.model = null;
        this.modelAvailable = false;
        terminated = true;
        hideJvmBrowser = false;
        SwingUtilities.invokeLater(() -> {
            contentPane.removeAll();
            contentPane.add(createComponent(contentPane));
            contentPane.revalidate();
        });
    }

    /**
     * Complete destroy the monitor
     */
    public void dispose() {
        if (timer != null) timer.stop();
        this.vmIdString = null;
        this.model = null;
        this.modelAvailable = false;
        terminated = true;
        psListModel.stop();
        contentPane.removeAll();
        contentPane.add(createComponent(contentPane));
        psListModel = null;
        contentPane = null;
        model = null;
        vmId = null;
    }

    protected void removed() {
        if (!this.modelAvailable)
            return;
        this.timer.stop();
        this.vmIdString = null;
        this.model = null;
    }

    public void actionPerformed(ActionEvent e) {
        refresh();
        FIFOList.timeStamp = System.currentTimeMillis();
    }

    private void refresh() {
        if (!this.timer.isRunning())
            return;
        if (this.model == null) {
            removed();
        } else {
            executor.execute(() -> {
                if (terminated) {
                    timer.stop();
                }

                try {
//            System.out.println("refresh() model=" + model);
                    GCSample gcsample = new GCSample(model);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            masterViewSupport.refresh(gcsample);
                            graphGCViewSupport.refresh(gcsample);
                            histogramViewSupport.refresh(gcsample);
                            spacesViewSupport.refresh(gcsample, graphGCViewSupport, histogramViewSupport);
                        }
                    });
                } catch (Exception e) {
                    timer.stop();
                }
            });
        }
    }

    /**
     * Monitor the JVM process and refresh content pane.
     *
     * @param processId process Id
     * @param pName     process display name
     */
    public void monitorProcessAndRefreshPane(int processId, String pName) {
        this.pName = processId + " " + pName;
        startMonitor(processId + "");
//              psListModel.stop();
        hideJvmBrowser = true;
        contentPane.removeAll();
        contentPane.add(createComponent(contentPane));

    }

    protected DataViewComponent createComponent(Container contentPane) {
        this.contentPane = contentPane;

        psList.setBorder(BorderFactory.createTitledBorder(Res.getString("double.click.a.jvm.process.to.start")));
        psList.getEmptyText().setText(Res.getString("status.text.loading.jvm.process.list"));
        psList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    String val = psList.getSelectedValue();
                    if (val != null) {
                        if (timer != null) timer.stop();
                        pName = val;

                        String processInput = val.substring(0, val.indexOf(' '));
                        startMonitor(processInput);
//              psListModel.stop();
                        contentPane.removeAll();
                        contentPane.add(createComponent(contentPane));
//            timer.start();
                    }
                }
            }
        });

        this.psListViewSupport = new PSListViewSupport();

        // GC 数据不可用时只显示进程列表
        if (!this.modelAvailable) {
            DataViewComponent.MasterView masterView = new DataViewComponent.MasterView("Visual GC", null, psListViewSupport);
//          new NotSupportedDisplayer(NotSupportedDisplayer.JVM));
            DataViewComponent.MasterViewConfiguration masterViewConfiguration = new DataViewComponent.MasterViewConfiguration(true);
            return new DataViewComponent(masterView, masterViewConfiguration);
        }

        this.masterViewSupport = new MasterViewSupport(this.timer);
        this.graphGCViewSupport = new GraphGCViewSupport();
        this.histogramViewSupport = new HistogramViewSupport();
        this.spacesViewSupport = new SpacesViewSupport();

        // 顶部标题区
        DataViewComponent.MasterView monitoringMasterView = new DataViewComponent.MasterView("Visual GC - " + pName,
                Res.getString("a.visual.garbage.collection.monitoring.tool"), this.masterViewSupport);

        DataViewComponent.MasterViewConfiguration monitoringMasterConfiguration = new DataViewComponent.MasterViewConfiguration(false);
        DataViewComponent dvc = new DataViewComponent(monitoringMasterView, monitoringMasterConfiguration);
        dvc.configureDetailsView(new DataViewComponent.DetailsViewConfiguration(0.35D, 0.15D, 0.7D, -1.0D, 0.66D, 0.85D));
        dvc.configureDetailsArea(new DataViewComponent.DetailsAreaConfiguration(
                Res.getString("graphs"), true), DataViewComponent.TOP_RIGHT);
        dvc.addDetailsView(this.graphGCViewSupport.getDetailsView(), DataViewComponent.TOP_RIGHT);
//    dvc.addDetailsView( new DataViewComponent.DetailsView( "GC Policy", null, 10, new JLabel(GCSample.gcPolicyName), null), DataViewComponent.BOTTOM_RIGHT);// Add a Tab

        if (!hideJvmBrowser) {
            dvc.addDetailsView(new DataViewComponent.DetailsView(Res.getString("jvm.browser"), null,
                    10, this.psListViewSupport, null), DataViewComponent.BOTTOM_RIGHT);// Add a Tab

            // 第二个标签 暂时隐藏 2022-9-18
//            HyperlinkLabel createdByLink = new HyperlinkLabel(Res.getString("this.tool.created.by"));
//            createdByLink.addHyperlinkListener((e) -> {
//                try {
//                    Desktop.getDesktop().browse(new URI("https://github.com/beansoft/visualgc_java8/tree/master/visualgc_idea"));
//                } catch (IOException | URISyntaxException ioException) {
//                    ioException.printStackTrace();
//                }
//            });
//            dvc.addDetailsView(new DataViewComponent.DetailsView(Res.getString("info"), null, 10,
//                    createdByLink,
//                    null), DataViewComponent.BOTTOM_RIGHT);// Add a Tab
        }

        dvc.configureDetailsArea(new DataViewComponent.DetailsAreaConfiguration(
                Res.getString("histogram"), true), DataViewComponent.BOTTOM_LEFT);
        dvc.addDetailsView(this.histogramViewSupport.getDetailsView(), DataViewComponent.BOTTOM_LEFT);
        if (!this.histogramSupported)
            dvc.hideDetailsArea(DataViewComponent.BOTTOM_LEFT);
        dvc.configureDetailsArea(new DataViewComponent.DetailsAreaConfiguration(
                Res.getString("spaces"), true), DataViewComponent.TOP_LEFT);
        dvc.addDetailsView(this.spacesViewSupport.getDetailsView(), DataViewComponent.TOP_LEFT);
        this.timer.start();
        refresh();
        return dvc;
    }

    private static class ComboRenderer implements ListCellRenderer {
        private ListCellRenderer renderer;

        ComboRenderer(JComboBox combo) {
            this.renderer = combo.getRenderer();
            if (this.renderer instanceof JLabel)
                ((JLabel) this.renderer).setHorizontalAlignment(11);
        }

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            return this.renderer.getListCellRendererComponent(list, (((Integer) value).intValue() == -1) ?
                    Res.getString("auto") :
                    NumberFormat.getInstance().format(value), index, isSelected, cellHasFocus);
        }

    }

    private static class ValuePanel extends JPanel {
        private Component c1;

        private Component c2;

        public ValuePanel(Component c1, Component c2) {
            this.c1 = c1;
            this.c2 = c2;
            setLayout((LayoutManager) null);
            add(c1);
            add(c2);
        }

        public void doLayout() {
            int width = getWidth();
            int height = getHeight();
            int c2width = (this.c2.getPreferredSize()).width;
            int c2x = Math.min((this.c1.getPreferredSize()).width, width - c2width);
            this.c2.setBounds(c2x, 0, c2width, height);
            this.c1.setBounds(0, 0, c2x, height);
        }

        public Dimension getPreferredSize() {
            Dimension c1pref = this.c1.getPreferredSize();
            Dimension c2pref = this.c2.getPreferredSize();
            return new Dimension(c1pref.width + c2pref.width,
                    Math.max(c1pref.height, c2pref.height));
        }

        public Dimension getMinimumSize() {
            Dimension c1min = this.c1.getMinimumSize();
            Dimension c2min = this.c2.getMinimumSize();
            return new Dimension(c1min.width + c2min.width,
                    Math.max(c1min.height, c2min.height));
        }
    }

    private static class ValuesPanel extends JPanel {
        private int tw;

        private int[] pw;

        public ValuesPanel(Component[] components) {
            setLayout((LayoutManager) null);
            this.pw = new int[components.length];
            for (int i = 0; i < components.length; i++) {
                this.pw[i] = (components[i].getPreferredSize()).width;
                this.tw += this.pw[i];
                add(components[i]);
            }
        }

        public void doLayout() {
            int x = 0;
            for (int i = 0; i < getComponentCount(); i++) {
                Component c = getComponent(i);
                int ww = (int) (getWidth() * this.pw[i] / this.tw);
                c.setBounds(x, 0, Math.min(ww, (c.getPreferredSize()).width),
                        getHeight());
                x += ww;
            }
        }

        public Dimension getPreferredSize() {
            int prefWidth = 0;
            int prefHeight = 0;
            for (Component c : getComponents()) {
                Dimension prefC = c.getPreferredSize();
                prefWidth += prefC.width;
                prefHeight = Math.max(prefHeight, prefC.height);
            }
            return new Dimension(prefWidth, prefHeight);
        }

        public Dimension getMinimumSize() {
            int minWidth = 0;
            int minHeight = 0;
            for (Component c : getComponents()) {
                Dimension minC = c.getMinimumSize();
                minWidth += minC.width;
                minHeight = Math.max(minHeight, minC.height);
            }
            return new Dimension(minWidth, minHeight);
        }
    }

    private static class HistogramViewSupport extends JComponent {
        VisualAgeHistogram visualHistogram;

        private HistogramViewSupport() {
        }

        public DataViewComponent.DetailsView getDetailsView() {
            return new DataViewComponent.DetailsView(Res.getString("histogram"), null, 10, this, null);
        }

        void refresh(GCSample gcsample) {
            if (getComponentCount() == 0) {
                setLayout(new BorderLayout());
                if (gcsample.ageTableSizes != null) {
                    if (!ORIGINAL_UI)
                        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                    this.visualHistogram = new VisualAgeHistogram(gcsample);
                    if (!ORIGINAL_UI)
                        customizeHistogram(this.visualHistogram);
                    customizeComponents(this.visualHistogram.getContentPane(), null);
                    add(this.visualHistogram.getContentPane(), BorderLayout.CENTER);
                } else {
                    add(new NotSupportedDisplayer(NotSupportedDisplayer.JVM), BorderLayout.CENTER);
                }
                repaint();
            } else if (this.visualHistogram != null) {
                BorderLayout layout = (BorderLayout) this.getLayout();
                if (gcsample.ageTableSizes == null) {
                    if (layout.getLayoutComponent(BorderLayout.CENTER) == this.visualHistogram.getContentPane()) {
                        remove(this.visualHistogram.getContentPane());
                        add(new NotSupportedDisplayer(NotSupportedDisplayer.JVM), BorderLayout.CENTER);
                        this.revalidate();
                    }
                } else {
                    if (layout.getLayoutComponent(BorderLayout.CENTER) != this.visualHistogram.getContentPane()) {
                        System.out.println("refresh() " + layout.getLayoutComponent(BorderLayout.CENTER));

                        remove(layout.getLayoutComponent(BorderLayout.CENTER));
                        add(this.visualHistogram.getContentPane(), BorderLayout.CENTER);
                        this.revalidate();
                    }
                    this.visualHistogram.update(gcsample);
                    repaint();
                }

            }
        }

        private void customizeHistogram(VisualAgeHistogram histogram) {
            JPanel textPanel = histogram.textPanel;
            JLabel ttLabel = (JLabel) textPanel.getComponent(0);
            JLabel mttLabel = (JLabel) textPanel.getComponent(2);
            JLabel dssLabel = (JLabel) textPanel.getComponent(4);
            JLabel cssLabel = (JLabel) textPanel.getComponent(6);
            textPanel.removeAll();
            textPanel.setLayout(new BorderLayout());
            textPanel.add(new ValuesPanel(new Component[]{new ValuePanel(ttLabel, histogram.ttField), new ValuePanel(mttLabel, histogram.mttField), new ValuePanel(dssLabel, histogram.dssField), new ValuePanel(cssLabel, histogram.cssField)}), "Center");
        }
    }

//  static {
//    customizeColors();
//  }

    class PsListModel extends DefaultListModel<String> implements ActionListener {
        private final Timer timer = new Timer(3000, this);// Slower update from 2 secs to 3 secs

        public PsListModel() {
            timer.start();
        }

//    @Override
//    public int getSize() {
//      return data.size();
//    }
//
//    @Override
//    public Object getElementAt(int index) {
//      return data.get(index);
//    }
//
//    /**
//     * Empties the list.
//     */
//    public void removeAllElements() {
//      if ( data.size() > 0 ) {
//        int firstIndex = 0;
//        int lastIndex = data.size() - 1;
//        data.clear();
//        fireIntervalRemoved(this, firstIndex, lastIndex);
//      }
//    }
//
//    /**
//     * Empties the list.
//     */
//    public void refreshAllElements(List<String> ps) {
//      if ( data.size() > 0 ) {
//        int firstIndex = 0;
//        int lastIndex = data.size() - 1;
//        data.clear();
//
//        fireIntervalRemoved(this, firstIndex, lastIndex);
//        data.addAll(ps);
//        fireIntervalAdded(this, 0, data.size() - 1);
//      }
//    }

        public void start() {
            timer.start();
        }

        public void stop() {
            timer.stop();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
//      System.out.println(this.getClass() + "actionPerformed(" + e + ")");
            // Using thread to avoid UI hang when connect to a unreachable host 2022-9-18
            executor.execute(() -> {
                try {
                    List<String> ps = JpsHelper.getJvmPSList(ApplicationSettingsService.getInstance().getState().getRemoteJvmURL());
                    // 定时器触发的任务
                    super.removeAllElements();
                    for (String pid : ps) {
                        super.addElement(pid);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                    // 定时器触发的任务
                    super.removeAllElements();
                    psList.getEmptyText().setText("Failed to read JVM list:");
                    psList.getEmptyText().appendLine(exception.getMessage());
                } catch (IllegalAccessError error) {
                    System.out.println("IllegalAccessError:" + error.getMessage());
                }
            });
        }
    }

    // 主界面顶部视图
    private class MasterViewSupport extends JPanel {
        private static final String KEY_REFRESH = "VisualGC.refresh";

        // 持久化存储刷新频率信息
        private final Preferences prefs;

        private Timer timer;
        private JLabel gcPolicyLabel = new JLabel();

        public MasterViewSupport(Timer timer) {
            this.prefs = Preferences.userRoot().node(VisualGCPane.class.getName());
            this.timer = timer;
            initComponents();
        }

        void refresh(GCSample gcsample) {
//      if(gcPolicyLabel == null ) {
//
//      }
            gcPolicyLabel.setText(MessageFormat.format(Res.getString("gc.policy.is.0"), GCSample.gcPolicyName));
        }

        // init MasterViewSupport
        private void initComponents() {
            setLayout(new BorderLayout());
            setOpaque(false);
            JPanel refreshRateContainer = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 5));
            refreshRateContainer.setOpaque(false);
            refreshRateContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            JLabel refreshRateLabel = new JLabel();
            refreshRateLabel.setFont(refreshRateLabel.getFont().deriveFont(1));
            refreshRateLabel.setText(Res.getString("refresh.rate"));
//      Mnemonics.setLocalizedText(refreshRateLabel, NbBundle.getMessage(VisualGCView.class, "LBL_RefreshRate"));
            refreshRateContainer.add(refreshRateLabel);
            JLabel unitsLabel = new JLabel(Res.getString("msec"));
            Integer[] refreshRates = {-1, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 120000};
            final JComboBox<Integer> combo = new ComboBox<Integer>(refreshRates);

            refreshRateLabel.setLabelFor(combo);
            combo.setEditable(false);
            combo.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    int delay = (Integer) combo.getSelectedItem();
                    MasterViewSupport.this.prefs.putInt("VisualGC.refresh", delay);
                    if (delay == -1)
                        delay = 1000;
                    MasterViewSupport.this.timer.setDelay(delay);
                    MasterViewSupport.this.timer.restart();
                }
            });
            combo.setSelectedItem(this.prefs.getInt("VisualGC.refresh", -1));
            combo.setRenderer(new ComboRenderer(combo));
            refreshRateContainer.add(combo);
            refreshRateContainer.add(unitsLabel);

//      refreshRateContainer.add(new JButton("Switch Process"));
            refreshRateContainer.add(gcPolicyLabel);

            add(refreshRateContainer, BorderLayout.WEST);
            JButton stopButton = new JButton(Res.getString("stop.monitor"));
            stopButton.addActionListener(stopMonitorAction);
            add(stopButton, BorderLayout.EAST);
        }

    }

    class TerminationHandler
            implements HostListener {

        final int lvmid;
        final MonitoredHost host;

        TerminationHandler(int i, MonitoredHost monitoredhost) {
            lvmid = i;
            host = monitoredhost;
        }

        public void vmStatusChanged(VmStatusChangeEvent vmstatuschangeevent) {
            if (vmstatuschangeevent.getTerminated().contains(lvmid) || !vmstatuschangeevent.getActive().contains(lvmid))
                terminated = true;
        }

        public void disconnected(HostEvent hostevent) {
            if (host == hostevent.getMonitoredHost())
                terminated = true;
        }
    }

    // <<< End

    private class GraphGCViewSupport extends JComponent {
        GraphGC graphGC;

        private GraphGCViewSupport() {
        }

        public DataViewComponent.DetailsView getDetailsView() {
            return new DataViewComponent.DetailsView(Res.getString("graphs"), null, 10, this);
        }

        void refresh(GCSample gcsample) {
            if (this.graphGC == null) {
                setLayout(new BorderLayout());
                if (!ORIGINAL_UI)
                    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                this.graphGC = new GraphGC(gcsample);
                fixMetaspace(this.graphGC);
                customizeComponents(this.graphGC.getContentPane(), null);
                add(this.graphGC.getContentPane(), "Center");
            }
            this.graphGC.update(gcsample);
            repaint();
        }

        private void fixMetaspace(GraphGC graphGC) {
            if (hasMetaspace)
                try {
                    Field PERM_PANEL = GraphGC.class.getDeclaredField("permPanel");
                    Field BORDER_STRING = GCSpacePanel.class.getDeclaredField("borderString");
                    PERM_PANEL.setAccessible(true);
                    BORDER_STRING.setAccessible(true);
                    GCSpacePanel permPanel = (GCSpacePanel) PERM_PANEL.get(graphGC);
                    String borderString = (String) BORDER_STRING.get(permPanel);
                    BORDER_STRING.set(permPanel, borderString.replace(Res.getString("perm.gen"), Res.getString("metaspace")));
                } catch (NoSuchFieldException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (SecurityException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (IllegalArgumentException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (IllegalAccessException ex) {
                    Exceptions.printStackTrace(ex);
                }
        }
    }

    private class SpacesViewSupport extends JComponent {
        private VisualHeap visualHeap;

        private List<GridDrawer> gridDrawers;

        private SpacesViewSupport() {
        }

        public DataViewComponent.DetailsView getDetailsView() {
            return new DataViewComponent.DetailsView(Res.getString("spaces"), null, 10, this, null);
        }

        void refresh(GCSample gcsample, GraphGCViewSupport gvs, HistogramViewSupport hvs) {
            if (this.visualHeap == null) {
                setLayout(new BorderLayout());
                if (!ORIGINAL_UI)
                    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                this.visualHeap = new VisualHeap(gvs.graphGC, hvs.visualHistogram, gcsample) {
                    public void updateLevel(GCSample sample) {
                        super.updateLevel(sample);
                        // 统一将所有的垂直柱图绘制单元格的颜色都改亮了
                        for (GridDrawer gridDrawer : SpacesViewSupport.this.gridDrawers) {
                            gridDrawer.setSecondaryColor(NORMAL_GRAY_ALPHA);
                        }

                    }

                    public void updateTextFields(GCSample gcSample) {
                        super.updateTextFields(gcSample);
                        customizeVisualHeap(this);
                    }
                };
                fixMetaspace(this.visualHeap);
                this.gridDrawers = new ArrayList<GridDrawer>();
                customizeComponents(this.visualHeap.getContentPane(), this.gridDrawers);
//        this.visualHeap.getContentPane().getComponent(0).setVisible(false);
                add(this.visualHeap.getContentPane(), "Center");
            }
            this.visualHeap.update(gcsample);
            repaint();
        }

        private void fixMetaspace(VisualHeap visualHeap) {
            if (hasMetaspace)
                try {
                    Field PERM_PANEL = VisualHeap.class.getDeclaredField("permPanel");
                    PERM_PANEL.setAccessible(true);
                    JPanel permPanel = (JPanel) PERM_PANEL.get(visualHeap);
                    TitledBorder border = (TitledBorder) permPanel.getBorder();
                    border.setTitle(Res.getString("metaspace"));
                } catch (NoSuchFieldException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (SecurityException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (IllegalArgumentException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (IllegalAccessException ex) {
                    Exceptions.printStackTrace(ex);
                }
        }
    }

    // 进程列表视图
    private class PSListViewSupport extends JPanel {
        private final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

        private JBLabel remoteHostLabel = new JBLabel();

        public PSListViewSupport() {
            initComponents();
            update();
        }

        public void update() {
            PluginSettings settings = ApplicationSettingsService.getInstance().getState();
            if(StringUtil.isEmpty(settings.getRemoteJvmURL())) {
                remoteHostLabel.setText("Host: local");
            } else {
                remoteHostLabel.setText("Host: " + settings.getRemoteJvmURL());
            }
        }

        private boolean isIPv4Address(String name) {
            Matcher m = IPV4_PATTERN.matcher(name);
            if (m.matches() && m.groupCount() == 4) {
                for (int i = 1; i <= 4; ++i) {
                    String ipSegment = m.group(i);
                    int iIpSegment = Integer.parseInt(ipSegment);
                    if (iIpSegment > 255) {
                        return false;
                    }

                    if (ipSegment.length() > 1 && ipSegment.startsWith("0")) {
                        return false;
                    }
                }

                return true;
            } else {
                return false;
            }
        }

        // init MasterViewSupport
        private void initComponents() {
            setLayout(new BorderLayout());
            setOpaque(false);
            JBPanel remoteJvmPane = new JBPanel(new FlowLayout(FlowLayout.LEADING, 5, 5));
            remoteJvmPane.setOpaque(false);
            remoteJvmPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

            remoteJvmPane.add(remoteHostLabel);

            JButton stopButton = new JButton(Res.getString("stop.monitor"));
            stopButton.setText(Res.getString("connect.remote.jvm"));
            try {
                HelpTooltip helpTooltip = new HelpTooltip()
                        .setTitle(Res.getString("remote.jvm.help.title"))
                        .setDescription(Res.getString("remote.jvm.help.description"))
                        .setLink(Res.getString("remote.more.info"), () -> {
                            try {
                                Desktop.getDesktop().browse(new URI("https://plugins.jetbrains.com/plugin/14557-visualgc/remote-jvm"));
                            } catch (IOException | URISyntaxException ioException) {
                                ioException.printStackTrace();
                            }
                        });
                helpTooltip.installOn(stopButton);
            } catch (Exception e) {
            }
            PluginSettings settings = ApplicationSettingsService.getInstance().getState();
            stopButton.addActionListener(e -> {
                String newHost = Messages.showInputDialog(myProject,
                        Res.getString("connect.remote.jvm.message"),
                        Res.getString("connect.remote.jvm"),
                        Messages.getQuestionIcon(),
                        settings.getRemoteJvmURL(),
                        new InputValidator() {
                            @Override
                            public boolean checkInput(String input) {
//                                if (input.trim().length() > 0) {
//                                    //return isIPv4Address(input);
//                                }

                                return true;
                            }

                            @Override
                            public boolean canClose(String inputString) {
//                      try {
//                        JpsHelper.getJvmPSList(inputString);
//                        settings.setRemoteJvmURL(inputString);
//                        return true;
//                      } catch (Exception exception) {
//                        Messages.showErrorDialog("Failed to connect to remote JVM:\n" + exception.getMessage(), "Error");
//                        exception.printStackTrace();
//                      }
//                      return false;
                                if (checkInput(inputString)) {
                                    settings.setRemoteJvmURL(inputString.trim());
                                    update();
                                    return true;
                                }

                                return false;
                            }
                        }
                );


            });

            remoteJvmPane.add(stopButton);
//            remoteJvmPane.add(ContextHelpLabel.createWithLink("Help", "More info", "Link", () -> {
//                            try {
//                                Desktop.getDesktop().browse(new URI("https://github.com/beansoft/visualgc_java8/tree/master/visualgc_idea"));
//                            } catch (IOException | URISyntaxException ioException) {
//                                ioException.printStackTrace();
//                            }
//                        }));
            add(new JBScrollPane(remoteJvmPane), BorderLayout.NORTH);

            add(new JBScrollPane(psList), BorderLayout.CENTER);

            // Using thread to avoid UI hang when connect to a unreachable host 2022-9-18
            executor.execute(() -> {
                try {
                    List<String> ps = JpsHelper.getJvmPSList(ApplicationSettingsService.getInstance().getState().getRemoteJvmURL());
                } catch (Exception exception) {
                    exception.printStackTrace();
                } catch (IllegalAccessError error) {
                    System.out.println("IllegalAccessError:" + error.getMessage());
                    if (error.getMessage().contains("sun.jvmstat.monitor.HostIdentifier")) {
                        HyperlinkLabel createdByLink = new HyperlinkLabel(Res.getString("link.label.error.fetch.jvm.list.please.add.following.lines.to.opened.editor"));
                        createdByLink.addHyperlinkListener((e) -> {
                            JBTextField textField = new JBTextField(
                                    "--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED\n" +
                                    "--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor.event=ALL-UNNAMED\n" +
                                    "--add-exports=jdk.internal.jvmstat/sun.jvmstat.perfdata.monitor=ALL-UNNAMED");

                            Messages.showTextAreaDialog(textField, Res.getString("dialog.title.please.edit.file.restart.ide"), "visualgc.input");
                            AnAction action = ActionManager.getInstance().getAction("EditCustomVmOptions");
                            if(action != null) {
                                action.actionPerformed(
                                        AnActionEvent.createFromDataContext(ActionPlaces.MAIN_MENU, action.getTemplatePresentation(),
                                                dataId -> {
                                                    if (PlatformDataKeys.PROJECT.is(dataId)) {
                                                        return DefaultProjectFactory.getInstance().getDefaultProject();
                                                    }
                                                    return null;
                                                }));
                            }

                        });

                        try {
                            HelpTooltip helpTooltip = new HelpTooltip()
                                    .setTitle(Res.getString("tooltip.title.unable.to.access.jdk.jvmstat.classes"))
                                    .setDescription("To make the VisualGC work, you must change the JVM option file and add the following lines to it:\n" +
                                            "--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED\n" +
                                            "--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor.event=ALL-UNNAMED\n" +
                                            "--add-exports=jdk.internal.jvmstat/sun.jvmstat.perfdata.monitor=ALL-UNNAMED")
                                    .setLink(Res.getString("link.label.more.info"), () -> {
                                        try {
                                            Desktop.getDesktop().browse(new URI("https://github.com/beansoft/visualgc_java8/tree/master/visualgc_idea"));
                                        } catch (IOException | URISyntaxException ioException) {
                                            ioException.printStackTrace();
                                        }
                                    });
                            helpTooltip.installOn(createdByLink);
                        } catch (Exception e) {
                        }

                        add(new JBScrollPane(createdByLink), BorderLayout.SOUTH);
                    }
                }
            });

        }

    }

}
