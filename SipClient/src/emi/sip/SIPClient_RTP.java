package emi.sip;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import emi.sip.TransmitRTP.TransmitRTP;
import emi.sip.ReceiveRTP.ReceiveRTP;

public class SIPClient_RTP extends JFrame {
    private JTextField textFieldLocalSIP;
    private JTextField textFieldDestinationSIP;
    private JTextArea textAreaMsgRecu;
    private JTextArea textAreaMsgSent;
    private JButton buttonAppeler;
    private JButton buttonRaccrocher;

    private SipClient sipClient;
    private TransmitRTP transmitter;
    private ReceiveRTP receiver;
    private boolean isCallActive = false;
    private static final int RTP_PORT = 10000;

    public SIPClient_RTP() {
        initializeUI();
        initializeSIP();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
                System.exit(0);
            }
        });
    }

    private void initializeUI() {
        setTitle("SIP Client with RTP");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 400);
        setLayout(null);

        // Local SIP address field
        JLabel labelLocalSIP = new JLabel("Local SIP Address:");
        labelLocalSIP.setBounds(20, 20, 150, 25);
        add(labelLocalSIP);

        String localAddress = getLocalIPAddress();
        String localSIP = "sip:" + localAddress + ":6060";
        textFieldLocalSIP = new JTextField(localSIP);
        textFieldLocalSIP.setBounds(180, 20, 380, 25);
        textFieldLocalSIP.setEditable(false);
        add(textFieldLocalSIP);

        // Destination SIP address field
        JLabel labelDestinationSIP = new JLabel("Destination SIP:");
        labelDestinationSIP.setBounds(20, 60, 150, 25);
        add(labelDestinationSIP);

        textFieldDestinationSIP = new JTextField();
        textFieldDestinationSIP.setBounds(180, 60, 380, 25);
        add(textFieldDestinationSIP);

        // Message areas
        JLabel labelMsgRecu = new JLabel("Received Messages:");
        labelMsgRecu.setBounds(20, 100, 150, 25);
        add(labelMsgRecu);

        textAreaMsgRecu = new JTextArea();
        JScrollPane scrollPaneRecu = new JScrollPane(textAreaMsgRecu);
        scrollPaneRecu.setBounds(20, 130, 260, 150);
        add(scrollPaneRecu);

        JLabel labelMsgSent = new JLabel("Sent Messages:");
        labelMsgSent.setBounds(310, 100, 150, 25);
        add(labelMsgSent);

        textAreaMsgSent = new JTextArea();
        JScrollPane scrollPaneSent = new JScrollPane(textAreaMsgSent);
        scrollPaneSent.setBounds(310, 130, 260, 150);
        add(scrollPaneSent);

        // Control buttons
        buttonAppeler = new JButton("Call");
        buttonAppeler.setBounds(150, 300, 120, 30);
        buttonAppeler.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                initiateCall();
            }
        });
        add(buttonAppeler);

        buttonRaccrocher = new JButton("Hang Up");
        buttonRaccrocher.setBounds(330, 300, 120, 30);
        buttonRaccrocher.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                endCall();
            }
        });
        add(buttonRaccrocher);
        buttonRaccrocher.setEnabled(false);

        setVisible(true);
    }

    private void initializeSIP() {
        try {
            sipClient = new SipClient();
            sipClient.setUiComponents(textAreaMsgRecu, textAreaMsgSent);
            sipClient.onOpen();
            textAreaMsgSent.append("SIP stack initialized\n");
        } catch (Exception e) {
            textAreaMsgSent.append("Error initializing SIP: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void initiateCall() {
        String destinationSIP = textFieldDestinationSIP.getText().trim();
        if (destinationSIP.isEmpty()) {
            textAreaMsgSent.append("Error: Empty destination address\n");
            return;
        }

        try {
            String destinationIP = destinationSIP.split(":")[1].split(";")[0];

            // Start SIP signaling
            sipClient.onInvite(destinationSIP);

            // Initialize RTP components
            transmitter = new TransmitRTP();
            receiver = new ReceiveRTP();

            // Start RTP transmission
            new Thread(() -> {
                try {
                    String rtpUrl = destinationIP;
                    transmitter.startTransmission(rtpUrl);
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            textAreaMsgSent.append("Error starting RTP transmission: " + e.getMessage() + "\n")
                    );
                }
            }).start();

            // Start RTP reception
            new Thread(() -> {
                try {
                    String rtpUrl = destinationIP;
                    receiver.startReception(rtpUrl);
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            textAreaMsgSent.append("Error starting RTP reception: " + e.getMessage() + "\n")
                    );
                }
            }).start();

            isCallActive = true;
            buttonAppeler.setEnabled(false);
            buttonRaccrocher.setEnabled(true);
            textAreaMsgSent.append("Call initiated with " + destinationIP + "\n");
        } catch (Exception e) {
            textAreaMsgSent.append("Error initiating call: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void endCall() {
        try {
            // Stop SIP session
            if (sipClient != null) {
                sipClient.onBye(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
            }

            // Stop RTP sessions
            cleanup();

            isCallActive = false;
            buttonAppeler.setEnabled(true);
            buttonRaccrocher.setEnabled(false);
            textAreaMsgSent.append("Call ended\n");
        } catch (Exception e) {
            textAreaMsgSent.append("Error ending call: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void cleanup() {
        try {
            // Stop RTP transmission
            if (transmitter != null) {
                transmitter.stopTransmission();
                transmitter = null;
            }

            // Stop RTP reception
            if (receiver != null) {
                receiver.stopReception();
                receiver = null;
            }

            System.gc(); // Request garbage collection to help clean up resources
            textAreaMsgSent.append("RTP sessions cleaned up\n");
        } catch (Exception e) {
            textAreaMsgSent.append("Error in cleanup: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private String getLocalIPAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "127.0.0.1";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    // Set system look and feel
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                new SIPClient_RTP();
            }
        });
    }
}