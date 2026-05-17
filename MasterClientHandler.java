import shared.Message;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;
import java.net.Socket;

public class MasterClientHandler extends Thread {
    private final Socket clientSocket;
    private final Master master;

    public MasterClientHandler(Socket clientSocket, Master master) {
        this.clientSocket = clientSocket;
        this.master = master;
    }

    public void run() {
        ObjectOutputStream out = null;
        ObjectInputStream in = null;

        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            in = new ObjectInputStream(clientSocket.getInputStream());

            while (true) {
                Message request = (Message) in.readObject();
                if ("EXIT".equals(request.getType())) {
                    out.writeObject(new Message("SUCCESS", "Connection closed"));
                    out.flush();
                    break;
                }

                Message response = master.handleClientRequest(request);
                out.writeObject(response);
                out.flush();
            }
        } catch (EOFException e) {
            System.out.println("Client disconnected");
        } catch (SocketException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("connection reset")) {
                System.out.println("Client connection reset");
            } else {
                System.out.println("Client socket error: " + e.getMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
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
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
