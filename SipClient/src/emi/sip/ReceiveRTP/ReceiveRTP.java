package emi.sip.ReceiveRTP;

import javax.media.*;
import javax.media.protocol.DataSource;

public class ReceiveRTP {
    private Player player;
    private DataSource source;
    private boolean isInitialized = false;

    public void startReception(String sourceIP) {
        try {
            // Create medialocator for RTP stream
            MediaLocator url = new MediaLocator("rtp://" + sourceIP + ":10000/audio/1");

            // Create data source
            source = Manager.createDataSource(url);

            // Create and configure player
            player = Manager.createPlayer(source);
            player.realize();

            // Add listener to track player state
            player.addControllerListener(new ControllerListener() {
                public void controllerUpdate(ControllerEvent event) {
                    if (event instanceof RealizeCompleteEvent) {
                        isInitialized = true;
                        player.start();
                        System.out.println("Receiving...");
                    }
                }
            });

            // Start the player
            player.prefetch();

        } catch (Exception ex) {
            System.err.println("Error starting reception: " + ex);
        }
    }

    public void stopReception() {
        try {
            if (player != null) {
                try {
                    player.stop();
                } catch (Exception e) {}

                try {
                    player.deallocate();
                } catch (Exception e) {}

                try {
                    player.close();
                } catch (Exception e) {}

                player = null;
            }

            if (source != null) {
                try {
                    source.disconnect();
                } catch (Exception e) {}
                source = null;
            }

            // Reset initialization flag
            isInitialized = false;

            System.out.println("Reception stopped");
        } catch (Exception e) {
            // Don't propagate the error since we're cleaning up
            System.out.println("Reception cleanup completed with some warnings");
        }
    }

    // Main method for standalone testing
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("Usage: ReceiveRTP <source-ip>");
                return;
            }

            ReceiveRTP receiver = new ReceiveRTP();
            receiver.startReception(args[0]);

            // Keep the main thread alive
            while (true) {
                Thread.sleep(1000);
            }
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }
}