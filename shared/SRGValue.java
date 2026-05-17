package shared;

import java.io.Serializable;

public class SRGValue implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int randomNumber;
    private final String hash;

    public SRGValue(int randomNumber, String hash) {
        this.randomNumber = randomNumber;
        this.hash = hash;
    }

    public int getRandomNumber() {
        return randomNumber;
    }

    public String getHash() {
        return hash;
    }
}
