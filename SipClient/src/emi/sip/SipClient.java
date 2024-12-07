package emi.sip;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sip.*;
import javax.sip.address.*;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.*;
import javax.swing.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;
import java.util.Vector;
import java.net.*;
import javax.sip.Dialog;
import javax.sdp.*;

public class SipClient extends JFrame implements SipListener {
    // SIP stack components
    private SipFactory sipFactory;
    private SipStack sipStack;
    private SipProvider sipProvider;
    private MessageFactory messageFactory;
    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    private ListeningPoint listeningPoint;
    private Properties properties;

    // Configuration parameters
    private String transport;
    private String ip;
    private int port = 6060;
    private String protocol = "tcp";
    private int tag = new Random().nextInt();
    private Address contactAddress;
    private ContactHeader contactHeader;
    private SdpFactory sdpFactory;
    private Dialog dialogClient;

    // UI components
    private JTextArea textAreaMsgRecu;
    private JTextArea textAreaMsgSent;

    // RTP configuration
    private int rtpPort = 10000;  // Default RTP port

    /**
     * Constructor
     */
    public SipClient() {}

    /**
     * Set UI components for message display
     */
    public void setUiComponents(JTextArea textAreaMsgRecu, JTextArea textAreaMsgSent) {
        this.textAreaMsgRecu = textAreaMsgRecu;
        this.textAreaMsgSent = textAreaMsgSent;
    }

    /**
     * Initialize SIP stack and components
     */
    public void onOpen() {
        try {
            // Get local IP address
            ip = InetAddress.getLocalHost().getHostAddress();
            transport = "udp";

            // Initialize SIP factory
            sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");

            // Configure properties
            properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "stack");
            properties.setProperty("javax.sip.IP_ADDRESS", ip);
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "sip_debug.txt");
            properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "sip_server.txt");

            // Create SIP stack
            sipStack = sipFactory.createSipStack(properties);
            messageFactory = sipFactory.createMessageFactory();
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();

            // Create listening point and provider
            listeningPoint = sipStack.createListeningPoint(ip, port, transport);
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);

            // Create contact header
            contactAddress = addressFactory.createAddress("sip:" + ip + ":" + port);
            contactHeader = headerFactory.createContactHeader(contactAddress);

            System.out.println("SIP stack initialized successfully on " + ip + ":" + port);
        } catch (Exception e) {
            System.err.println("Error initializing SIP stack: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send INVITE request
     */
    public void onInvite(String destinationSIP) {
        try {
            // Parse destination address
            String ipAddress = destinationSIP.split(":")[1].split(";")[0];
            Address addressTo = addressFactory.createAddress("sip:" + ipAddress + ":6060");
            URI requestURI = addressTo.getURI();

            // Create headers
            ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
            ViaHeader viaHeader = headerFactory.createViaHeader(ip, port, transport, null);
            viaHeaders.add(viaHeader);

            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
            FromHeader fromHeader = headerFactory.createFromHeader(contactAddress, String.valueOf(tag));
            ToHeader toHeader = headerFactory.createToHeader(addressTo, null);

            // Create INVITE request
            Request request = messageFactory.createRequest(
                    requestURI, Request.INVITE, callIdHeader, cSeqHeader,
                    fromHeader, toHeader, viaHeaders, maxForwards
            );
            request.addHeader(contactHeader);

            // Add SDP content
            String sdpData = createSDPData();
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            request.setContent(sdpData, contentTypeHeader);

            // Send request
            ClientTransaction inviteTid = sipProvider.getNewClientTransaction(request);
            inviteTid.sendRequest();

            if (textAreaMsgSent != null) {
                textAreaMsgSent.append("INVITE sent:\n" + request.toString() + "\n");
            }
        } catch (Exception e) {
            System.err.println("Error sending INVITE: " + e.getMessage());
            if (textAreaMsgSent != null) {
                textAreaMsgSent.append("Error sending INVITE: " + e.getMessage() + "\n");
            }
        }
    }

    /**
     * Create SDP data for RTP session
     */
    private String createSDPData() {
        try {
            sdpFactory = SdpFactory.getInstance();
            SessionDescription sessDescr = sdpFactory.createSessionDescription();

            Version v = sdpFactory.createVersion(0);
            Origin o = sdpFactory.createOrigin("SipClient", 0, 0, "IN", "IP4", ip);
            SessionName s = sdpFactory.createSessionName("-");
            Connection c = sdpFactory.createConnection("IN", "IP4", ip);
            TimeDescription t = sdpFactory.createTimeDescription();
            Vector<TimeDescription> timeDescs = new Vector<>();
            timeDescs.add(t);

            // Configure audio media description
            String[] formats = {"0"}; // PCM μ-law format
            MediaDescription audioMedia = sdpFactory.createMediaDescription(
                    "audio", rtpPort, 1, "RTP/AVP", formats
            );

            Vector<MediaDescription> mediaDescs = new Vector<>();
            mediaDescs.add(audioMedia);

            // Set session description
            sessDescr.setVersion(v);
            sessDescr.setOrigin(o);
            sessDescr.setSessionName(s);
            sessDescr.setConnection(c);
            sessDescr.setTimeDescriptions(timeDescs);
            sessDescr.setMediaDescriptions(mediaDescs);

            return sessDescr.toString();
        } catch (Exception e) {
            System.err.println("Error creating SDP: " + e.getMessage());
            return "";
        }
    }

    /**
     * Send BYE request
     */
    public void onBye(java.awt.event.ActionEvent evt) {
        try {
            if (dialogClient == null) {
                System.out.println("No active dialog for BYE request");
                return;
            }

            Request byeRequest = dialogClient.createRequest(Request.BYE);
            ClientTransaction byeTransaction = sipProvider.getNewClientTransaction(byeRequest);
            dialogClient.sendRequest(byeTransaction);

            if (textAreaMsgSent != null) {
                textAreaMsgSent.append("BYE sent:\n" + byeRequest.toString() + "\n");
            }
        } catch (Exception e) {
            System.err.println("Error sending BYE: " + e.getMessage());
            if (textAreaMsgSent != null) {
                textAreaMsgSent.append("Error sending BYE: " + e.getMessage() + "\n");
            }
        }
    }

    /**
     * Process incoming requests
     */
    @Override
    public void processRequest(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            if (textAreaMsgRecu != null) {
                textAreaMsgRecu.append("Request received:\n" + request.toString() + "\n");
            }

            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            if (serverTransaction == null) {
                serverTransaction = sipProvider.getNewServerTransaction(request);
            }

            if (request.getMethod().equals(Request.INVITE)) {
                // Send RINGING
                Response ringingResponse = messageFactory.createResponse(Response.RINGING, request);
                serverTransaction.sendResponse(ringingResponse);

                // Send OK
                Response okResponse = messageFactory.createResponse(Response.OK, request);
                okResponse.addHeader(contactHeader);
                serverTransaction.sendResponse(okResponse);

                dialogClient = serverTransaction.getDialog();
            } else if (request.getMethod().equals(Request.BYE)) {
                Response okResponse = messageFactory.createResponse(Response.OK, request);
                serverTransaction.sendResponse(okResponse);
                dialogClient = null;
            }
        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
        }
    }

    /**
     * Process responses
     */
    @Override
    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            if (textAreaMsgRecu != null) {
                textAreaMsgRecu.append("Response received:\n" + response.toString() + "\n");
            }

            if (response.getStatusCode() == Response.OK) {
                CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
                if (cSeqHeader.getMethod().equals(Request.INVITE)) {
                    Dialog dialog = responseEvent.getClientTransaction().getDialog();
                    Request ackRequest = dialog.createAck(cSeqHeader.getSeqNumber());
                    dialog.sendAck(ackRequest);
                    dialogClient = dialog;
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing response: " + e.getMessage());
        }
    }

    // Required SipListener interface methods
    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        System.out.println("Timeout occurred");
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        System.out.println("IO Exception occurred");
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent event) {
        System.out.println("Transaction terminated");
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent event) {
        System.out.println("Dialog terminated");
    }
}