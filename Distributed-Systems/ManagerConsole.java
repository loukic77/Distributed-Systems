import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ManagerConsole {
    public static void main(String[] args) {
        String host = args.length >= 1 ? args[0] : "127.0.0.1";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : 6000;

        try {
            Socket socket = new Socket(host, port);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            Scanner scanner = new Scanner(System.in);

            System.out.println("Manager console connected to Master on " + host + ":" + port);
            printHelp();

            while (true) {
                System.out.print("manager> ");
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

        if ("add".equals(cmd)) {
            if (parts.length < 10) {
                throw new IllegalArgumentException("add <gameName> <provider> <stars> <votes> <logoPath> <minBet> <maxBet> <risk> <hashKey>");
            }
            Game game = new Game(
                    parts[1],
                    parts[2],
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    parts[5],
                    Double.parseDouble(parts[6]),
                    Double.parseDouble(parts[7]),
                    parts[8],
                    parts[9]
            );
            return new Message("MANAGER_ADD_GAME", game.getGameName(), game);
        }

        if ("addjson".equals(cmd)) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("addjson <jsonPath>");
            }
            return new Message("MANAGER_ADD_GAME_JSON", parts[1]);
        }

        if ("remove".equals(cmd)) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("remove <gameName>");
            }
            return new Message("MANAGER_REMOVE_GAME", parts[1]);
        }

        if ("risk".equals(cmd)) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("risk <gameName> <low|medium|high>");
            }
            return new Message("MANAGER_UPDATE_RISK", parts[1], parts[2]);
        }

        if ("list".equals(cmd)) {
            return new Message("MANAGER_LIST_GAMES", "");
        }

        if ("provider".equals(cmd)) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("provider <providerName>");
            }
            return new Message("MANAGER_PROVIDER_REPORT", parts[1]);
        }

        if ("player".equals(cmd)) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("player <playerId>");
            }
            return new Message("MANAGER_PLAYER_REPORT", parts[1]);
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
        } else if (payload instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) payload;
            for (Object key : map.keySet()) {
                System.out.println("  " + key + " -> " + map.get(key));
            }
        }
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  add <gameName> <provider> <stars> <votes> <logoPath> <minBet> <maxBet> <risk> <hashKey>");
        System.out.println("  addjson <jsonPath>");
        System.out.println("  remove <gameName>");
        System.out.println("  risk <gameName> <low|medium|high>");
        System.out.println("  list");
        System.out.println("  provider <providerName>");
        System.out.println("  player <playerId>");
        System.out.println("  quit");
    }
}
