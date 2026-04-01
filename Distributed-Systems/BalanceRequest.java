import java.io.Serializable;

public class BalanceRequest implements Serializable {

    private String playerId;
    private double amount;

    public BalanceRequest(String playerId, double amount) {
        this.playerId = playerId;
        this.amount = amount;
    }

    public String getPlayerId() {
        return playerId;
    }

    public double getAmount() {
        return amount;
    }
}