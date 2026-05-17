import shared.HashUtil;
import shared.Message;
import shared.SRGValue;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.HashMap;

public class SecureRandomServer {
    private static final int DEFAULT_PORT = 7000;
    private static final int DEFAULT_BUFFER_CAPACITY = 64;

    private final int port;
    private final HashMap<String, RandomBuffer> buffers;

    public SecureRandomServer(int port) {
        this.port = port;
        this.buffers = new HashMap<String, RandomBuffer>();
    }

    public static void main(String[] args) {
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        SecureRandomServer server = new SecureRandomServer(port);
        server.start();
    }

    public void start() {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("SRG listening on port " + port);

            while (true) {
                Socket client = serverSocket.accept();
                Thread t = new Thread(new ClientHandler(client, this));
                t.start();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public SRGValue getNextRandom(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("Secret key is required");
        }

        RandomBuffer buffer = getOrCreateBuffer(secret.trim());
        return buffer.take();
    }

    private RandomBuffer getOrCreateBuffer(String secret) {
        synchronized (buffers) {
            RandomBuffer buffer = buffers.get(secret);
            if (buffer != null) {
                return buffer;
            }

            RandomBuffer created = new RandomBuffer(DEFAULT_BUFFER_CAPACITY);
            buffers.put(secret, created);

            Thread producer = new Thread(new Producer(secret, created));
            producer.setDaemon(true);
            producer.start();
            return created;
        }
    }

    private static class Producer implements Runnable {
        private final String secret;
        private final RandomBuffer buffer;
        private final SecureRandom secureRandom;

        Producer(String secret, RandomBuffer buffer) {
            this.secret = secret;
            this.buffer = buffer;
            this.secureRandom = new SecureRandom();
        }

        @Override
        public void run() {
            while (true) {
                int number = secureRandom.nextInt(Integer.MAX_VALUE);
                String hash = HashUtil.sha256(number + secret);
                buffer.put(new SRGValue(number, hash));
            }
        }
    }

    private static class RandomBuffer {
        private final SRGValue[] values;
        private int head;
        private int tail;
        private int size;

        RandomBuffer(int capacity) {
            this.values = new SRGValue[capacity];
            this.head = 0;
            this.tail = 0;
            this.size = 0;
        }

        public synchronized void put(SRGValue value) {
            while (size == values.length) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            values[tail] = value;
            tail = (tail + 1) % values.length;
            size++;
            notifyAll();
        }

        public synchronized SRGValue take() {
            while (size == 0) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for random value", ex);
                }
            }

            SRGValue value = values[head];
            values[head] = null;
            head = (head + 1) % values.length;
            size--;
            notifyAll();
            return value;
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket client;
        private final SecureRandomServer server;

        ClientHandler(Socket client, SecureRandomServer server) {
            this.client = client;
            this.server = server;
        }

        @Override
        public void run() {
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            try {
                out = new ObjectOutputStream(client.getOutputStream());
                in = new ObjectInputStream(client.getInputStream());

                while (true) {
                    Message request = (Message) in.readObject();
                    if (!"SRG_GET".equals(request.getType())) {
                        out.writeObject(new Message("ERROR", "Unknown SRG operation"));
                        out.flush();
                        continue;
                    }

                    SRGValue value = server.getNextRandom(request.getContent());
                    out.writeObject(new Message("SUCCESS", "SRG value generated", value));
                    out.flush();
                }
            } catch (EOFException ignored) {
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (ClassNotFoundException ex) {
                ex.printStackTrace();
            } finally {
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
                    client.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
