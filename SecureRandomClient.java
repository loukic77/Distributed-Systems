import gr.aueb.dist.shared.Message;
import gr.aueb.dist.shared.SRGValue;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class SecureRandomClient {
    private final String host;
    private final int port;
    private final Object lock;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public SecureRandomClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.lock = new Object();
    }

    public SRGValue getSecureRandom(String secret) {
        synchronized (lock) {
            try {
                ensureConnected();
                out.writeObject(new Message("SRG_GET", secret));
                out.flush();

                Message response = (Message) in.readObject();
                if (!"SUCCESS".equals(response.getType())) {
                    throw new IllegalStateException("SRG error: " + response.getContent());
                }
                if (!(response.getPayload() instanceof SRGValue)) {
                    throw new IllegalStateException("Invalid SRG response payload");
                }
                return (SRGValue) response.getPayload();
            } catch (IOException ex) {
                closeQuietly();
                throw new IllegalStateException("Failed to communicate with SRG", ex);
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Invalid SRG response class", ex);
            }
        }
    }

    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }

        closeQuietly();
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    private void closeQuietly() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        in = null;
        out = null;
        socket = null;
    }
}
