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
    private static final HashMap<String, ReduceJobState> JOBS = new HashMap<String, ReduceJobState>();
    private static final HashMap<String, WaitingMaster> WAITING_MASTERS = new HashMap<String, WaitingMaster>();

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
                    Message response = handleRequest(request, out);
                    if (response != null) {
                        sendResponse(out, response);
                    }
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
        private Message handleRequest(Message request, ObjectOutputStream out) {
            try {
                if ("MAP_SUBMIT".equals(request.getType())) {
                    if (!(request.getPayload() instanceof HashMap)) {
                        return new Message("ERROR", "MAP_SUBMIT payload must be HashMap");
                    }

                    HashMap<?, ?> rawSubmission = (HashMap<?, ?>) request.getPayload();
                    Object mapIdObj = rawSubmission.get("mapId");
                    Object reduceTypeObj = rawSubmission.get("reduceType");
                    Object mapResultObj = rawSubmission.get("mapResult");
                    if (!(mapIdObj instanceof String) || !(reduceTypeObj instanceof String)
                            || !(mapResultObj instanceof Message)) {
                        return new Message("ERROR", "Invalid MAP_SUBMIT payload fields");
                    }

                    String mapId = (String) mapIdObj;
                    String reduceType = (String) reduceTypeObj;
                    Message mapResult = (Message) mapResultObj;
                    Submission submission = new Submission(mapId, reduceType, mapResult);
                    return acceptMapSubmission(submission);
                }

                if ("REDUCE_WAIT".equals(request.getType())) {
                    if (!(request.getPayload() instanceof Integer)) {
                        return new Message("ERROR", "REDUCE_WAIT payload must be Integer expected worker count");
                    }
                    int expectedCount = ((Integer) request.getPayload()).intValue();
                    return registerWaiter(request.getContent(), expectedCount, out);
                }

                return new Message("ERROR", "Unknown reducer request type: " + request.getType());
            } catch (Exception ex) {
                return new Message("ERROR", ex.getMessage());
            }
        }

        private void sendResponse(ObjectOutputStream out, Message response) throws IOException {
            synchronized (out) {
                out.writeObject(response);
                out.flush();
            }
        }

        private Message acceptMapSubmission(Submission submission) {
            String mapId = submission.getMapId();
            if (mapId == null || mapId.trim().isEmpty()) {
                return new Message("ERROR", "Invalid map id");
            }

            synchronized (JOBS) {
                ReduceJobState state = JOBS.get(mapId);
                if (state == null) {
                    state = new ReduceJobState(submission.getReduceType());
                    JOBS.put(mapId, state);
                }
                state.add(submission.getMapResult());
            }

            tryCompleteAndNotify(mapId);

            return new Message("SUCCESS", "Map submission accepted", mapId);
        }

        private Message registerWaiter(String mapId, int expectedCount, ObjectOutputStream out) {
            if (mapId == null || mapId.trim().isEmpty()) {
                return new Message("ERROR", "Invalid map id");
            }
            if (expectedCount <= 0) {
                return new Message("ERROR", "Expected worker count must be positive");
            }

            synchronized (WAITING_MASTERS) {
                WAITING_MASTERS.put(mapId, new WaitingMaster(expectedCount, out));
            }

            tryCompleteAndNotify(mapId);
            return null;
        }

        private void tryCompleteAndNotify(String mapId) {
            ReduceJobState state;
            WaitingMaster waiter;

            synchronized (JOBS) {
                state = JOBS.get(mapId);
            }
            if (state == null) {
                return;
            }

            synchronized (WAITING_MASTERS) {
                waiter = WAITING_MASTERS.get(mapId);
            }
            if (waiter == null) {
                return;
            }

            if (state.size() < waiter.expectedCount) {
                return;
            }

            Message reduced = reduceByType(state.getReduceType(), state.snapshot());

            try {
                sendResponse(waiter.out, reduced);
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            synchronized (JOBS) {
                JOBS.remove(mapId);
            }
            synchronized (WAITING_MASTERS) {
                WAITING_MASTERS.remove(mapId);
            }
        }

        private Message reduceByType(String type, List<Message> mapResults) {
            if (type == null || type.trim().isEmpty()) {
                return new Message("ERROR", "Missing reduce type for job");
            }

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
        }

    }

    private static class ReduceJobState {
        private final String reduceType;
        private final ArrayList<Message> mapResults;

        ReduceJobState(String reduceType) {
            this.reduceType = reduceType;
            this.mapResults = new ArrayList<Message>();
        }

        public synchronized void add(Message mapResult) {
            mapResults.add(mapResult);
        }

        public synchronized int size() {
            return mapResults.size();
        }

        public synchronized List<Message> snapshot() {
            return new ArrayList<Message>(mapResults);
        }

        public String getReduceType() {
            return reduceType;
        }
    }

    private static class Submission {
        private final String mapId;
        private final String reduceType;
        private final Message mapResult;

        Submission(String mapId, String reduceType, Message mapResult) {
            this.mapId = mapId;
            this.reduceType = reduceType;
            this.mapResult = mapResult;
        }

        public String getMapId() {
            return mapId;
        }

        public String getReduceType() {
            return reduceType;
        }

        public Message getMapResult() {
            return mapResult;
        }
    }

    private static class WaitingMaster {
        private final int expectedCount;
        private final ObjectOutputStream out;

        WaitingMaster(int expectedCount, ObjectOutputStream out) {
            this.expectedCount = expectedCount;
            this.out = out;
        }
    }
}
