package org.openpnp.machine.reference.camera.wizards;


import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.components.ComponentDecorators;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.machine.reference.camera.RemoteJpgCaptureCamera;
import org.openpnp.util.UiUtils;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

@SuppressWarnings("serial")
public class RemoteJpgCaptureCameraWizard extends AbstractConfigurationWizard {

    private final RemoteJpgCaptureCamera camera;
    private JPanel panelGeneral;

    public RemoteJpgCaptureCameraWizard(RemoteJpgCaptureCamera camera) {
        this.camera = camera;

        panelGeneral = new JPanel();
        contentPanel.add(panelGeneral);
        panelGeneral.setBorder(new TitledBorder(null,
                "General", TitledBorder.LEADING, TitledBorder.TOP, null));
        panelGeneral.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("default:grow"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,},
            new RowSpec[] {
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,}));

        lblIP = new JLabel("Camera URL");
        lblIP.setToolTipText("URL for remote camera such as http://example.com:8080/capture-image");
        panelGeneral.add(lblIP, "2, 2, right, default");

        urlTextField = new JTextField();
        panelGeneral.add(urlTextField, "4, 2");
        urlTextField.setColumns(16);
        
        lblTimeout = new JLabel("Connection Timeout [ms]");
        lblTimeout.setToolTipText("Timeout in milliseconds used when connecting to remote camera server");
        panelGeneral.add(lblTimeout, "2, 4, right, default");
        
        timeout = new JTextField();
        panelGeneral.add(timeout, "4, 4, left, default");
        timeout.setColumns(10);

        lblRequestTimeout = new JLabel("Request Timeout [ms]");
        lblRequestTimeout.setToolTipText("Timeout in milliseconds used by HTTP request for a captured frame");
        panelGeneral.add(lblRequestTimeout, "2, 6, right, default");

        requestTimeout = new JTextField();
        panelGeneral.add(requestTimeout, "4, 6, left, default");
        requestTimeout.setColumns(10);

        lblOpenRetryInterval = new JLabel("Reopen Retry Interval [ms]");
        lblOpenRetryInterval.setToolTipText("If the remote camera does not respond, the connection will be closed and a reopen will be attempted using this interval.");
        panelGeneral.add(lblOpenRetryInterval, "2, 8, right, default");

        openRetryInterval = new JTextField();
        panelGeneral.add(openRetryInterval, "4, 8, left, default");
        openRetryInterval.setColumns(10);

        lblCaptureWidth = new JLabel("Capture Width");
        lblCaptureWidth.setToolTipText("Width in pixels of frame captured by remote camera. This is used as the width for a place holder image if the remote does not respond with an image.");
        panelGeneral.add(lblCaptureWidth, "2, 10, right, default");

        captureWidth = new JTextField();
        panelGeneral.add(captureWidth, "4, 10, left, default");
        captureWidth.setColumns(10);

        lblCaptureHeight = new JLabel("Capture Height");
        lblCaptureHeight.setToolTipText("Height in pixels of frame captured by remote camera. This is used as the height for a place holder image if the remote does not respond with an image.");
        panelGeneral.add(lblCaptureHeight, "2, 12, right, default");

        captureHeight = new JTextField();
        panelGeneral.add(captureHeight, "4, 12, left, default");
        captureHeight.setColumns(10);
    }

    @Override
    public void createBindings() {
        IntegerConverter intConverter = new IntegerConverter();

        // Should always be last so that it doesn't trigger multiple camera reloads.
        addWrappedBinding(camera, "remoteURL", urlTextField, "text");
        addWrappedBinding(camera, "timeout", timeout, "text", intConverter);
        addWrappedBinding(camera, "requestTimeout", requestTimeout, "text", intConverter);
        addWrappedBinding(camera, "openRetryInterval", openRetryInterval, "text", intConverter);
        addWrappedBinding(camera, "captureWidth", captureWidth, "text", intConverter);
        addWrappedBinding(camera, "captureHeight", captureHeight, "text", intConverter);

        ComponentDecorators.decorateWithAutoSelect(urlTextField);
        ComponentDecorators.decorateWithAutoSelect(timeout);
        ComponentDecorators.decorateWithAutoSelect(requestTimeout);
        ComponentDecorators.decorateWithAutoSelect(openRetryInterval);
        ComponentDecorators.decorateWithAutoSelect(captureWidth);
        ComponentDecorators.decorateWithAutoSelect(captureHeight);
    }

    @Override
    protected void loadFromModel() {
        super.loadFromModel();
    }

    @Override
    protected void saveToModel() {
        super.saveToModel();
        if (camera.isDirty()) {
            UiUtils.messageBoxOnException(() -> {
                camera.reinitialize(); 
            });
        }
    }

    private JLabel lblIP;
    private JTextField urlTextField;
    private JLabel lblTimeout;
    private JTextField timeout;
    private JLabel lblRequestTimeout;
    private JTextField requestTimeout;
    private JLabel lblOpenRetryInterval;
    private JTextField openRetryInterval;
    private JLabel lblCaptureWidth;
    private JTextField captureWidth;
    private JLabel lblCaptureHeight;
    private JTextField captureHeight;
}
