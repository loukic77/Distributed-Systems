import java.io.*;
import java.net.*;


public class Master extends Thread {


    public static void main(String args[]) {
        int numWorkers=Integer.parseInt(args[0]);//take workers as input in cmd
        Socket[] requestSocket = new Socket[numWorkers];
        ObjectOutputStream[] out =  new ObjectOutputStream[numWorkers];
        ObjectInputStream[] in = new ObjectInputStream[numWorkers];
        
        try {
            for (int i = 0; i < numWorkers; i++) {
                int port=5000+i;
                requestSocket[i] = new Socket("127.0.0.1", port);
                out[i] = new ObjectOutputStream(requestSocket[i].getOutputStream());
                in[i] = new ObjectInputStream(requestSocket[i].getInputStream());
                System.out.println("Master connected to Worker");
            
            }
            //ai generated Test
             String[] gameNames = {"Roulette", "Blackjack", "Poker"};
            for (String gameName : gameNames) {
                int workerIdx = Math.abs(gameName.hashCode()) % numWorkers;//to share games with the workers
                System.out.println(gameName + " -> Worker-" + workerIdx);

                Message msg = new Message("ADD_GAME", gameName);
                out[workerIdx].writeObject(msg);
                out[workerIdx].flush();

                Message response = (Message) in[workerIdx].readObject();
                System.out.println("  Answer: " + response.getContent());
            }
                /* 
                out.writeObject(msg);
                out.flush();

                Message response = (Message) in.readObject();
                System.out.println("Worker answered: " + response.getContent());
                */
            
        } catch (UnknownHostException unknownHost) {
			System.err.println("You are trying to connect to an unknown host!");
		} catch (IOException ioException) {
			ioException.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
                for (int i = 0; i < numWorkers; i++) {
				    in[i].close();	out[i].close();
				    requestSocket[i].close();
                }

            } catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}


    }    
}
