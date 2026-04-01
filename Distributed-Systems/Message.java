
import java.io.Serializable;

public class Message implements Serializable {
	private static final long serialVersionUID = 1L;

	String type;
	String content;
	Object payload;

	public Message(String type, String content) {
		this(type, content, null);
	}

	public Message(String type, String content, Object payload) {
		this.type = type;
		this.content = content;
		this.payload = payload;
	}

	public String getType() {
		return type;
	}

	public String getContent() {
		return content;
	}

	public Object getPayload() {
		return payload;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public void setPayload(Object payload) {
		this.payload = payload;
	}

}
