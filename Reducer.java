import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Reducer {
    private static final int DEFAULT_PORT = 6100;

    private final int port;

    public Reducer(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Reducer reducer = new Reducer(port);
        reducer.start();
    }

    public void start() {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Reducer listening on port " + port);

            while (true) {
                Socket client = serverSocket.accept();
                Thread t = new Thread(new ClientHandler(client));
                t.start();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket client;

        ClientHandler(Socket client) {
            this.client = client;
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
                    Message response = handleRequest(request);
                    out.writeObject(response);
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

        @SuppressWarnings("unchecked")
        private Message handleRequest(Message request) {
            try {
                Object payload = request.getPayload();
                if (!(payload instanceof List)) {
                    return new Message("ERROR", "Reducer payload must be List<Message>");
                }

                List<?> rawList = (List<?>) payload;
                ArrayList<Message> mapResults = new ArrayList<Message>();
                for (Object item : rawList) {
                    if (!(item instanceof Message)) {
                        return new Message("ERROR", "Reducer payload contains invalid entry");
                    }
                    mapResults.add((Message) item);
                }

                String type = request.getType();
                if ("REDUCE_GAME_LIST".equals(type) || "REDUCE_SEARCH".equals(type)) {
                    ArrayList<GameInfo> reduced = new ArrayList<GameInfo>();
                    for (Message result : mapResults) {
                        if (!"SUCCESS".equals(result.getType())) {
                            return result;
                        }
                        if (result.getPayload() instanceof List) {
                            List<?> list = (List<?>) result.getPayload();
                            for (Object item : list) {
                                if (item instanceof GameInfo) {
                                    reduced.add((GameInfo) item);
                                }
                            }
                        }
                    }
                    String content = "REDUCE_GAME_LIST".equals(type) ? "MapReduce list complete" : "MapReduce search complete";
                    return new Message("SUCCESS", content, reduced);
                }

                if ("REDUCE_PROVIDER_REPORT".equals(type) || "REDUCE_PLAYER_REPORT".equals(type)) {
                    HashMap<String, Double> reduced = new HashMap<String, Double>();
                    double total = 0.0;

                    for (Message result : mapResults) {
                        if (!"SUCCESS".equals(result.getType())) {
                            return result;
                        }
                        if (result.getPayload() instanceof HashMap) {
                            HashMap<?, ?> map = (HashMap<?, ?>) result.getPayload();
                            for (Object key : map.keySet()) {
                                if (!(key instanceof String)) {
                                    continue;
                                }
                                Object value = map.get(key);
                                if (!(value instanceof Double)) {
                                    continue;
                                }
                                String gameName = (String) key;
                                double gameValue = ((Double) value).doubleValue();
                                Double prev = reduced.get(gameName);
                                if (prev == null) {
                                    prev = 0.0;
                                }
                                reduced.put(gameName, prev + gameValue);
                            }
                        }
                    }

                    for (double value : reduced.values()) {
                        total += value;
                    }
                    reduced.put("TOTAL", total);

                    String content = "REDUCE_PROVIDER_REPORT".equals(type)
                        ? "MapReduce provider report complete"
                        : "MapReduce player report complete";
                    return new Message("SUCCESS", content, reduced);
                }

                return new Message("ERROR", "Unknown reducer operation: " + type);
            } catch (Exception ex) {
                return new Message("ERROR", ex.getMessage());
            }
        }
    }
}
