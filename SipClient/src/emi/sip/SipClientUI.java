package emi.sip;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import emi.sip.TransmitRTP.TransmitRTP;
import emi.sip.ReceiveRTP.ReceiveRTP;

public class SipClientUI extends JFrame {
    // UI Components
    private JTextField textFieldLocalSIP;
    private JTextField textFieldDestinationSIP;
    private JTextArea textAreaMsgRecu;
    private JTextArea textAreaMsgSent;
    private JButton buttonAppeler;
    private JButton buttonRaccrocher;
    private JLabel statusLabel;

    // Core components
    private SipClient sipClient;
    private TransmitRTP transmitRTP;
    private ReceiveRTP receiveRTP;
    private Thread transmitThread;
    private Thread receiveThread;

    // Call state
    private boolean isCallActive = false;
    private static final int RTP_PORT = 10000;

    public SipClientUI() {
        setupMainWindow();
        initializeComponents();
        initializeSIPStack();
        addWindowListener();
    }

    private void setupMainWindow() {
        setTitle("Enhanced SIP Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // Use BorderLayout for main organization
        setLayout(new BorderLayout(10, 10));

        // Add padding around the main content
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initializeComponents() {
        // Create main panels
        JPanel topPanel = createTopPanel();
        JPanel centerPanel = createCenterPanel();
        JPanel bottomPanel = createBottomPanel();

        // Add panels to frame
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Local SIP Address
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Local SIP Address:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        textFieldLocalSIP = new JTextField(getLocalSipAddress());
        textFieldLocalSIP.setEditable(false);
        panel.add(textFieldLocalSIP, gbc);

        // Destination SIP Address
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        panel.add(new JLabel("Destination SIP:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        textFieldDestinationSIP = new JTextField();
        panel.add(textFieldDestinationSIP, gbc);

        panel.setBorder(BorderFactory.createTitledBorder("SIP Configuration"));
        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));

        // Received Messages
        JPanel receivedPanel = new JPanel(new BorderLayout());
        receivedPanel.setBorder(BorderFactory.createTitledBorder("Received Messages"));
        textAreaMsgRecu = new JTextArea();
        textAreaMsgRecu.setEditable(false);
        JScrollPane receivedScroll = new JScrollPane(textAreaMsgRecu);
        receivedPanel.add(receivedScroll, BorderLayout.CENTER);

        // Sent Messages
        JPanel sentPanel = new JPanel(new BorderLayout());
        sentPanel.setBorder(BorderFactory.createTitledBorder("Sent Messages"));
        textAreaMsgSent = new JTextArea();
        textAreaMsgSent.setEditable(false);
        JScrollPane sentScroll = new JScrollPane(textAreaMsgSent);
        sentPanel.add(sentScroll, BorderLayout.CENTER);

        panel.add(receivedPanel);
        panel.add(sentPanel);
        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));

        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(statusLabel, BorderLayout.WEST);

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        buttonAppeler = new JButton("Call");
        buttonAppeler.addActionListener(e -> initiateCall());

        buttonRaccrocher = new JButton("Hang Up");
        buttonRaccrocher.addActionListener(e -> endCall());
        buttonRaccrocher.setEnabled(false);

        buttonsPanel.add(buttonAppeler);
        buttonsPanel.add(buttonRaccrocher);
        panel.add(buttonsPanel, BorderLayout.EAST);

        return panel;
    }

    private void initializeSIPStack() {
        try {
            sipClient = new SipClient();
            sipClient.setUiComponents(textAreaMsgRecu, textAreaMsgSent);
            sipClient.onOpen();
            updateStatus("SIP stack initialized");
        } catch (Exception e) {
            updateStatus("Error initializing SIP stack: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initiateCall() {
        if (isCallActive) {
            updateStatus("Call already in progress");
            return;
        }

        String destinationSIP = textFieldDestinationSIP.getText().trim();
        if (destinationSIP.isEmpty()) {
            updateStatus("Error: Destination address is empty");
            return;
        }

        try {
            // Extract destination IP from SIP URI
            String destinationIP = destinationSIP.split(":")[1].split(";")[0];

            // Start SIP signaling
            sipClient.onInvite(destinationSIP);

            // Start RTP transmission in a separate thread
            transmitThread = new Thread(() -> {
                try {
                    String rtpURL = "rtp://" + destinationIP + ":" + RTP_PORT + "/audio/1";
                    transmitRTP = new TransmitRTP();
                    String[] args = {rtpURL};
                    transmitRTP.main(args);
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            updateStatus("Error in RTP transmission: " + e.getMessage()));
                }
            });
            transmitThread.start();

            // Start RTP reception in a separate thread
            receiveThread = new Thread(() -> {
                try {
                    String rtpURL = "rtp://" + destinationIP + ":" + RTP_PORT + "/audio/1";
                    receiveRTP = new ReceiveRTP();
                    String[] args = {rtpURL};
                    receiveRTP.main(args);
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            updateStatus("Error in RTP reception: " + e.getMessage()));
                }
            });
            receiveThread.start();

            // Update UI state
            isCallActive = true;
            buttonAppeler.setEnabled(false);
            buttonRaccrocher.setEnabled(true);
            updateStatus("Call initiated with " + destinationIP);

        } catch (Exception e) {
            updateStatus("Error initiating call: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void endCall() {
        if (!isCallActive) {
            updateStatus("No active call to end");
            return;
        }

        try {
            // End SIP session
            sipClient.onBye(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));

            // Stop RTP threads
            if (transmitThread != null && transmitThread.isAlive()) {
                transmitThread.interrupt();
            }
            if (receiveThread != null && receiveThread.isAlive()) {
                receiveThread.interrupt();
            }

            // Clean up RTP resources
            transmitRTP = null;
            receiveRTP = null;

            // Update UI state
            isCallActive = false;
            buttonAppeler.setEnabled(true);
            buttonRaccrocher.setEnabled(false);
            updateStatus("Call ended");

        } catch (Exception e) {
            updateStatus("Error ending call: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addWindowListener() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isCallActive) {
                    endCall();
                }
            }
        });
    }

    private String getLocalSipAddress() {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();
            return "sip:" + localIP + ":6060";
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "sip:127.0.0.1:6060";
        }
    }

    private void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            textAreaMsgSent.append(message + "\n");
        });
    }

    public static void main(String[] args) {
        try {
            // Set system look and feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            SipClientUI ui = new SipClientUI();
            ui.setVisible(true);
        });
    }
}