package emi.sip;

import com.sun.media.rtp.RTPSessionMgr;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.media.*;
import javax.media.control.TrackControl;
import javax.media.format.AudioFormat;
import javax.media.format.UnsupportedFormatException;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.rtp.*;
import javax.media.rtp.event.NewReceiveStreamEvent;
import javax.media.rtp.event.ReceiveStreamEvent;

public class TP_RTP extends javax.swing.JFrame implements ReceiveStreamListener {

    String remoteIP;
    int remotePort, localPort;
    int fmt = 5;
    private RTPSessionMgr myVoiceSessionManager = null;
    private Processor myProcessor = null;
    private SendStream ss = null;
    private ReceiveStream rs = null;
    private Player player = null;
    private DataSource oDS = null;
    private String soundMicInput = "javasound://0";

    private void logprog(String txt) {
        System.out.println(txt);
    }

    private boolean isPortAvailable(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            return true; // Port is available
        } catch (IOException e) {
            return false; // Port is already in use
        }
    }


    // Démarre la session RTP
    public int demarrerSession(String peerIP, int peerPort, int recvPort, int fmt) throws IOException, NoDataSourceException, NoProcessorException {
        try {
            // Resolve the remote IP address
            InetAddress remoteHost = InetAddress.getByName(peerIP);
            if (remoteHost == null) {
                logprog("Erreur: l'adresse IP distante est invalide.");
                return -1;
            }

            // Dynamically assign a free port if recvPort is unavailable
            if (!isPortAvailable(recvPort)) {
                logprog("Port " + recvPort + " is already in use. Assigning a new port...");
                DatagramSocket tempSocket = new DatagramSocket(0); // System assigns a free port
                recvPort = tempSocket.getLocalPort();
                tempSocket.close();
                logprog("New receive port: " + recvPort);
            }

            // Create a local media source (microphone input)
            MediaLocator mediaLocator = new MediaLocator("javasound://0");
            DataSource dataSource = Manager.createDataSource(mediaLocator);
            myProcessor = Manager.createProcessor(dataSource);

            // Prepare the processor for the selected format
            PrepareProcessor(fmt);
            oDS = myProcessor.getDataOutput();

            // Initialize the RTP session
            myVoiceSessionManager = new RTPSessionMgr();
            InetAddress localHost = InetAddress.getLocalHost();
            SessionAddress localAddr = new SessionAddress(localHost, recvPort, localHost, recvPort + 1);
            SessionAddress remoteAddr = new SessionAddress(remoteHost, peerPort, remoteHost, peerPort + 1);

            myVoiceSessionManager.initSession(localAddr, null, 0.05, 0.25);
            myVoiceSessionManager.startSession(localAddr, localAddr, remoteAddr, null);

            // Start sending audio data
            ss = myVoiceSessionManager.createSendStream(oDS, 0);
            ss.start();

            myProcessor.start();
            logprog("RTP session started on local port: " + recvPort);
        } catch (Exception e) {
            System.err.println("Erreur lors du démarrage de la session RTP: " + e.getMessage());
            return -1;
        }
        return 1;
    }


    // Arrêter la session RTP
    public void arreterSession() {
        if (player != null) {
            try {
                player.stop();
                player.deallocate();
                player.close();
            } catch (Exception e) {
                System.err.println("Error closing player: " + e.getMessage());
            } finally {
                player = null;
            }
        }

        if (ss != null) {
            try {
                ss.stop();
            } catch (IOException e) {
                System.err.println("Error stopping SendStream: " + e.getMessage());
            } finally {
                ss = null;
            }
        }

        if (myProcessor != null) {
            try {
                myProcessor.stop();
                myProcessor.deallocate();
                myProcessor.close();
            } catch (Exception e) {
                System.err.println("Error stopping processor: " + e.getMessage());
            } finally {
                myProcessor = null;
            }
        }

        if (myVoiceSessionManager != null) {
            try {
                myVoiceSessionManager.closeSession();
                myVoiceSessionManager.dispose();
            } catch (Exception e) {
                System.err.println("Error closing RTP session: " + e.getMessage());
            } finally {
                myVoiceSessionManager = null;
            }
        }
    }

    // Préparer le processeur pour le format audio
    void PrepareProcessor(int fmt) {
        myProcessor.configure();
        while (myProcessor.getState()!=Processor.Configured) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                System.out.println(TP_RTP.class.getName() + " Exception : " + ex);
            }
        }
        myProcessor.setContentDescriptor(new ContentDescriptor (ContentDescriptor.RAW_RTP));

        TrackControl track[]=myProcessor.getTrackControls();
        AudioFormat af =null;
        switch (fmt) {
            case 3: af=new AudioFormat(AudioFormat.GSM_RTP,8000,4,1);break;
            case 4: af=new AudioFormat(AudioFormat.G723_RTP,8000,4,1);break;
            case 5: af=new AudioFormat(AudioFormat.ULAW_RTP);break;
            default:logprog("unknown audio format");return;
        }
        track[0].setFormat(af);
        myProcessor.realize();
        while (myProcessor.getState() != Processor.Realized) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                System.out.println(TP_RTP.class.getName() + " Exception : " + ex);
            }
        }
    }

    @Override
    public void update(ReceiveStreamEvent event) {
        if (event instanceof NewReceiveStreamEvent) {
            try {
                rs = event.getReceiveStream();
                DataSource receivedDataSource = rs.getDataSource();

                if (receivedDataSource != null) {
                    player = Manager.createPlayer(receivedDataSource);
                    player.start();
                }
            } catch (IOException | NoPlayerException ex) {
                System.err.println("Error handling the received stream: " + ex.getMessage());
            }
        }
    }
}
