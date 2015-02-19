package org.openmrs.module.muzimafingerPrint.panels;

import com.neurotec.biometrics.*;
import com.neurotec.devices.NDeviceManager;
import com.neurotec.devices.NDeviceType;
import com.neurotec.devices.NFingerScanner;
import com.neurotec.util.concurrent.CompletionHandler;
import org.json.JSONException;
import org.openmrs.module.muzimafingerPrint.model.PatientFingerPrintModel;
import org.openmrs.module.muzimafingerPrint.services.JavaScriptCallerService;
import org.openmrs.module.muzimafingerPrint.settings.FingersTools;

import javax.swing.*;
import javax.xml.bind.DatatypeConverter;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;

/**
 * Created by vikas on 19/02/15.
 */
public class ScanFingerprint extends BasePanel implements ActionListener {

    private static final String INITIALIZING_FINGERPRINT_MODULE = "initializing fingerprint";
    private static final String OBTAINING_LICENCES = "Obtaining licences, please wait.";
    private static final String SEARCHING_FOR_DEVICE = "Connecting to fingerprint device.";
    private static final String SCANNING_FINGERPRINT_PROGRESS = "Scanning fingerprint.";
    private static final String SCANNING_FINGERPRINT_COMPLETED = "Scanning fingerprint completed";
    private static final String IDENTIFYING_PATIENT = "Identification of patient stared";

    private static final String NO_LICENCE_FOUND = "No licence Found";
    private static final String NO_DEVICE_FOUND = "No device Found";
    private static final String NO_PATIENT_FOUND = "No patient Found";
    private static final String SCANNING_FAILED = "Unable to scan finger";

    private final CaptureCompletionHandler captureCompletionHandler = new CaptureCompletionHandler();
    private JLabel lblProgressMessage;
    private JPanel panelMain;
    private JPanel panelButtons;
    private JPanel panelMessage;
    private JList scannerList;
    private JButton btnTryAgain;
    private JButton btnRegisterPatient;
    private final NDeviceManager deviceManager;
    private NSubject subject;
    private JavaScriptCallerService service;

    public ScanFingerprint() {
        super();
        requiredLicenses = new ArrayList<String>();
        requiredLicenses.add("Biometrics.FingerExtraction");
        requiredLicenses.add("Biometrics.FingerMatchingFast");
        requiredLicenses.add("Biometrics.FingerMatching");
        requiredLicenses.add("Biometrics.FingerQualityAssessment");
        requiredLicenses.add("Biometrics.FingerSegmentation");
        requiredLicenses.add("Biometrics.FingerSegmentsDetection");
        requiredLicenses.add("Biometrics.Standards.Fingers");
        requiredLicenses.add("Biometrics.Standards.FingerTemplates");
        requiredLicenses.add("Devices.FingerScanners");

        optionalLicenses = new ArrayList<String>();
        optionalLicenses.add("Images.WSQ");

        FingersTools.getInstance().getClient().setUseDeviceManager(true);

        deviceManager = FingersTools.getInstance().getClient().getDeviceManager();
        deviceManager.setDeviceTypes(EnumSet.of(NDeviceType.FINGER_SCANNER));
        deviceManager.initialize();

        lblProgressMessage = new JLabel(INITIALIZING_FINGERPRINT_MODULE);
        btnTryAgain = new JButton("Try Again");
        btnRegisterPatient = new JButton("RegisterPatient");
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {

        try {
            if (actionEvent.getSource() == btnTryAgain) {

                RunFingerprintScanProcess();

            } else if (actionEvent.getSource() == btnRegisterPatient) {

                String template = DatatypeConverter.printBase64Binary(subject.getTemplateBuffer().toByteArray());
                service.callRegisterPatientJavaScriptFunction(template);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void initGUI() throws IOException, JSONException{
        panelMain = new JPanel();

        panelMessage = new JPanel();
        panelMessage.add(lblProgressMessage);
        panelMessage.setLayout(new FlowLayout(FlowLayout.TRAILING));

        panelButtons = new JPanel();
        panelButtons.setLayout(new FlowLayout(FlowLayout.TRAILING));

        btnTryAgain.setVisible(false);
        btnTryAgain.addActionListener(this);

        btnRegisterPatient.setVisible(false);
        btnRegisterPatient.addActionListener(this);

        panelButtons.add(btnTryAgain);
        panelButtons.add(btnRegisterPatient);

        panelMain.add(panelMessage);
        panelMain.add(panelButtons);

        add(panelMain);

        scannerList = new JList();

        RunFingerprintScanProcess();
    }

    private void RunFingerprintScanProcess() throws IOException, JSONException {

        lblProgressMessage.setText(OBTAINING_LICENCES);
        if (obtainLicenses()) {
            lblProgressMessage.setText(SEARCHING_FOR_DEVICE);
            if (SearchDevice()) {
                lblProgressMessage.setText(SCANNING_FINGERPRINT_PROGRESS);
                if (ScanFingerPrint()) {
                    lblProgressMessage.setText(IDENTIFYING_PATIENT);
                    if (IdentifyPatient()) {
                        lblProgressMessage.setText("Found");
                    } else {
                        lblProgressMessage.setText(NO_PATIENT_FOUND);
                        btnRegisterPatient.setVisible(true);
                    }
                } else {
                    lblProgressMessage.setText(SCANNING_FAILED);
                    btnTryAgain.setVisible(true);
                }
            } else {
                lblProgressMessage.setText(NO_DEVICE_FOUND);
                btnTryAgain.setVisible(true);
            }
        } else {
            lblProgressMessage.setText(NO_LICENCE_FOUND);
            btnTryAgain.setVisible(true);
        }

    }

    private boolean IdentifyPatient() throws JSONException {
        service = new JavaScriptCallerService((Applet) this.getParent().getParent().getParent().getParent().getParent().getParent().getParent());
        String fingerprint = DatatypeConverter.printBase64Binary(subject.getTemplateBuffer().toByteArray());
        PatientFingerPrintModel patient = service.identifyPatient(fingerprint);
        if (patient != null) {
            service.updatePatientListView(patient);
            return true;
        }
        return false;
    }

    private boolean ScanFingerPrint() {

        NFinger finger = new NFinger();
        finger.setCaptureOptions(EnumSet.of(NBiometricCaptureOption.MANUAL));
        subject = new NSubject();
        subject.getFingers().add(finger);
        NBiometricStatus status = FingersTools.getInstance().getClient().capture(subject);
        if (status == NBiometricStatus.OK) {
            return true;
        }
        return false;
    }

    private boolean SearchDevice() {

        NFingerScanner scanner = (NFingerScanner) FingersTools.getInstance().getClient().getFingerScanner();
        if (scanner != null) {
            FingersTools.getInstance().getClient().setFingerScanner(scanner);
            return true;
        } else {
            return false;
        }
    }


    @Override
    protected void setDefaultValues() {

    }

    @Override
    protected void updateControls() {

    }

    @Override
    protected void updateFingersTools() {
        FingersTools.getInstance().getClient().reset();
        FingersTools.getInstance().getClient().setUseDeviceManager(true);
        FingersTools.getInstance().getClient().setFingersReturnProcessedImage(true);

    }


    protected boolean obtainLicenses() {

        try {
            boolean status = FingersTools.getInstance().obtainLicenses(getRequiredLicenses());
            FingersTools.getInstance().obtainLicenses(getOptionalLicenses());
            return status;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private class CaptureCompletionHandler implements CompletionHandler<NBiometricTask, Object> {

        @Override
        public void completed(final NBiometricTask result, final Object attachment) {

        }

        @Override
        public void failed(final Throwable th, final Object attachment) {

        }

    }
}