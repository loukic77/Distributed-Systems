import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
//import java.util.HashMap; not used apparently
import java.util.List;
import java.io.IOException;

public class Master {
    private static final int BASE_WORKER_PORT = 5000;
    private static final int DEFAULT_MASTER_PORT = 6000;
    private static final int DEFAULT_REDUCER_PORT = 6100;

    private final int numWorkers;
    private final int masterPort;
    private final String reducerHost;
    private final int reducerPort;
    private final Socket[] workerSockets;
    private final ObjectOutputStream[] workerOut;
    private final ObjectInputStream[] workerIn;
    private final Object[] workerLocks;
    private final ReducerClient reducerClient;

    public Master(int numWorkers, int masterPort, String reducerHost, int reducerPort) {
        this.numWorkers = numWorkers;
        this.masterPort = masterPort;
        this.reducerHost = reducerHost;
        this.reducerPort = reducerPort;
        this.workerSockets = new Socket[numWorkers];
        this.workerOut = new ObjectOutputStream[numWorkers];
        this.workerIn = new ObjectInputStream[numWorkers];
        this.workerLocks = new Object[numWorkers];
        this.reducerClient = new ReducerClient(reducerHost, reducerPort);
        for (int i = 0; i < numWorkers; i++) {
            workerLocks[i] = new Object();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Master <numWorkers> [masterPort] [reducerHost] [reducerPort]");
            return;
        }

        int numWorkers = Integer.parseInt(args[0]);
        int masterPort = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_MASTER_PORT;
        String reducerHost = args.length >= 3 ? args[2] : "127.0.0.1";
        int reducerPort = args.length >= 4 ? Integer.parseInt(args[3]) : DEFAULT_REDUCER_PORT;

        Master master = new Master(numWorkers, masterPort, reducerHost, reducerPort);
        master.start();
    }

    public void start() {
        try {
            connectWorkers();
            System.out.println("Master connected to Reducer on " + reducerHost + ":" + reducerPort);
            startClientServer();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            closeWorkerConnections();
        }
    }

    private void connectWorkers() throws IOException {
        for (int i = 0; i < numWorkers; i++) {
            int port = BASE_WORKER_PORT + i;
            workerSockets[i] = new Socket("127.0.0.1", port);
            workerOut[i] = new ObjectOutputStream(workerSockets[i].getOutputStream());
            workerIn[i] = new ObjectInputStream(workerSockets[i].getInputStream());
            System.out.println("Master connected to Worker-" + i + " on port " + port);
        }
    }

    private void startClientServer() throws IOException {
        
        System.out.println("Master listening for clients on port " + masterPort);
        try(ServerSocket serverSocket = new ServerSocket(masterPort)){
            while (true) {
                Socket client = serverSocket.accept();
                Thread t = new MasterClientHandler(client, this);
                t.start();
            }
        }
    }
    

    public Message handleClientRequest(Message request) {
        try {
            String type = request.getType();

            if ("MANAGER_ADD_GAME".equals(type) || "ADD_GAME".equals(type)) {
                if (!(request.getPayload() instanceof Game)) {
                    return new Message("ERROR", "MANAGER_ADD_GAME payload must be Game");
                }
                Game game = (Game) request.getPayload();
                int workerIdx = getWorkerIndexForGame(game.getGameName());
                return sendToWorker(workerIdx, new Message("ADD_GAME", game.getGameName(), game));
            }

            if ("MANAGER_ADD_GAME_JSON".equals(type)) {
                String jsonPath = request.getContent();
                Game game = JsonGameParser.parseFromFile(jsonPath);
                int workerIdx = getWorkerIndexForGame(game.getGameName());
                return sendToWorker(workerIdx, new Message("ADD_GAME", game.getGameName(), game));
            }

            if ("MANAGER_REMOVE_GAME".equals(type) || "REMOVE_GAME".equals(type)) {
                int workerIdx = getWorkerIndexForGame(request.getContent());
                return sendToWorker(workerIdx, new Message("REMOVE_GAME", request.getContent()));
            }

            if ("MANAGER_UPDATE_RISK".equals(type) || "UPDATE_RISK".equals(type)) {
                if (!(request.getPayload() instanceof String)) {
                    return new Message("ERROR", "MANAGER_UPDATE_RISK payload must be String risk level");
                }
                String gameName = request.getContent();
                int workerIdx = getWorkerIndexForGame(gameName);
                return sendToWorker(workerIdx, new Message("UPDATE_RISK", gameName, request.getPayload()));
            }

            if ("MANAGER_LIST_GAMES".equals(type) || "LIST_GAMES".equals(type)) {
                return reduceGameList("LIST_ACTIVE_GAME_INFO", "");
            }

            if ("PLAYER_SEARCH".equals(type) || "SEARCH".equals(type)) {
                if (!(request.getPayload() instanceof SearchFilter)) {
                    return new Message("ERROR", "PLAYER_SEARCH payload must be SearchFilter");
                }
                SearchFilter filter = (SearchFilter) request.getPayload();
                return reduceSearch(filter);
            }

            if ("PLAYER_PLAY".equals(type) || "PLAY".equals(type)) {
                if (!(request.getPayload() instanceof BetRequest)) {
                    return new Message("ERROR", "PLAYER_PLAY payload must be BetRequest");
                }
                BetRequest betRequest = (BetRequest) request.getPayload();
                int workerIdx = getWorkerIndexForGame(betRequest.getGameName());
                return sendToWorker(workerIdx, new Message("PLAY_GAME", betRequest.getGameName(), betRequest));
            }

            if ("MANAGER_PROVIDER_REPORT".equals(type) || "PROVIDER_REPORT".equals(type)) {
                return reduceProviderReport(request.getContent());
            }

            if ("MANAGER_PLAYER_REPORT".equals(type) || "PLAYER_REPORT".equals(type)) {
                return reducePlayerReport(request.getContent());
            }

            if ("PING".equals(type)) {
                return new Message("SUCCESS", "PONG");
            }
            //new
            if ("PLAYER_RATE".equals(type)) {
                String gameName = request.getContent();
                Integer stars = (Integer) request.getPayload();
                int workerIdx = getWorkerIndexForGame(gameName);
                return sendToWorker(workerIdx, new Message("RATE_GAME", gameName, stars));
            }

            return new Message("ERROR", "Unknown client request type: " + type);
        } catch (IOException ex) {
            return new Message("ERROR", "I/O error: " + ex.getMessage());
        } catch (ClassNotFoundException ex) {
            return new Message("ERROR", "Protocol error: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return new Message("ERROR", ex.getMessage());
        }
    }

    private int getWorkerIndexForGame(String gameName) {
        if (gameName == null || gameName.trim().isEmpty()) {
            throw new IllegalArgumentException("Game name is required");
        }
        return Math.abs(gameName.hashCode()) % numWorkers;
    }

    private Message sendToWorker(int workerIdx, Message request) throws IOException, ClassNotFoundException {
        synchronized (workerLocks[workerIdx]) {
            workerOut[workerIdx].writeObject(request);
            workerOut[workerIdx].flush();
            return (Message) workerIn[workerIdx].readObject();
        }
    }

    private Message reduceGameList(String mapOperation, String content) {
        List<Message> mapResults = executeWorkerMap(mapOperation, content, null);
        return reducerClient.reduce("REDUCE_GAME_LIST", "", mapResults);
    }

    private Message reduceSearch(SearchFilter filter) {
        List<Message> mapResults = executeWorkerMap("SEARCH_GAMES", "", filter);
        return reducerClient.reduce("REDUCE_SEARCH", "", mapResults);
    }

    private Message reduceProviderReport(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            return new Message("ERROR", "Provider name is required");
        }

        List<Message> mapResults = executeWorkerMap("MAP_PROVIDER_PROFIT_LOSS", providerName, null);
        return reducerClient.reduce("REDUCE_PROVIDER_REPORT", providerName, mapResults);
    }

    private Message reducePlayerReport(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            return new Message("ERROR", "Player id is required");
        }

        List<Message> mapResults = executeWorkerMap("MAP_PLAYER_PROFIT_LOSS", playerId, null);
        return reducerClient.reduce("REDUCE_PLAYER_REPORT", playerId, mapResults);
    }

    private List<Message> executeWorkerMap(String workerType, String content, Object payload) {
        final MapCollector collector = new MapCollector(numWorkers);

        for (int i = 0; i < numWorkers; i++) {
            final int workerIdx = i;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    Message result;
                    try {
                        result = sendToWorker(workerIdx, new Message(workerType, content, payload));
                    } catch (EOFException ex) {
                        result = new Message("ERROR", "Worker disconnected: " + workerIdx);
                    } catch (IOException ex) {
                        result = new Message("ERROR", "Worker I/O error: " + ex.getMessage());
                    } catch (ClassNotFoundException ex) {
                        result = new Message("ERROR", "Worker protocol error: " + ex.getMessage());
                    }
                    collector.set(workerIdx, result);
                }
            });
            t.start();
        }

        return collector.awaitAll();
    }

    private void closeWorkerConnections() {
        for (int i = 0; i < numWorkers; i++) {
            try {
                if (workerIn[i] != null) {
                    workerIn[i].close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (workerOut[i] != null) {
                    workerOut[i].close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (workerSockets[i] != null) {
                    workerSockets[i].close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static class MapCollector {
        private final Message[] results;
        private int completed;

        MapCollector(int size) {
            this.results = new Message[size];
            this.completed = 0;
        }

        public synchronized void set(int index, Message value) {
            results[index] = value;
            completed++;
            notifyAll();
        }

        public synchronized List<Message> awaitAll() {
            while (completed < results.length) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            ArrayList<Message> list = new ArrayList<>();//new
            for (Message result : results) {
                if (result == null) {
                    list.add(new Message("ERROR", "Missing map output"));
                } else {
                    list.add(result);
                }
            }
            return list;
        }
    }
}
