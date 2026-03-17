import java.io.*;
import java.net.*;

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

            Message response = new Message("SUCCESS", "Worker received: " + request.getContent());
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
}
