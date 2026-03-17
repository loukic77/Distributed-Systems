import java.io.*;
import java.net.*;

public class Worker {
    
    public static void main(String args[]) {
        int port=Integer.parseInt((args[0]));//we take port from command line, so we can have many workers 
        new Worker().openServer(port);
    }
    ServerSocket providerSocket;
    Socket connection = null;

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
