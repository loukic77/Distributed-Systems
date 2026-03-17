
import java.io.Serializable;
//import java.util.HashMap;
public class Message implements Serializable {
	String type;
	String content;//maybe hash
	//HashMap<String, Object> data;
	public Message(String type, String content) {
		
		this.type = type;
        this.content = content;
		// this.data = new HashMap<>();
	}
	public String getType() { return type; }
    public String getContent() { return content; }
	
	public void setType(String type) {
		this.type = type;
	}
	
	public void setContent(String content) {
		this.content = content;
	}
		
}
