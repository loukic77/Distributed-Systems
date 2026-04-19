import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.UUID;
import java.io.IOException;

public class Master {
    private static final int BASE_WORKER_PORT = 5000;
    private static final String DEFAULT_WORKER_HOST = "127.0.0.1";
    private static final int DEFAULT_MASTER_PORT = 6000;
    private static final int DEFAULT_REDUCER_PORT = 6100;

    private final int numWorkers;
    private final int masterPort;
    private final String reducerHost;
    private final int reducerPort;
    private final String[] workerHosts;
    private final int[] workerPorts;
    private final Socket[] workerSockets;
    private final ObjectOutputStream[] workerOut;
    private final ObjectInputStream[] workerIn;
    private final Object[] workerLocks;
    private final ReducerClient reducerClient;

    public Master(int numWorkers, int masterPort, String reducerHost, int reducerPort, String[] workerHosts, int[] workerPorts) {
        this.numWorkers = numWorkers;
        this.masterPort = masterPort;
        this.reducerHost = reducerHost;
        this.reducerPort = reducerPort;
        this.workerHosts = workerHosts;
        this.workerPorts = workerPorts;
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
            System.err.println("Usage: java Master <numWorkers> [masterPort] [reducerHost] [reducerPort] [workerEndpoints]");
            System.err.println("workerEndpoints format: host1:port1,host2:port2,... (must match numWorkers)");
            return;
        }

        int numWorkers = Integer.parseInt(args[0]);
        int masterPort = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_MASTER_PORT;
        String reducerHost = args.length >= 3 ? args[2] : DEFAULT_WORKER_HOST;
        int reducerPort = args.length >= 4 ? Integer.parseInt(args[3]) : DEFAULT_REDUCER_PORT;
        String[] workerHosts = new String[numWorkers];
        int[] workerPorts = new int[numWorkers];

        if (args.length >= 5) {
            parseWorkerEndpoints(args[4], numWorkers, workerHosts, workerPorts);
        } else {
            for (int i = 0; i < numWorkers; i++) {
                workerHosts[i] = DEFAULT_WORKER_HOST;
                workerPorts[i] = BASE_WORKER_PORT + i;
            }
        }

        Master master = new Master(numWorkers, masterPort, reducerHost, reducerPort, workerHosts, workerPorts);
        master.start();
    }

    private static void parseWorkerEndpoints(String endpointsArg, int numWorkers, String[] workerHosts, int[] workerPorts) {
        String[] endpoints = endpointsArg.split(",");
        if (endpoints.length != numWorkers) {
            throw new IllegalArgumentException("workerEndpoints count must equal numWorkers");
        }

        for (int i = 0; i < endpoints.length; i++) {
            String endpoint = endpoints[i].trim();
            int separator = endpoint.lastIndexOf(':');
            if (separator <= 0 || separator >= endpoint.length() - 1) {
                throw new IllegalArgumentException("Invalid worker endpoint: " + endpoint + " (expected host:port)");
            }

            String host = endpoint.substring(0, separator).trim();
            String portText = endpoint.substring(separator + 1).trim();
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Worker host must not be empty");
            }

            int port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid worker port: " + port);
            }

            workerHosts[i] = host;
            workerPorts[i] = port;
        }
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
            String host = workerHosts[i];
            int port = workerPorts[i];
            workerSockets[i] = new Socket(host, port);
            workerOut[i] = new ObjectOutputStream(workerSockets[i].getOutputStream());
            workerIn[i] = new ObjectInputStream(workerSockets[i].getInputStream());
            System.out.println("Master connected to Worker-" + i + " on " + host + ":" + port);
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
        return executeMapReduce("REDUCE_GAME_LIST", mapOperation, content, null);
    }

    private Message reduceSearch(SearchFilter filter) {
        return executeMapReduce("REDUCE_SEARCH", "SEARCH_GAMES", "", filter);
    }

    private Message reduceProviderReport(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            return new Message("ERROR", "Provider name is required");
        }

        return executeMapReduce("REDUCE_PROVIDER_REPORT", "MAP_PROVIDER_PROFIT_LOSS", providerName, null);
    }

    private Message reducePlayerReport(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            return new Message("ERROR", "Player id is required");
        }

        return executeMapReduce("REDUCE_PLAYER_REPORT", "MAP_PLAYER_PROFIT_LOSS", playerId, null);
    }

    private Message executeMapReduce(String reduceType, String workerType, String content, Object payload) {
        String mapId = UUID.randomUUID().toString();
        Message dispatchResult = dispatchWorkerMapToReducer(mapId, reduceType, workerType, content, payload);
        if (!"SUCCESS".equals(dispatchResult.getType())) {
            return dispatchResult;
        }

        return reducerClient.waitForReduced(mapId, numWorkers);
    }

    private Message dispatchWorkerMapToReducer(String mapId, String reduceType, String workerType, String content, Object payload) {
        final Message[] dispatchResults = new Message[numWorkers];
        Thread[] threads = new Thread[numWorkers];

        for (int i = 0; i < numWorkers; i++) {
            final int workerIdx = i;
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        HashMap<String, Object> task = new HashMap<String, Object>();
                        task.put("mapId", mapId);
                        task.put("reduceType", reduceType);
                        task.put("mapType", workerType);
                        task.put("content", content);
                        task.put("payload", payload);
                        task.put("reducerHost", reducerHost);
                        task.put("reducerPort", Integer.valueOf(reducerPort));
                        dispatchResults[workerIdx] = sendToWorker(workerIdx, new Message("MAP_TO_REDUCER", "", task));
                    } catch (EOFException ex) {
                        dispatchResults[workerIdx] = new Message("ERROR", "Worker disconnected: " + workerIdx);
                    } catch (IOException ex) {
                        dispatchResults[workerIdx] = new Message("ERROR", "Worker I/O error: " + ex.getMessage());
                    } catch (ClassNotFoundException ex) {
                        dispatchResults[workerIdx] = new Message("ERROR", "Worker protocol error: " + ex.getMessage());
                    }
                }
            });
            threads[i].start();
        }

        for (int i = 0; i < numWorkers; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return new Message("ERROR", "Interrupted while waiting map dispatch completion");
            }
        }

        for (int i = 0; i < numWorkers; i++) {
            Message result = dispatchResults[i];
            if (result == null) {
                return new Message("ERROR", "Missing map dispatch result from worker: " + i);
            }
            if (!"SUCCESS".equals(result.getType())) {
                return result;
            }
        }

        return new Message("SUCCESS", "All map tasks submitted");
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
}
