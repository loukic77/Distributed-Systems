import gr.aueb.dist.shared.BetRequest;
import gr.aueb.dist.shared.Game;
import gr.aueb.dist.shared.GameInfo;
import gr.aueb.dist.shared.HashUtil;
import gr.aueb.dist.shared.SRGValue;
import gr.aueb.dist.shared.SearchFilter;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Worker {

    private static final HashMap<String, Game> GAME_STORE = new HashMap<String, Game>();
    private static SecureRandomClient srgClient;
    
    public static void main(String args[]) {
        int port=Integer.parseInt((args[0]));//we take port from command line, so we can have many workers
        String srgHost = args.length >= 2 ? args[1] : "127.0.0.1";
        int srgPort = args.length >= 3 ? Integer.parseInt(args[2]) : 7000;
        configureSecureRandomClient(srgHost, srgPort);
        new Worker().openServer(port);
    }
        public static synchronized void configureSecureRandomClient(String host, int port) {
            srgClient = new SecureRandomClient(host, port);
        }

    ServerSocket providerSocket;
    Socket connection = null;
    //new    
    public static synchronized boolean addRating(String gameName, int stars) {
        Game g = GAME_STORE.get(gameName);
        if (g == null) {
            return false;
        }
        g.addRating(stars);
        return true;
    }     

    public static synchronized boolean addGame(Game game) {
        if (game == null) {
            return false;
        }
        if (GAME_STORE.containsKey(game.getGameName())) {
            return false;
        }
        GAME_STORE.put(game.getGameName(), game);
        return true;
    }

    public static synchronized Game getGame(String gameName) {
        return GAME_STORE.get(gameName);
    }

    public static synchronized boolean deactivateGame(String gameName) {
        Game g = GAME_STORE.get(gameName);
        if (g == null) {
            return false;
        }
        g.deactivate();
        return true;
    }

    public static synchronized boolean updateRiskLevel(String gameName, String newRisk) {
        Game g = GAME_STORE.get(gameName);
        if (g == null) {
            return false;
        }
        g.updateRiskLevel(newRisk);
        return true;
    }

    public static synchronized List<String> listActiveGameNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (Game g : GAME_STORE.values()) {
            if (g.isActive()) {
                names.add(g.getGameName());
            }
        }
        return names;
    }

    public static synchronized List<GameInfo> listActiveGameInfo() {
        ArrayList<GameInfo> games = new ArrayList<GameInfo>();
        for (Game g : GAME_STORE.values()) {
            if (g.isActive()) {
                games.add(g.toGameInfo());
            }
        }
        return games;
    }

    public static synchronized List<GameInfo> searchGames(SearchFilter filter) {
        ArrayList<GameInfo> matches = new ArrayList<GameInfo>();
        for (Game g : GAME_STORE.values()) {
            if (!g.isActive()) {
                continue;
            }
            GameInfo info = g.toGameInfo();
            if (filter == null || filter.matches(info)) {
                matches.add(info);
            }
        }
        return matches;
    }

    public static synchronized Game.BetResult applyBet(BetRequest betRequest) {
        if (betRequest == null) {
            throw new IllegalArgumentException("Missing bet request");
        }
        Game g = GAME_STORE.get(betRequest.getGameName());
        if (g == null) {
            throw new IllegalArgumentException("Game not found: " + betRequest.getGameName());
        }

        if (srgClient == null) {
            throw new IllegalStateException("SRG client is not configured");
        }

        SRGValue secureRandom = srgClient.getSecureRandom(g.getHashKey());
        String localHash = HashUtil.sha256(secureRandom.getRandomNumber() + g.getHashKey());
        if (!localHash.equals(secureRandom.getHash())) {
            throw new IllegalStateException("Secure random hash verification failed");
        }

        int randomNumber = secureRandom.getRandomNumber();
        return g.applyBet(betRequest.getPlayerId(), betRequest.getAmount(), randomNumber);
    }

    public static synchronized HashMap<String, Double> mapProviderProfitLoss(String providerName) {
        HashMap<String, Double> mapOutput = new HashMap<String, Double>();
        for (Game g : GAME_STORE.values()) {
            if (g.hasProvider(providerName)) {
                mapOutput.put(g.getGameName(), g.getTotalHouseProfitLoss());
            }
        }
        return mapOutput;
    }

    public static synchronized HashMap<String, Double> mapPlayerProfitLoss(String playerId) {
        HashMap<String, Double> mapOutput = new HashMap<String, Double>();
        for (Game g : GAME_STORE.values()) {
            double value = g.getPlayerProfitLoss(playerId);
            if (value != 0.0) {
                mapOutput.put(g.getGameName(), value);
            }
        }
        return mapOutput;
    }

    void openServer(int port) {
        try {
            providerSocket = new ServerSocket(port, 10);
            System.out.println("Worker listening on "+port);

            while (true) {
                connection = providerSocket.accept();
                Thread t = new WorkerHandler(connection);
                t.start();
            }
        }  catch (IOException ioException) {
			ioException.printStackTrace();
		} finally {
			try {
				providerSocket.close();
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
    }

}
