package emi.sip.TransmitRTP;

import javax.media.*;
import javax.media.format.AudioFormat;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import java.io.IOException;
import java.net.MalformedURLException;

public class TransmitRTP {
    private Processor mediaProcessor;
    private DataSink dataSink;
    private DataSource source;

    static final Format[] FORMATS = new Format[] {new AudioFormat(AudioFormat.ULAW_RTP)};
    static final ContentDescriptor CONTENT_DESCRIPTOR = new ContentDescriptor(ContentDescriptor.RAW_RTP);

    public void startTransmission(String destinationIP) throws MalformedURLException, IOException, NoDataSourceException, NoProcessorException, CannotRealizeException, NoDataSinkException {
        // Create media locator for microphone
        MediaLocator locator = new MediaLocator("javasound://8000");

        // Create source
        source = Manager.createDataSource(locator);

        // Create and configure processor
        mediaProcessor = Manager.createRealizedProcessor(
                new ProcessorModel(source, FORMATS, CONTENT_DESCRIPTOR)
        );

        // Start the processor
        mediaProcessor.start();

        // Create output locator
        MediaLocator outputLocator = new MediaLocator("rtp://" + destinationIP + ":" + 10000 + "/audio/1");

        // Create and start data sink
        dataSink = Manager.createDataSink(mediaProcessor.getDataOutput(), outputLocator);
        dataSink.open();
        dataSink.start();

        System.out.println("Transmitting...");
    }

    public void stopTransmission() {
        try {
            if (dataSink != null) {
                dataSink.stop();
                dataSink.close();
                dataSink = null;
            }

            if (mediaProcessor != null) {
                mediaProcessor.stop();
                mediaProcessor.close();
                mediaProcessor.deallocate();
                mediaProcessor = null;
            }

            if (source != null) {
                source.disconnect();
                source = null;
            }

            System.out.println("Transmission stopped");
        } catch (Exception e) {
            System.err.println("Error stopping transmission: " + e.getMessage());
        }
    }

    // Main method for standalone testing
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("Usage: TransmitRTP <destination-ip>");
                return;
            }

            TransmitRTP transmitter = new TransmitRTP();
            transmitter.startTransmission(args[0]);

            // Keep the main thread alive
            while (true) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}