/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.snap.gui.util;

import com.bc.ceres.core.Assert;
import org.esa.snap.gui.actions.window.NewWorkspaceAction;
import org.netbeans.swing.tabcontrol.DefaultTabDataModel;
import org.netbeans.swing.tabcontrol.TabData;
import org.netbeans.swing.tabcontrol.TabDisplayer;
import org.netbeans.swing.tabcontrol.TabbedContainer;
import org.netbeans.swing.tabcontrol.WinsysInfoForTabbedContainer;
import org.netbeans.swing.tabcontrol.event.TabActionEvent;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.UndoRedo;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.openide.util.NbBundle.Messages;

/**
 * A top-level window which hosts an internal desktop in which other windows can be floated as internal frames.
 *
 * @author Norman
 */
@TopComponent.Description(
        preferredID = "WorkspaceTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED
)
@TopComponent.Registration(
        mode = "editor",
        openAtStartup = true)
@ActionID(category = "Window", id = "org.esa.snap.gui.window.WorkspaceTopComponent")
@ActionReference(path = "Menu/Window/Tool Windows", position = 0)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_WorkspaceTopComponentNameBase",
        preferredID = "WorkspaceTopComponent"
)
@Messages({
                  "CTL_WorkspaceTopComponentNameBase=Workspace",
                  "CTL_WorkspaceTopComponentDescription=Provides an internal desktop for document windows",
          })
public class WorkspaceTopComponent extends TopComponent {

    public final static String ID = WorkspaceTopComponent.class.getSimpleName();

    private static final Logger LOG = Logger.getLogger(WorkspaceTopComponent.class.getName());

    private final Map<TabData, JInternalFrame> tabToFrameMap;
    private final Map<JInternalFrame, TabData> frameToTabMap;
    private final Map<Object, Rectangle> idToBoundsMap;
    private final ActionListener tabActionListener;
    private final InternalFrameListener internalFrameListener;
    private final PropertyChangeListener propertyChangeListener;
    private final FrameProxyLookup lookup;

    private TabbedContainer tabbedContainer;
    private JDesktopPane desktopPane;

    public WorkspaceTopComponent() {
        this(Bundle.CTL_WorkspaceTopComponentNameBase());
    }

    public WorkspaceTopComponent(String displayName) {
        frameToTabMap = new HashMap<>();
        tabToFrameMap = new HashMap<>();
        idToBoundsMap = new HashMap<>();
        tabActionListener = new TabActionListener();
        internalFrameListener = new MyInternalFrameListener();
        propertyChangeListener = new MyPropertyChangeListener();
        lookup = new FrameProxyLookup();
        associateLookup(lookup);
        initComponents();
        setName(displayName);
        setDisplayName(displayName);
        setToolTipText(Bundle.CTL_WorkspaceTopComponentDescription());
        //putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.TRUE);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        DefaultTabDataModel tabDataModel = new DefaultTabDataModel();
        tabbedContainer = new TabbedContainer(tabDataModel,
                                              TabbedContainer.TYPE_EDITOR,
                                              WinsysInfoForTabbedContainer.getDefault(new MyWinsysInfoForTabbedContainer()));
        tabbedContainer.setVisible(false);

        desktopPane = new JDesktopPane();

        add(tabbedContainer, BorderLayout.NORTH);
        add(desktopPane, BorderLayout.CENTER);
    }

    @Override
    public UndoRedo getUndoRedo() {
        TopComponent topComponent = getActiveTopComponent();
        if (topComponent != null) {
            return topComponent.getUndoRedo();
        }
        return super.getUndoRedo();
    }

    /**
     * @return The currently active window.
     */
    public TopComponent getActiveTopComponent() {
        JInternalFrame selectedFrame = desktopPane.getSelectedFrame();
        return selectedFrame != null ? getTopComponent(selectedFrame) : null;
    }

    /**
     * Gets all windows contained in this one.
     *
     * @return The list of all windows which may be empty.
     */
    public List<TopComponent> getTopComponents() {
        List<TabData> tabs = tabbedContainer.getModel().getTabs();
        List<TopComponent> topComponents = new ArrayList<>();
        for (TabData tab : tabs) {
            JInternalFrame internalFrame = tabToFrameMap.get(tab);
            topComponents.add(getTopComponent(internalFrame));
        }
        return topComponents;
    }

    /**
     * Adds a window to this workspace window. The method actually "floats" the window as an internal frame into an
     * internal desktop pane. If the window already exists, it's internal frame will be activated.
     *
     * @param topComponent The window to add.
     */
    @Messages("CTL_WorkspaceTopComponentFrameUnnamed=Unnamed")
    public void addTopComponent(TopComponent topComponent) {

        // If the window already exists, activate it and return.
        List<TabData> tabs = tabbedContainer.getModel().getTabs();
        for (TabData tab : tabs) {
            JInternalFrame internalFrame = tabToFrameMap.get(tab);
            if (topComponent == getTopComponent(internalFrame)) {
                try {
                    internalFrame.setSelected(true);
                } catch (PropertyVetoException e) {
                    // ok
                }
                return;
            }
        }

        // Make sure, topComponent is closed and not any longer controlled by NB's WindowManager
        if (topComponent.isOpened()) {
            topComponent.close();
        }

        String displayName = topComponent.getDisplayName();
        if (displayName == null) {
            displayName = Bundle.CTL_WorkspaceTopComponentFrameUnnamed();
        }

        JInternalFrame internalFrame = new JInternalFrame(displayName, true, true, true, true);
        Image iconImage = topComponent.getIcon();
        ImageIcon imageIcon = null;
        if (iconImage != null) {
            imageIcon = new ImageIcon(iconImage);
            internalFrame.setFrameIcon(imageIcon);
        }

        // Note: The following dummyComponent with preferred size (-1, 4) allows for using the tabbedContainer as
        // a *thin*, empty tabbed bar on top of the desktopPane.
        JComponent dummyComponent = new JPanel();
        dummyComponent.setPreferredSize(new Dimension(-1, 4));
        //dummyComponent.setSize(new Dimension(-1, 4));
        TabData tabData = new TabData(dummyComponent, imageIcon, displayName, null);

        frameToTabMap.put(internalFrame, tabData);
        tabToFrameMap.put(tabData, internalFrame);

        internalFrame.setContentPane(topComponent);

        Object internalFrameID = getInternalFrameID(topComponent);
        Rectangle bounds = idToBoundsMap.get(internalFrameID);
        if (bounds == null) {
            int count = frameToTabMap.size() % 5;
            bounds = new Rectangle(count * 24, count * 24, 400, 400);
        }
        internalFrame.setBounds(bounds);

        tabbedContainer.getModel().addTab(tabbedContainer.getModel().size(), tabData);
        tabbedContainer.setVisible(true);
        desktopPane.add(internalFrame);

        internalFrame.addInternalFrameListener(internalFrameListener);

        internalFrame.setVisible(true);
        try {
            internalFrame.setSelected(true);
        } catch (PropertyVetoException e) {
            // ok
        }

        topComponent.addPropertyChangeListener(propertyChangeListener);
    }

    /**
     * Gets extra actions, e.g. for floating a docked window or group into a workspace.
     *
     * @param topComponent The document window.
     * @return The extra actions.
     */
    public static Action[] getExtraActions(TopComponent topComponent) {
        return new Action[]{
                new FloatIntoWorkspaceAction(topComponent),
                new FloatGroupIntoWorkspaceAction(topComponent)
        };
    }

    /**
     * Adds various "tile window" actions to the base class' set of actions.
     *
     * @return Array of actions for this component.
     */
    @Override
    public Action[] getActions() {
        Action[] actions = super.getActions();
        ArrayList<Action> actionList = new ArrayList<>();
        actionList.add(new NewWorkspaceAction());
        actionList.add(new RenameWorkspaceAction());
        if (actions.length > 0) {
            actionList.add(null);
            actionList.addAll(Arrays.asList(actions));
        }
        if (tabbedContainer.getTabCount() > 0) {
            actionList.add(null);
            actionList.add(new TileEvenlyAction());
            actionList.add(new TileHorizontallyAction());
            actionList.add(new TileVerticallyAction());
        }
        return actionList.toArray(new Action[actionList.size()]);
    }

    // CHECKME: How does NB Platform use this method? What is its use?
    // See https://netbeans.org/bugzilla/show_bug.cgi?id=209051
    @Override
    public SubComponent[] getSubComponents() {
        Map<Object, JInternalFrame> internalFrameMap = new HashMap<>();
        SubComponent[] subComponents = new SubComponent[tabbedContainer.getTabCount()];
        ActionListener activator = actionEvent -> {
            JInternalFrame internalFrame = internalFrameMap.get(actionEvent.getSource());
            try {
                internalFrame.setSelected(true);
            } catch (PropertyVetoException e1) {
                // ok
            }
            internalFrame.requestFocusInWindow();
        };
        for (int i = 0; i < subComponents.length; i++) {
            TabData tab = tabbedContainer.getModel().getTab(i);
            JInternalFrame internalFrame = tabToFrameMap.get(tab);
            SubComponent subComponent = new SubComponent(internalFrame.getTitle(),
                                                         internalFrame.getToolTipText(),
                                                         activator,
                                                         internalFrame.isSelected(),
                                                         getTopComponent(internalFrame).getLookup(),
                                                         internalFrame.isShowing());
            internalFrameMap.put(subComponent, internalFrame);
            subComponents[i] = subComponent;
        }
        return subComponents;
    }

    @Override
    protected void componentOpened() {
        tabbedContainer.addActionListener(tabActionListener);
    }

    @Override
    protected void componentClosed() {
        tabbedContainer.removeActionListener(tabActionListener);
    }

    @Override
    protected void componentActivated() {
        JInternalFrame internalFrame;
        // Make sure that activation states of tabbedContainer and desktopPane are synchronized
        int tabIndex = tabbedContainer.getSelectionModel().getSelectedIndex();
        if (tabIndex >= 0) {
            TabData tab = tabbedContainer.getModel().getTab(tabIndex);
            internalFrame = tabToFrameMap.get(tab);
            if (!internalFrame.isSelected()) {
                try {
                    internalFrame.setSelected(true);
                } catch (PropertyVetoException e) {
                    // ok
                }
            }
        } else {
            internalFrame = desktopPane.getSelectedFrame();
            if (internalFrame != null) {
                TabData tab = frameToTabMap.get(internalFrame);
                tabIndex = tabbedContainer.getModel().indexOf(tab);
                if (tabIndex >= 0) {
                    tabbedContainer.getSelectionModel().setSelectedIndex(tabIndex);
                }
            }
        }
        if (internalFrame != null) {
            internalFrame.requestFocusInWindow();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        //  store your settings
    }

    @SuppressWarnings("UnusedDeclaration")
    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // read your settings according to their version
    }

    private TopComponent closeInternalFrame(JInternalFrame internalFrame) {
        return closeInternalFrame(internalFrame, true);
    }

    private TopComponent closeInternalFrame(JInternalFrame internalFrame, boolean removeTab) {
        internalFrame.removeInternalFrameListener(internalFrameListener);
        TopComponent topComponent = getTopComponent(internalFrame);
        topComponent.removePropertyChangeListener(propertyChangeListener);

        Object internalFrameID = getInternalFrameID(topComponent);
        idToBoundsMap.put(internalFrameID, new Rectangle(internalFrame.getBounds()));

        TabData tabData = frameToTabMap.get(internalFrame);
        if (tabData != null) {
            if (removeTab) {
                int tabIndex = tabbedContainer.getModel().indexOf(tabData);
                if (tabIndex >= 0) {
                    tabbedContainer.getModel().removeTab(tabIndex);
                }
            }
            tabToFrameMap.remove(tabData);
        }
        frameToTabMap.remove(internalFrame);

        internalFrame.dispose();
        desktopPane.remove(internalFrame);
        if (desktopPane.getComponentCount() == 0) {
            tabbedContainer.setVisible(false);
        }

        // make sure the topComponent's parent is not the internalFrame which we just closed
        internalFrame.setContentPane(new TopComponent());

        return topComponent;
    }

    private TopComponent dockInternalFrame(JInternalFrame internalFrame) {
        TopComponent topComponent = closeInternalFrame(internalFrame, true);

        Mode mode = WindowManager.getDefault().findMode("editor");
        mode.dockInto(topComponent);
        if (!topComponent.isOpened()) {
            topComponent.open();
        }

        return topComponent;
    }

    private TopComponent getTopComponent(JInternalFrame internalFrame) {
        return (TopComponent) internalFrame.getContentPane();
    }

    private JInternalFrame getInternalFrame(TopComponent topComponent) {
        for (JInternalFrame internalFrame : frameToTabMap.keySet()) {
            if (topComponent == getTopComponent(internalFrame)) {
                return internalFrame;
            }
        }
        return null;
    }

    private JInternalFrame getInternalFrame(int tabIndex) {
        return tabToFrameMap.get(tabbedContainer.getModel().getTab(tabIndex));
    }

    private Object getInternalFrameID(TopComponent topComponent) {
        Object internalFrameID = topComponent.getClientProperty("internalFrameID");
        if (internalFrameID == null) {
            internalFrameID = "IF" + Long.toHexString(new Random().nextLong());
            topComponent.putClientProperty("internalFrameID", internalFrameID);
        }
        return internalFrameID;
    }

    /**
     * Used to listen to actions invoked on the tabbedContainer
     */
    private class TabActionListener implements ActionListener {
        private final Map<String, Action> tabActions;

        private TabActionListener() {
            tabActions = new HashMap<>();
            initTabActions();
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Action action = tabActions.get(actionEvent.getActionCommand());
            if (action != null) {
                action.actionPerformed(actionEvent);
            }
        }

        private void initTabActions() {
            addTabAction(new TabAction("close") {
                @Override
                public void tabActionPerformed(TabActionEvent actionEvent) {
                    // Note: the tab UI is already removed, but the tabData is still in the model. NetBeans will remove it later.
                    int tabIndex = actionEvent.getTabIndex();
                    JInternalFrame internalFrame = getInternalFrame(tabIndex);
                    if (internalFrame != null) {
                        closeInternalFrame(internalFrame, false);
                    }
                }
            });
            addTabAction(new TabAction("select") {
                @Override
                public void tabActionPerformed(TabActionEvent actionEvent) {
                    int tabIndex = actionEvent.getTabIndex();
                    JInternalFrame internalFrame = getInternalFrame(tabIndex);
                    if (internalFrame != null) {
                        try {
                            if (internalFrame.isIcon()) {
                                internalFrame.setIcon(false);
                            }
                            internalFrame.setSelected(true);
                        } catch (PropertyVetoException e) {
                            // ok
                        }
                    }
                }
            });
            addTabAction(new TabAction("maximize") {
                @Override
                public void tabActionPerformed(TabActionEvent actionEvent) {
                    int tabIndex = actionEvent.getTabIndex();
                    new MaximizeWindowAction(tabIndex).actionPerformed(actionEvent);
                }
            });
            addTabAction(new TabAction("popup") {
                @Override
                public void tabActionPerformed(TabActionEvent actionEvent) {

                    int tabCount = tabbedContainer.getTabCount();
                    if (tabCount == 0) {
                        return;
                    }

                    int tabIndex = actionEvent.getTabIndex();
                    //System.out.println("tabIndex = " + tabIndex);

                    JPopupMenu popupMenu = new JPopupMenu();
                    if (tabIndex >= 0) {
                        popupMenu.add(new CloseWindowAction(tabIndex));
                    }
                    if (tabCount > 1) {
                        popupMenu.add(new CloseAllWindowsAction());
                    }
                    if (tabIndex >= 0 && tabCount > 1) {
                        popupMenu.add(new CloseOtherWindowsAction(tabIndex));
                    }
                    if (tabIndex >= 0 || tabCount > 1) {
                        popupMenu.addSeparator();
                        if (tabIndex >= 0) {
                            popupMenu.add(new MaximizeWindowAction(tabIndex));
                            popupMenu.add(new DockAction(tabIndex));
                        }
                        if (tabCount > 1) {
                            popupMenu.add(new DockGroupAction());
                        }
                    }
                    //if (tabIndex >= 0) {
                    //    popupMenu.addSeparator();
                    //    popupMenu.add(new CloneWindowAction(tabIndex));
                    //}
                    if (tabCount > 1) {
                        popupMenu.addSeparator();
                        popupMenu.add(new TileEvenlyAction());
                        popupMenu.add(new TileHorizontallyAction());
                        popupMenu.add(new TileVerticallyAction());
                    }
                    popupMenu.show(tabbedContainer, actionEvent.getMouseEvent().getX(), actionEvent.getMouseEvent().getY());
                }
            });
        }

        private void addTabAction(Action action) {
            tabActions.put((String) action.getValue(Action.ACTION_COMMAND_KEY), action);
        }

    }

    private abstract class TabAction extends AbstractAction {
        private TabAction(String name) {
            super(name);
            putValue(Action.ACTION_COMMAND_KEY, name);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e instanceof TabActionEvent) {
                tabActionPerformed((TabActionEvent) e);
            }
        }

        public abstract void tabActionPerformed(TabActionEvent e);
    }

    @Messages("CTL_RenameActionName=Rename")
    private class RenameWorkspaceAction extends AbstractAction {

        public RenameWorkspaceAction() {
            super(Bundle.CTL_RenameActionName());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            NotifyDescriptor.InputLine d = new NotifyDescriptor.InputLine("Name:", "Rename Workspace");
            d.setInputText(WorkspaceTopComponent.this.getDisplayName());
            Object result = DialogDisplayer.getDefault().notify(d);
            if (NotifyDescriptor.OK_OPTION.equals(result)) {
                WorkspaceTopComponent.this.setDisplayName(d.getInputText());
            }
        }
    }

    @Messages("CTL_CloseWindowActionName=Close")
    private class CloseWindowAction extends AbstractAction {
        private final int tabIndex;

        public CloseWindowAction(int tabIndex) {
            super(Bundle.CTL_CloseWindowActionName());
            this.tabIndex = tabIndex;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            TabData tab = tabbedContainer.getModel().getTab(tabIndex);
            JInternalFrame internalFrame = tabToFrameMap.get(tab);
            closeInternalFrame(internalFrame);
        }
    }

    @Messages("CTL_CloseAllWindowsActionName=Close All")
    private class CloseAllWindowsAction extends AbstractAction {

        public CloseAllWindowsAction() {
            super(Bundle.CTL_CloseAllWindowsActionName());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            new CloseOtherWindowsAction(-1).actionPerformed(e);
        }
    }

    @Messages("CTL_CloseOtherWindowsActionName=Close Others")
    private class CloseOtherWindowsAction extends AbstractAction {
        private final int tabIndex;

        public CloseOtherWindowsAction(int tabIndex) {
            super(Bundle.CTL_CloseOtherWindowsActionName());
            this.tabIndex = tabIndex;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            TabData tab = tabbedContainer.getModel().getTab(tabIndex);
            JInternalFrame selectedInternalFrame = tabToFrameMap.get(tab);
            Set<JInternalFrame> internalFrameSet = frameToTabMap.keySet();
            JInternalFrame[] internalFrames = internalFrameSet.toArray(new JInternalFrame[internalFrameSet.size()]);
            for (JInternalFrame internalFrame : internalFrames) {
                if (internalFrame != selectedInternalFrame) {
                    closeInternalFrame(internalFrame);
                }
            }
        }
    }

    @Messages("CTL_MaximizeWindowActionName=Maximise")
    private class MaximizeWindowAction extends AbstractAction {
        private final int tabIndex;

        public MaximizeWindowAction(int tabIndex) {
            super(Bundle.CTL_MaximizeWindowActionName());
            this.tabIndex = tabIndex;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            TabData tab = tabbedContainer.getModel().getTab(tabIndex);
            JInternalFrame internalFrame = tabToFrameMap.get(tab);
            try {
                internalFrame.setMaximum(true);
            } catch (PropertyVetoException e1) {
                // ok
            }
        }
    }

    @Messages("CTL_TileEvenlyActionName=Tile Evenly")
    private class TileEvenlyAction extends AbstractAction {
        public TileEvenlyAction() {
            super(Bundle.CTL_TileEvenlyActionName());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int desktopWidth = desktopPane.getWidth();
            int desktopHeight = desktopPane.getHeight();
            int windowCount = frameToTabMap.size();

            double bestDeltaValue = Double.POSITIVE_INFINITY;
            int bestHorCount = -1;
            int bestVerCount = -1;
            for (int verCount = 1; verCount <= windowCount; verCount++) {
                for (int horCount = 1; horCount <= windowCount; horCount++) {
                    if (horCount * verCount >= windowCount && horCount * verCount <= 2 * windowCount) {
                        double deltaRatio = Math.abs(1.0 - verCount / (double) horCount);
                        double deltaCount = Math.abs(1.0 - (horCount * verCount) / ((double) windowCount));
                        double deltaValue = deltaRatio + deltaCount;
                        if (deltaValue < bestDeltaValue) {
                            bestDeltaValue = deltaValue;
                            bestHorCount = horCount;
                            bestVerCount = verCount;
                        }
                    }
                }
            }

            int windowWidth = desktopWidth / bestHorCount;
            int windowHeight = desktopHeight / bestVerCount;

            List<TabData> tabs = tabbedContainer.getModel().getTabs();
            int windowIndex = 0;
            for (int j = 0; j < bestVerCount; j++) {
                for (int i = 0; i < bestHorCount; i++) {
                    if (windowIndex < windowCount) {
                        TabData tabData = tabs.get(windowIndex);
                        JInternalFrame internalFrame = tabToFrameMap.get(tabData);
                        internalFrame.setBounds(i * windowWidth, j * windowHeight, windowWidth, windowHeight);
                    }
                    windowIndex++;
                }
            }
        }
    }

    @Messages("CTL_TileHorizontallyActionName=Tile Horizontally")
    private class TileHorizontallyAction extends AbstractAction {
        public TileHorizontallyAction() {
            super(Bundle.CTL_TileHorizontallyActionName());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int desktopWidth = desktopPane.getWidth();
            int desktopHeight = desktopPane.getHeight();
            int windowCount = frameToTabMap.size();
            int windowWidth = desktopWidth / windowCount;
            List<TabData> tabs = tabbedContainer.getModel().getTabs();
            for (int windowIndex = 0; windowIndex < windowCount; windowIndex++) {
                TabData tabData = tabs.get(windowIndex);
                JInternalFrame internalFrame = tabToFrameMap.get(tabData);
                internalFrame.setBounds(windowIndex * windowWidth, 0, windowWidth, desktopHeight);
            }
        }
    }

    @Messages("CTL_TileVerticallyActionName=Tile Vertically")
    private class TileVerticallyAction extends AbstractAction {
        public TileVerticallyAction() {
            super(Bundle.CTL_TileVerticallyActionName());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int desktopWidth = desktopPane.getWidth();
            int desktopHeight = desktopPane.getHeight();
            int windowCount = frameToTabMap.size();
            int windowHeight = desktopHeight / windowCount;
            List<TabData> tabs = tabbedContainer.getModel().getTabs();
            for (int windowIndex = 0; windowIndex < windowCount; windowIndex++) {
                TabData tabData = tabs.get(windowIndex);
                JInternalFrame internalFrame = tabToFrameMap.get(tabData);
                internalFrame.setBounds(0, windowIndex * windowHeight, desktopWidth, windowHeight);
            }
        }
    }

    @Messages("CTL_DockActionName=Dock")
    private class DockAction extends AbstractAction {
        private int tabIndex;

        public DockAction(int tabIndex) {
            super(Bundle.CTL_DockActionName());
            this.tabIndex = tabIndex;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            TabData tabData = tabbedContainer.getModel().getTab(tabIndex);
            JInternalFrame internalFrame = tabToFrameMap.get(tabData);
            TopComponent topComponent = dockInternalFrame(internalFrame);
            if (topComponent != null) {
                topComponent.requestActive();
            }
        }
    }

    @Messages("CTL_DockGroupActionName=Dock Group")
    private class DockGroupAction extends AbstractAction {
        public DockGroupAction() {
            super(Bundle.CTL_DockGroupActionName());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Set<JInternalFrame> internalFrameSet = frameToTabMap.keySet();
            JInternalFrame[] internalFrames = internalFrameSet.toArray(new JInternalFrame[internalFrameSet.size()]);
            TopComponent topComponent = null;
            for (JInternalFrame internalFrame : internalFrames) {
                topComponent = dockInternalFrame(internalFrame);
            }
            if (topComponent != null) {
                topComponent.requestActive();
            }
        }
    }

    @Messages({
                      "CTL_FloatIntoWorkspaceActionName=Float into Workspace",
                      "LBL_FloatIntoWorkspaceActionName=Workspaces:",
                      "CTL_FloatIntoWorkspaceActionTitle=Select Workspace",
              })
    public static class FloatIntoWorkspaceAction extends AbstractAction {
        private TopComponent window;

        public FloatIntoWorkspaceAction(TopComponent window) {
            super(Bundle.CTL_FloatIntoWorkspaceActionName());
            this.window = window;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            WorkspaceTopComponent workspaceTopComponent = promptForWorkspaces();
            if (workspaceTopComponent != null) {
                workspaceTopComponent.requestActive();
                workspaceTopComponent.addTopComponent(window);
            }
        }

        static WorkspaceTopComponent promptForWorkspaces() {
            List<WorkspaceTopComponent> workspaces = WindowUtilities.findOpen(WorkspaceTopComponent.class);
            WorkspaceTopComponent workspaceTopComponent = null;
            if (workspaces.size() == 1) {
                workspaceTopComponent = workspaces.get(0);
            } else if (workspaces.size() > 1) {
                List<String> displayNames = workspaces.stream()
                        .map(WorkspaceTopComponent::getDisplayName)
                        .collect(Collectors.toList());
                JPanel panel = new JPanel(new BorderLayout(2, 2));
                panel.setBorder(new EmptyBorder(8, 8, 8, 8));
                panel.add(new JLabel(Bundle.LBL_FloatIntoWorkspaceActionName()), BorderLayout.NORTH);
                JList<Object> listComponent = new JList<>(new Vector<>(displayNames));
                listComponent.setVisibleRowCount(6);
                listComponent.setSelectedIndex(0);
                panel.add(new JScrollPane(listComponent));
                DialogDescriptor dd = new DialogDescriptor(panel, Bundle.CTL_FloatIntoWorkspaceActionTitle());
                DialogDisplayer.getDefault().createDialog(dd).setVisible(true);
                Object result = dd.getValue();
                if (DialogDescriptor.OK_OPTION.equals(result)) {
                    int selectedIndex = listComponent.getSelectedIndex();
                    if (selectedIndex < 0) {
                        selectedIndex = 0;
                    }
                    workspaceTopComponent = workspaces.get(selectedIndex);
                }
            } else {
                TopComponent topComponent = WindowManager.getDefault().findTopComponent(ID);
                if (topComponent instanceof WorkspaceTopComponent) {
                    workspaceTopComponent = (WorkspaceTopComponent) topComponent;
                    workspaceTopComponent.open();
                }
            }
            return workspaceTopComponent;
        }
    }

    @Messages("CTL_FloatGroupIntoWorkspaceActionName=Float Group into Workspace")
    public static class FloatGroupIntoWorkspaceAction extends AbstractAction {
        private TopComponent window;

        public FloatGroupIntoWorkspaceAction(TopComponent window) {
            super(Bundle.CTL_FloatGroupIntoWorkspaceActionName());
            this.window = window;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            WorkspaceTopComponent workspaceTopComponent = FloatIntoWorkspaceAction.promptForWorkspaces();
            if (workspaceTopComponent != null) {
                workspaceTopComponent.requestActive();
                Mode mode = WindowManager.getDefault().findMode(window);
                if (mode != null) {
                    TopComponent[] topComponents = WindowManager.getDefault().getOpenedTopComponents(mode);
                    for (TopComponent topComponent : topComponents) {
                        if (!(topComponent instanceof WorkspaceTopComponent)) {
                            workspaceTopComponent.addTopComponent(topComponent);
                        }
                    }
                }
            }
        }
    }

    private static class FrameProxyLookup extends ProxyLookup {
        void setLookup(Lookup lookup) {
            setLookups(lookup);
        }
    }

    private class MyInternalFrameListener implements InternalFrameListener {
        @Override
        public void internalFrameOpened(InternalFrameEvent e) {
            LOG.info("internalFrameOpened: e = " + e);

            tabbedContainer.updateUI();

            DocumentTopComponent dw = getDocumentWindow(e);
            if (dw != null) {
                dw.componentOpened();
            }
        }

        @Override
        public void internalFrameClosing(InternalFrameEvent e) {
            LOG.info("internalFrameClosing: e = " + e);
            // do nothing
        }

        @Override
        public void internalFrameClosed(InternalFrameEvent e) {
            LOG.info("internalFrameClosed: e = " + e);
            JInternalFrame internalFrame = e.getInternalFrame();
            if (frameToTabMap.containsKey(internalFrame)) {
                closeInternalFrame(internalFrame);
            }
            tabbedContainer.updateUI();

            DocumentTopComponent dw = getDocumentWindow(e);
            if (dw != null) {
                dw.componentClosed();
            }
        }

        @Override
        public void internalFrameActivated(InternalFrameEvent e) {
            LOG.info("internalFrameActivated: e = " + e);

            // Synchronise tab selection state, if not already done
            JInternalFrame internalFrame = e.getInternalFrame();
            TabData selectedTab = frameToTabMap.get(internalFrame);
            int selectedTabIndex = tabbedContainer.getSelectionModel().getSelectedIndex();
            List<TabData> tabs = tabbedContainer.getModel().getTabs();
            for (int i = 0; i < tabs.size(); i++) {
                TabData tab = tabs.get(i);
                if (tab == selectedTab && selectedTabIndex != i) {
                    tabbedContainer.getSelectionModel().setSelectedIndex(i);
                    break;
                }
            }
            tabbedContainer.updateUI();

            DocumentTopComponent dw = getDocumentWindow(e);
            if (dw != null) {
                dw.componentActivated();
            }

            TopComponent topComponent = getTopComponent(internalFrame);
            // Publish lookup contents of selected frame to parent window
            lookup.setLookup(topComponent.getLookup());
            // Publish activated nodes, if any
            setActivatedNodes(topComponent.getActivatedNodes());

            if (WorkspaceTopComponent.this != WindowManager.getDefault().getRegistry().getActivated()) {
                WorkspaceTopComponent.this.requestActive();
            }
        }

        @Override
        public void internalFrameDeactivated(InternalFrameEvent e) {
            LOG.info("internalFrameDeactivated: e = " + e);
            tabbedContainer.updateUI();

            DocumentTopComponent dw = getDocumentWindow(e);
            if (dw != null) {
                dw.componentDeactivated();
            }

            lookup.setLookup(Lookup.EMPTY);
        }

        @Override
        public void internalFrameIconified(InternalFrameEvent e) {
            LOG.info("internalFrameIconified: e = " + e);
            tabbedContainer.updateUI();

            DocumentTopComponent dw = getDocumentWindow(e);
            if (dw != null) {
                dw.componentHidden();
            }
        }

        @Override
        public void internalFrameDeiconified(InternalFrameEvent e) {
            LOG.info("internalFrameDeiconified: e = " + e);
            tabbedContainer.updateUI();

            DocumentTopComponent dw = getDocumentWindow(e);
            if (dw != null) {
                dw.componentShowing();
            }
        }

        private DocumentTopComponent getDocumentWindow(InternalFrameEvent e) {
            TopComponent topComponent = getTopComponent(e.getInternalFrame());
            if (topComponent instanceof DocumentTopComponent) {
                return (DocumentTopComponent) topComponent;
            }
            return null;
        }

    }

    /**
     * Allows telling the tabbedContainer if a tab component is maximized.
     */
    private class MyWinsysInfoForTabbedContainer extends WinsysInfoForTabbedContainer {
        @Override
        public Object getOrientation(Component comp) {
            return TabDisplayer.ORIENTATION_CENTER;
        }

        @Override
        public boolean inMaximizedMode(Component comp) {
            JInternalFrame internalFrame = desktopPane.getSelectedFrame();
            return internalFrame != null && internalFrame.isMaximum();
        }
    }

    private class MyPropertyChangeListener implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            TopComponent source = (TopComponent) event.getSource();
            JInternalFrame frame = getInternalFrame(source);
            if ("icon".equals(event.getPropertyName())) {
                Image icon = source.getIcon();
                if (icon != null) {
                    frame.setFrameIcon(new ImageIcon(icon));
                } else {
                    frame.setFrameIcon(null);
                }
            } else if ("displayName".equals(event.getPropertyName())) {
                frame.setTitle(source.getDisplayName());
                TabData tabData = frameToTabMap.get(frame);
                Assert.notNull(tabData);
                int i = tabbedContainer.getModel().indexOf(tabData);
                if (i >= 0) {
                    tabbedContainer.getModel().setText(i, source.getDisplayName());
                }
            }
        }
    }
}