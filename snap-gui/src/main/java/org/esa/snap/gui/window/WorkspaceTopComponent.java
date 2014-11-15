/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.snap.gui.window;

import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.netbeans.swing.tabcontrol.DefaultTabDataModel;
import org.netbeans.swing.tabcontrol.TabData;
import org.netbeans.swing.tabcontrol.TabDisplayer;
import org.netbeans.swing.tabcontrol.TabbedContainer;
import org.netbeans.swing.tabcontrol.WinsysInfoForTabbedContainer;
import org.netbeans.swing.tabcontrol.event.TabActionEvent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Norman
 */
@TopComponent.Description(
        preferredID = "WorkspaceTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(
        mode = "editor",
        openAtStartup = true)
@ActionID(category = "Window", id = "org.snap.gui.WorkspaceTopComponent")
@ActionReference(path = "Menu/View/Tool Windows", position = 0)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_WorkspaceTopComponent",
        preferredID = "WorkspaceTopComponent"
)
@Messages({"CTL_WorkspaceTopComponentAction=Workspace Window",
                  "CTL_WorkspaceTopComponent=Workspace",
                  "HINT_WorkspaceTopComponent=This is a Workspace window",
          })
public class WorkspaceTopComponent extends TopComponent implements InternalFrameListener, ActionListener {

    private TabbedContainer tabbedContainer;
    private JDesktopPane desktopPane;
    private final Map<JInternalFrame, Editor> frameToEditorMap;
    private final Map<TabData, JInternalFrame> tabToFrameMap;
    private final Map<JInternalFrame, TabData> frameToTabMap;
    private final Map<String, Action> tabActions;
    private int tabCount;
    private static WorkspaceTopComponent instance;

    public WorkspaceTopComponent() {
        frameToEditorMap = new HashMap<>();
        frameToTabMap = new HashMap<>();
        tabToFrameMap = new HashMap<>();
        tabActions = new HashMap<>();
        initComponents();
        setName(Bundle.CTL_WorkspaceTopComponent());
        setToolTipText(Bundle.HINT_WorkspaceTopComponent());
        instance = this;
    }

    public static WorkspaceTopComponent getInstance() {
        return instance;
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        DefaultTabDataModel tabDataModel = new DefaultTabDataModel();
        tabbedContainer = new TabbedContainer(tabDataModel,
                                              TabbedContainer.TYPE_EDITOR,
                                              WinsysInfoForTabbedContainer.getDefault(new MyWinsysInfoForTabbedContainer()));

        //TabData tabData = new TabData(new JPanel(), null, "Dummy Tab", "Dummy Tab");
        //tabbedContainer.getModel().addTab(0, tabData);
        tabbedContainer.setVisible(false);

        desktopPane = new JDesktopPane();
        desktopPane.setBackground(new Color(16, 12, 123));

        add(tabbedContainer, BorderLayout.NORTH);
        add(desktopPane, BorderLayout.CENTER);

        initTabActions();
    }

    private void initTabActions() {
        addTabAction(new TabAction("close") {
            @Override
            public void tabActionPerformed(TabActionEvent actionEvent) {
                // Note: the tab UI is already removed, but the tabData is still in the model. NetBeans will remove it later.
                int tabIndex = actionEvent.getTabIndex();
                JInternalFrame internalFrame = getInternalFrame(tabIndex);
                if (internalFrame != null) {
                    closeWindow(internalFrame, false);
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
                JInternalFrame internalFrame = getInternalFrame(tabIndex);
                if (internalFrame != null) {
                    try {
                        internalFrame.setMaximum(!internalFrame.isMaximum());
                    } catch (PropertyVetoException e) {
                        // ok
                    }
                }
            }
        });
        addTabAction(new TabAction("popup") {
            @Override
            public void tabActionPerformed(TabActionEvent actionEvent) {

                int tabIndex = actionEvent.getTabIndex();
                System.out.println("tabIndex = " + tabIndex);

                JPopupMenu popupMenu = new JPopupMenu();
                if (tabIndex >= 0) {
                    popupMenu.add(new JMenuItem("Close"));
                    popupMenu.add(new JMenuItem("Close All"));
                    popupMenu.add(new JMenuItem("Close Others"));
                    popupMenu.addSeparator();
                    popupMenu.add(new JMenuItem("Maximize"));
                    popupMenu.addSeparator();
                    popupMenu.add(new JMenuItem("Clone"));
                    popupMenu.addSeparator();
                    popupMenu.add(new JMenuItem("Tile Evenly"));
                    popupMenu.add(new JMenuItem("Tile Horizontally"));
                    popupMenu.add(new JMenuItem("Tile Vertically"));
                } else {
                    popupMenu.add(new JMenuItem("Close All"));
                    popupMenu.addSeparator();
                    popupMenu.add(new JMenuItem("Tile Evenly"));
                    popupMenu.add(new JMenuItem("Tile Horizontally"));
                    popupMenu.add(new JMenuItem("Tile Vertically"));
                }

                popupMenu.show(tabbedContainer, actionEvent.getMouseEvent().getX(), actionEvent.getMouseEvent().getY());
            }
        });
    }

    private void addTabAction(Action action) {
        tabActions.put((String) action.getValue(Action.ACTION_COMMAND_KEY), action);
    }


    private JInternalFrame getInternalFrame(int tabIndex) {
        return tabToFrameMap.get(tabbedContainer.getModel().getTab(tabIndex));
    }

    public List<Editor<ProductSceneView>> findImageEditors(RasterDataNode band) {
        ArrayList<Editor<ProductSceneView>> list = new ArrayList<>();
        Collection<Editor> editors = frameToEditorMap.values();
        for (Editor editor : editors) {
            Container component = editor.getComponent();
            if (component instanceof ProductSceneView) {
                ProductSceneView view = (ProductSceneView) component;
                if (view.getRaster() == band) {
                    //noinspection unchecked
                    list.add(editor);
                }
            }
        }
        return list;
    }

    public <T extends Container> Editor<T> addEditorComponent(String title, T component) {
        int index = tabCount++;
        JInternalFrame internalFrame = new JInternalFrame(title, true, true, true, true);

        // Note: The following dummyComponent with preferred size (-1, 2) allows for using the tabbedContainer as
        // a *thin*, empty tabbed bar on top of the desktopPane.
        JComponent dummyComponent = new JPanel();
        dummyComponent.setPreferredSize(new Dimension(-1, 2));
        TabData tabData = new TabData(dummyComponent, null, title, "Tab + " + index);

        Editor editor = new Editor(internalFrame);
        frameToEditorMap.put(internalFrame, editor);
        frameToTabMap.put(internalFrame, tabData);
        tabToFrameMap.put(tabData, internalFrame);

        internalFrame.setContentPane(component);
        internalFrame.setBounds(new Rectangle(tabCount * 24, tabCount * 24, 400, 400));

        tabbedContainer.getModel().addTab(tabbedContainer.getModel().size(), tabData);
        tabbedContainer.setVisible(true);
        desktopPane.add(internalFrame);

        internalFrame.addInternalFrameListener(this);

        internalFrame.setVisible(true);
        try {
            internalFrame.setSelected(true);
        } catch (PropertyVetoException e) {
            e.printStackTrace();
        }

        //noinspection unchecked
        return editor;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        Action action = tabActions.get(actionEvent.getActionCommand());
        if (action != null) {
            action.actionPerformed(actionEvent);
        }
    }

    @Override
    public void internalFrameOpened(InternalFrameEvent e) {
        tabbedContainer.updateUI();
    }

    @Override
    public void internalFrameClosing(InternalFrameEvent e) {
        // do nothing
    }

    @Override
    public void internalFrameClosed(InternalFrameEvent e) {
        JInternalFrame internalFrame = e.getInternalFrame();
        if (frameToTabMap.containsKey(internalFrame)) {
            closeWindow(internalFrame, true);
        }
        tabbedContainer.updateUI();
    }

    @Override
    public void internalFrameIconified(InternalFrameEvent e) {
        tabbedContainer.updateUI();
    }

    @Override
    public void internalFrameDeiconified(InternalFrameEvent e) {
        tabbedContainer.updateUI();
    }

    @Override
    public void internalFrameActivated(InternalFrameEvent e) {

        JInternalFrame internalFrame = e.getInternalFrame();
        TabData selectedTab = frameToTabMap.get(internalFrame);

        List<TabData> tabs = tabbedContainer.getModel().getTabs();
        for (int i = 0; i < tabs.size(); i++) {
            TabData tab = tabs.get(i);
            if (tab == selectedTab && tabbedContainer.getSelectionModel().getSelectedIndex() != i) {
                tabbedContainer.getSelectionModel().setSelectedIndex(i);
                break;
            }
        }
        tabbedContainer.updateUI();
    }

    @Override
    public void internalFrameDeactivated(InternalFrameEvent e) {
        tabbedContainer.updateUI();
    }

    @Override
    public void componentOpened() {
        tabbedContainer.addActionListener(this);
    }

    @Override
    public void componentClosed() {
        tabbedContainer.removeActionListener(this);
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

    private void closeWindow(JInternalFrame internalFrame, boolean removeTab) {
        internalFrame.removeInternalFrameListener(this);
        frameToEditorMap.remove(internalFrame);
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
    }

    public String getUniqueEditorTitle(String titleBase) {
        return UIUtils.getUniqueFrameTitle(desktopPane.getAllFrames(), titleBase);
    }

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

    abstract class TabAction extends AbstractAction {
        protected TabAction(String name) {
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

    /**
     * todo: make the API compatible or adaptible to the
     * <a href="http://bits.netbeans.org/8.0/javadoc/org-netbeans-core-multiview/index.html?overview-summary.html">org.netbeans.core.spi.multiview.MultiViewElement</a>.
     * @param <T> The editor component type.
     */
    @SuppressWarnings("unchecked")
    public class Editor<T extends Container> {
        private final JInternalFrame internalFrame;

        Editor(JInternalFrame internalFrame) {
            this.internalFrame = internalFrame;
        }

        // Add more internalFrame property accessors here

        public void setTitle(String title) {
            internalFrame.setTitle(title);
        }

        public T getComponent() {
            return (T) this.internalFrame.getContentPane();
        }

        public void addListener(EditorListener editorListener) {
            final Editor<T> editor = this;
            internalFrame.addInternalFrameListener(new InternalFrameAdapter() {
                @Override
                public void internalFrameActivated(InternalFrameEvent e) {
                    editorListener.editorActivated(editor);
                }

                @Override
                public void internalFrameDeactivated(InternalFrameEvent e) {
                    editorListener.editorDeactivated(editor);
                }

                @Override
                public void internalFrameClosing(InternalFrameEvent e) {
                    editorListener.editorClosing(editor);
                }

                @Override
                public void internalFrameClosed(InternalFrameEvent e) {
                    editorListener.editorClosed(editor);
                }
            });
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Editor && internalFrame == ((Editor) o).internalFrame;
        }

        @Override
        public int hashCode() {
            return internalFrame.hashCode();
        }

    }

    public interface EditorListener<T extends Container> {
        void editorActivated(Editor<T> editor);

        void editorDeactivated(Editor<T> editor);

        boolean editorClosing(Editor<T> editor);

        void editorClosed(Editor<T> editor);
    }

    public static class EditorAdapter<T extends Container> implements EditorListener<T> {
        @Override
        public void editorActivated(Editor<T> editor) {
        }

        @Override
        public void editorDeactivated(Editor<T> editor) {
        }

        @Override
        public boolean editorClosing(Editor<T> editor) {
            return true;
        }

        @Override
        public void editorClosed(Editor<T> editor) {
        }
    }
}
