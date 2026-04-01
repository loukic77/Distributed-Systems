import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

public class DummyPlayerConsole {
    public static void main(String[] args) {
        String host = args.length >= 1 ? args[0] : "127.0.0.1";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : 6000;

        try {
            Socket socket = new Socket(host, port);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            Scanner scanner = new Scanner(System.in);

            System.out.println("Dummy player connected to Master on " + host + ":" + port);
            printHelp();

            while (true) {
                System.out.print("player> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                if ("quit".equals(cmd) || "exit".equals(cmd)) {
                    out.writeObject(new Message("EXIT", ""));
                    out.flush();
                    Message resp = (Message) in.readObject();
                    System.out.println(resp.getType() + ": " + resp.getContent());
                    break;
                }

                try {
                    Message request = buildRequest(parts);
                    out.writeObject(request);
                    out.flush();

                    Message response = (Message) in.readObject();
                    printResponse(response);
                } catch (IllegalArgumentException ex) {
                    System.out.println("ERROR: " + ex.getMessage());
                    printHelp();
                }
            }

            scanner.close();
            in.close();
            out.close();
            socket.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static Message buildRequest(String[] parts) {
        String cmd = parts[0].toLowerCase();

        if ("search".equals(cmd)) {
            if (parts.length < 4) {
                throw new IllegalArgumentException("search <minStars> <risk|*> <betCategory|*>");
            }
            int minStars = Integer.parseInt(parts[1]);
            SearchFilter filter = new SearchFilter(minStars, parts[2], parts[3]);
            return new Message("PLAYER_SEARCH", "", filter);
        }

        if ("play".equals(cmd)) {
            if (parts.length < 4) {
                throw new IllegalArgumentException("play <playerId> <gameName> <amount>");
            }
            BetRequest req = new BetRequest(parts[1], parts[2], Double.parseDouble(parts[3]));
            return new Message("PLAYER_PLAY", req.getGameName(), req);
        }

        throw new IllegalArgumentException("Unknown command: " + cmd);
    }

    private static void printResponse(Message response) {
        System.out.println(response.getType() + ": " + response.getContent());
        Object payload = response.getPayload();

        if (payload instanceof List) {
            List<?> list = (List<?>) payload;
            for (Object item : list) {
                System.out.println("  " + item);
            }
        } else if (payload instanceof Game.BetResult) {
            Game.BetResult bet = (Game.BetResult) payload;
            System.out.println("  game=" + bet.getGameName());
            System.out.println("  player=" + bet.getPlayerId());
            System.out.println("  bet=" + bet.getBetAmount());
            System.out.println("  payout=" + bet.getPayout());
            System.out.println("  playerNet=" + bet.getPlayerNetProfitLoss());
            System.out.println("  houseNet=" + bet.getHouseNetProfitLoss());
            System.out.println("  jackpotHit=" + bet.isJackpotHit());
            System.out.println("  random=" + bet.getRandomNumber());
        }
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  search <minStars> <risk|*> <betCategory|*>");
        System.out.println("  play <playerId> <gameName> <amount>");
        System.out.println("  quit");
    }
}
