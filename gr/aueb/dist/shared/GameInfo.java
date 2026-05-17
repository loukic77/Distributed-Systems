package gr.aueb.dist.shared;

import java.io.Serializable;

public class GameInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String gameName;
    private final String providerName;
    private final int stars;
    private final int noOfVotes;
    private final String gameLogo;
    private final double minBet;
    private final double maxBet;
    private final String riskLevel;
    private final String betCategory;
    private final double jackpot;
    private final boolean active;

    public GameInfo(
            String gameName,
            String providerName,
            int stars,
            int noOfVotes,
            String gameLogo,
            double minBet,
            double maxBet,
            String riskLevel,
            String betCategory,
            double jackpot,
            boolean active
    ) {
        this.gameName = gameName;
        this.providerName = providerName;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.gameLogo = gameLogo;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.riskLevel = riskLevel;
        this.betCategory = betCategory;
        this.jackpot = jackpot;
        this.active = active;
    }

    public String getGameName() {
        return gameName;
    }

    public String getProviderName() {
        return providerName;
    }

    public int getStars() {
        return stars;
    }

    public int getNoOfVotes() {
        return noOfVotes;
    }

    public String getGameLogo() {
        return gameLogo;
    }

    public double getMinBet() {
        return minBet;
    }

    public double getMaxBet() {
        return maxBet;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getBetCategory() {
        return betCategory;
    }

    public double getJackpot() {
        return jackpot;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        StringBuilder starsDisplay = new StringBuilder();
        for (int i = 0; i < stars; i++) {
            starsDisplay.append("*");
            if (i < stars - 1) {
                starsDisplay.append(" ");
            }
        }
        return gameName + " [provider=" + providerName
                + ", stars= " + starsDisplay.toString()
                + ", risk=" + riskLevel
                + ", betCategory=" + betCategory
                + ", min=" + minBet
                + ", max=" + maxBet
                + ", jackpot=" + jackpot
                + "]";
    }
}
