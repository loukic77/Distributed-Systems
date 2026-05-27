import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class ReducerClient {
    private final String host;
    private final int port;
    private final Object lock;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ReducerClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.lock = new Object();
    }

    public Message reduce(String type, String content, List<Message> mapResults) {
        synchronized (lock) {
            try {
                ensureConnected();
                out.writeObject(new Message(type, content, mapResults));
                out.flush();

                Object response = in.readObject();
                if (!(response instanceof Message)) {
                    throw new IllegalStateException("Invalid reducer response payload");
                }
                return (Message) response;
            } catch (IOException ex) {
                closeQuietly();
                throw new IllegalStateException("Failed to communicate with Reducer", ex);
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Invalid reducer response class", ex);
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
