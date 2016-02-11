package org.python.pydev.editor.hover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Display;
import org.python.pydev.editor.PyInformationPresenter;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.shared_core.log.Log;
import org.python.pydev.shared_core.string.FastStringBuffer;

public class DefaultPydevCombiningHover extends AbstractPyEditorTextHover implements IExecutableExtensionFactory {

    public static final Object ID_DEFAULT_COMBINING_HOVER = "org.python.pydev.editor.hover.defaultCombiningHover";

    private ArrayList<PyEditorTextHoverDescriptor> fTextHoverSpecifications;

    private ArrayList<AbstractPyEditorTextHover> fInstantiatedTextHovers;

    private Map<AbstractPyEditorTextHover, PyEditorTextHoverDescriptor> hoverMap = new HashMap<AbstractPyEditorTextHover, PyEditorTextHoverDescriptor>();

    boolean preempt = false;

    Integer currentPriority = null;

    boolean contentTypeSupported = false;

    private int lastDividerLen;

    protected ITextViewer viewer;

    private static final String DIVIDER_CHAR = Character.toString((char) 0xfeff2015);

    public DefaultPydevCombiningHover() {
        installTextHovers();
        this.addInformationPresenterControlListener(new ControlListener() {

            @Override
            public void controlMoved(ControlEvent e) {
            }

            @Override
            public void controlResized(ControlEvent e) {
                if (hoverControlPreferredWidth != null) {
                    informationControl.setSize(hoverControlPreferredWidth, informationControl.getBounds().height);
                }
                hoverControlWidth = informationControl.getBounds().width;
                StyledText text = (StyledText) e.getSource();
                if (PyHoverPreferencesPage.getUseHoverDelimiters()) {
                    resizeDividerText(text, hoverControlWidth);
                }
            }

        });
    }

    /**
     * Installs all text hovers.
     */
    private void installTextHovers() {

        // initialize lists - indicates that the initialization happened
        fTextHoverSpecifications = new ArrayList<PyEditorTextHoverDescriptor>(2);
        fInstantiatedTextHovers = new ArrayList<AbstractPyEditorTextHover>(2);

        // populate list
        PyEditorTextHoverDescriptor[] hoverDescs = PydevPlugin.getDefault().getPyEditorTextHoverDescriptors(false);
        for (int i = 0; i < hoverDescs.length; i++) {
            // ensure that we don't add ourselves to the list
            if (!ID_DEFAULT_COMBINING_HOVER.equals(hoverDescs[i].getId())) {
                fTextHoverSpecifications.add(hoverDescs[i]);
            }
        }
    }

    private void checkTextHovers() {
        if (fTextHoverSpecifications == null) {
            return;
        }

        boolean done = true;
        int i = -1;
        for (Iterator<PyEditorTextHoverDescriptor> iterator = fTextHoverSpecifications.iterator(); iterator
                .hasNext();) {
            i++;
            PyEditorTextHoverDescriptor spec = iterator.next();
            if (spec == null) {
                continue;
            }

            done = false;

            AbstractPyEditorTextHover hover = spec.createTextHover();
            if (hover != null) {
                fTextHoverSpecifications.set(i, null);
                if (i == fInstantiatedTextHovers.size()) {
                    fInstantiatedTextHovers.add(i, hover);
                } else {
                    fInstantiatedTextHovers.set(i, hover);
                }
                hoverMap.put(hover, spec);
            }

        }
        if (done) {
            fTextHoverSpecifications = null;
        }
    }

    @Override
    public String getHoverInfo(final ITextViewer textViewer, IRegion hoverRegion) {
        this.viewer = textViewer;
        final FastStringBuffer buf = new FastStringBuffer();
        checkTextHovers();

        if (fInstantiatedTextHovers == null) {
            return null;
        }

        boolean firstHoverInfo = true;
        //hovers are sorted by priority in descending order
        for (Iterator<AbstractPyEditorTextHover> iterator = fInstantiatedTextHovers.iterator(); iterator.hasNext();) {
            final AbstractPyEditorTextHover hover = iterator.next();
            if (hover == null) {
                continue;
            }

            PyEditorTextHoverDescriptor descr = hoverMap.get(hover);
            if (!descr.isEnabled()) {
                continue;
            }
            if (hoverMap.get(hover) != null) {
                if (currentPriority == null) {
                    currentPriority = descr.getPriority();
                }
                if (descr.getPriority().equals(currentPriority) || !preempt) {
                    final String hoverText = hover.getHoverInfo(textViewer, hoverRegion);
                    if (hoverText != null && hoverText.trim().length() > 0) {
                        if (!firstHoverInfo && PyHoverPreferencesPage.getUseHoverDelimiters()) {
                            buf.append(PyInformationPresenter.LINE_DELIM);
                            viewer.getTextWidget().getDisplay().syncExec(new Runnable() {

                                @Override
                                public void run() {
                                    if (hoverControlWidth != null) {
                                        buf.append(createDivider(hoverControlWidth));
                                    } else {
                                        buf.append(createDivider(getMaxExtent(hoverText)));
                                    }
                                }

                            });
                            buf.append(PyInformationPresenter.LINE_DELIM);
                        } else if (buf.length() > 0) {
                            buf.append(PyInformationPresenter.LINE_DELIM);
                        }
                        buf.append(hoverText);
                        firstHoverInfo = false;
                        checkHoverControlWidth(hover);
                    }
                }
                currentPriority = descr.getPriority();
                preempt = descr.isPreempt();
            }
        }
        currentPriority = null;
        preempt = false;
        return buf.toString();
    }

    private void checkHoverControlWidth(AbstractPyEditorTextHover hover) {
        if (hover.getHoverControlPreferredWidth() != null) {
            if (this.hoverControlWidth == null) {
                this.hoverControlPreferredWidth = hover.getHoverControlPreferredWidth();
                if (informationControl != null) {
                    informationControl.setSize(hoverControlPreferredWidth, informationControl.getBounds().height);
                }
            } else if (hover.getHoverControlPreferredWidth() > this.hoverControlWidth) {
                this.hoverControlPreferredWidth = hover.getHoverControlPreferredWidth();
                if (informationControl != null) {
                    informationControl.setSize(hoverControlPreferredWidth, informationControl.getBounds().height);
                }
            }
        }
    }

    protected void resizeDividerText(StyledText text, final int width) {
        if (width != lastDividerLen) {
            final String[] newDivider = new String[1];
            int oldLen = lastDividerLen;
            text.getDisplay().syncExec(new Runnable() {

                @Override
                public void run() {
                    newDivider[0] = createDivider(width);
                }

            });
            String regex = "\\" + DIVIDER_CHAR + "{" + oldLen + "}\\n\\s\\" + DIVIDER_CHAR + "{" +
                    Math.abs(oldLen - lastDividerLen) + "}";
            text.setText(text.getText().replaceAll(regex, newDivider[0]));
        }
    }

    /**
     * Must be called from the event dispatch thread
     * @param width
     * @return
     */
    String createDivider(final int width) {
        Assert.isTrue(Display.getCurrent().getThread() == Thread.currentThread(),
                "This method must be called from the UI thread");
        final StringBuilder divider = new StringBuilder();
        getHoverControlCreator();
        GC gc = new GC(viewer.getTextWidget().getDisplay());
        while (gc.stringExtent(divider.toString()).x < width) {
            divider.append(DIVIDER_CHAR);
        }
        divider.deleteCharAt(divider.length() - 1);
        gc.dispose();
        lastDividerLen = divider.length();
        return divider.toString();
    }

    protected int getMaxExtent(String hoverText) {
        GC gc = new GC(viewer.getTextWidget().getDisplay());
        int max = 0;
        for (String line : hoverText.split("\\n")) {
            /*TODO we need a way to skip lines that will be formatted by the InformationPresenter
              For now, we hard-code it to skip file paths embedded in the hover info*/
            if (!line.startsWith("FILE_PATH=")) {
                int extent = gc.stringExtent(line).x;
                if (extent > max) {
                    max = extent;
                }
            }
        }
        gc.dispose();
        return max;
    }

    @Override
    public boolean isContentTypeSupported(String contentType) {
        return true;
    }

    @Override
    public Object create() throws CoreException {
        try {
            PyEditorTextHoverDescriptor contributedHover = PydevPlugin.getDefault()
                    .getPyEditorCombiningTextHoverDescriptor(false);
            return contributedHover != null ? contributedHover : new DefaultPydevCombiningHover();
        } catch (CoreException e) {
            Log.log(e.getMessage());
            return new DefaultPydevCombiningHover();
        }
    }

}
