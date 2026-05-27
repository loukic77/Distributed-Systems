import java.io.*;
import java.net.*;
import java.util.List;

public class WorkerHandler extends Thread {
    
    ObjectInputStream in;
    ObjectOutputStream out;

    public WorkerHandler(Socket connection){
       try {
            out = new ObjectOutputStream(connection.getOutputStream());
            in = new ObjectInputStream(connection.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }
    public void run() {
        try {
            while(true){
                Message request = (Message) in.readObject();
                System.out.println("Worker got: " + request.getType() + " -> " + request.getContent());

                Message response = handleRequest(request);
                out.writeObject(response);
                out.flush();
            }
        } catch (EOFException e) {
        System.out.println("Master disconnected");
        } catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				in.close();
				out.close();
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
    }

    private Message handleRequest(Message request) {
        try {
            String type = request.getType();
            if ("ADD_GAME".equals(type)) {
                if (!(request.getPayload() instanceof Game)) {
                    return new Message("ERROR", "ADD_GAME payload must be Game");
                }
                Game game = (Game) request.getPayload();
                boolean inserted = Worker.addGame(game);
                if (!inserted) {
                    return new Message("ERROR", "Game already exists: " + game.getGameName());
                }
                return new Message("SUCCESS", "Game added: " + game.getGameName());
            }

            if ("REMOVE_GAME".equals(type)) {
                boolean removed = Worker.deactivateGame(request.getContent());
                if (!removed) {
                    return new Message("ERROR", "Game not found: " + request.getContent());
                }
                return new Message("SUCCESS", "Game deactivated: " + request.getContent());
            }

            if ("UPDATE_RISK".equals(type)) {
                String gameName = request.getContent();
                if (!(request.getPayload() instanceof String)) {
                    return new Message("ERROR", "UPDATE_RISK payload must be risk level string");
                }
                String risk = (String) request.getPayload();
                boolean updated = Worker.updateRiskLevel(gameName, risk);
                if (!updated) {
                    return new Message("ERROR", "Game not found: " + gameName);
                }
                return new Message("SUCCESS", "Risk updated for game: " + gameName + " -> " + risk);
            }

            if ("LIST_ACTIVE_GAMES".equals(type)) {
                List<String> names = Worker.listActiveGameNames();
                return new Message("SUCCESS", "Active games listed", names);
            }

            if ("LIST_ACTIVE_GAME_INFO".equals(type)) {
                List<GameInfo> games = Worker.listActiveGameInfo();
                return new Message("SUCCESS", "Active games listed", games);
            }

            if ("SEARCH_GAMES".equals(type)) {
                if (!(request.getPayload() instanceof SearchFilter)) {
                    return new Message("ERROR", "SEARCH_GAMES payload must be SearchFilter");
                }
                SearchFilter filter = (SearchFilter) request.getPayload();
                List<GameInfo> games = Worker.searchGames(filter);
                return new Message("SUCCESS", "Search map output", games);
            }

            if ("PLAY_GAME".equals(type)) {
                if (!(request.getPayload() instanceof BetRequest)) {
                    return new Message("ERROR", "PLAY_GAME payload must be BetRequest");
                }
                BetRequest betRequest = (BetRequest) request.getPayload();
                Game.BetResult result = Worker.applyBet(betRequest);
                return new Message("SUCCESS", "Bet processed", result);
            }

            if ("MAP_PROVIDER_PROFIT_LOSS".equals(type)) {
                String providerName = request.getContent();
                return new Message("SUCCESS", "Provider map output", Worker.mapProviderProfitLoss(providerName));
            }

            if ("MAP_PLAYER_PROFIT_LOSS".equals(type)) {
                String playerId = request.getContent();
                return new Message("SUCCESS", "Player map output", Worker.mapPlayerProfitLoss(playerId));
            }

            return new Message("ERROR", "Unknown operation: " + type);
        } catch (IllegalArgumentException ex) {
            return new Message("ERROR", ex.getMessage());
        } catch (IllegalStateException ex) {
            return new Message("ERROR", ex.getMessage());
        }
    }
}
