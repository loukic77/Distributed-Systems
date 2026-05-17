package shared;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class Game implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final double[] LOW_MULTIPLIERS =
            {0.0, 0.0, 0.0, 0.1, 0.5, 1.0, 1.1, 1.3, 2.0, 2.5};
    private static final double[] MEDIUM_MULTIPLIERS =
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0, 1.5, 2.5, 3.5};
    private static final double[] HIGH_MULTIPLIERS =
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 6.5};

    private final String gameName;
    private final String providerName;
    private int stars;
    private int noOfVotes;
    private final String gameLogo;
    private final double minBet;
    private final double maxBet;
    private String riskLevel;
    private final String hashKey;

    private final String betCategory;
    private double jackpot;

    private boolean active;
    private double totalHouseProfitLoss;
    private double totalPayout;
    private double totalBetAmount;
    private long totalBets;
    private final HashMap<String, Double> playerProfitLoss;

    public Game(
            String gameName,
            String providerName,
            int stars,
            int noOfVotes,
            String gameLogo,
            double minBet,
            double maxBet,
            String riskLevel,
            String hashKey
    ) {
        this.gameName = requireNonBlank(gameName, "GameName");
        this.providerName = requireNonBlank(providerName, "ProviderName");
        this.gameLogo = requireNonBlank(gameLogo, "GameLogo");
        this.hashKey = requireNonBlank(hashKey, "HashKey");

        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Stars must be in range [1, 5]");
        }
        if (noOfVotes < 0) {
            throw new IllegalArgumentException("NoOfVotes must be >= 0");
        }
        if (minBet <= 0.0 || maxBet <= 0.0 || minBet > maxBet) {
            throw new IllegalArgumentException("Invalid betting limits");
        }

        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.riskLevel = normalizeRiskLevel(riskLevel);

        this.betCategory = computeBetCategory(minBet);
        this.jackpot = computeJackpot(this.riskLevel);

        this.active = true;
        this.totalHouseProfitLoss = 0.0;
        this.totalPayout = 0.0;
        this.totalBetAmount = 0.0;
        this.totalBets = 0;
        this.playerProfitLoss = new HashMap<String, Double>();
        this.totalRatingSum = stars * noOfVotes;
    }

    public synchronized BetResult applyBet(String playerId, double amount, int randomNumber) {
        requireNonBlank(playerId, "playerId");
        if (!active) {
            throw new IllegalStateException("Game is inactive");
        }
        if (amount < minBet || amount > maxBet) {
            throw new IllegalArgumentException("Bet amount out of allowed range");
        }

        int absRandom = Math.abs(randomNumber);
        double payout;
        int indexUsed;
        boolean jackpotHit;

        if (absRandom % 100 == 0) {
            jackpotHit = true;
            indexUsed = -1;
            payout = amount * jackpot;
        } else {
            jackpotHit = false;
            indexUsed = absRandom % 10;
            payout = amount * getRiskMultipliers()[indexUsed];
        }

        double roundedAmount = roundMoney(amount);
        double roundedPayout = roundMoney(payout);
        double playerNet = roundMoney(roundedPayout - roundedAmount);
        double houseNet = roundMoney(-playerNet);

        totalBets++;
        totalBetAmount = roundMoney(totalBetAmount + roundedAmount);
        totalPayout = roundMoney(totalPayout + roundedPayout);
        totalHouseProfitLoss = roundMoney(totalHouseProfitLoss + houseNet);

        Double prev = playerProfitLoss.get(playerId);
        if (prev == null) {
            prev = 0.0;
        }
        playerProfitLoss.put(playerId, roundMoney(prev + playerNet));

        return new BetResult(
                gameName,
                playerId,
                roundedAmount,
                roundedPayout,
                playerNet,
                houseNet,
                jackpotHit,
                indexUsed,
                absRandom
        );
    }

    public synchronized void deactivate() {
        this.active = false;
    }

    public synchronized void activate() {
        this.active = true;
    }

    public synchronized void updateRiskLevel(String newRiskLevel) {
        this.riskLevel = normalizeRiskLevel(newRiskLevel);
        this.jackpot = computeJackpot(this.riskLevel);
    }

    private int totalRatingSum = 0;

    public synchronized void addRating(int newStars) {
        if (newStars < 1 || newStars > 5) {
            throw new IllegalArgumentException("Rating must be in range [1, 5]");
        }

        this.totalRatingSum += newStars;
        this.noOfVotes += 1;
        this.stars = Math.round((float) this.totalRatingSum / (float) this.noOfVotes);
    }

    public String getGameName() {
        return gameName;
    }

    public String getProviderName() {
        return providerName;
    }

    public boolean hasProvider(String provider) {
        if (provider == null) {
            return false;
        }
        return providerName.equalsIgnoreCase(provider.trim());
    }

    public synchronized int getStars() {
        return stars;
    }

    public synchronized int getNoOfVotes() {
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

    public synchronized String getRiskLevel() {
        return riskLevel;
    }

    public String getHashKey() {
        return hashKey;
    }

    public String getBetCategory() {
        return betCategory;
    }

    public synchronized double getJackpot() {
        return jackpot;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized double getTotalHouseProfitLoss() {
        return totalHouseProfitLoss;
    }

    public synchronized double getTotalPayout() {
        return totalPayout;
    }

    public synchronized double getTotalBetAmount() {
        return totalBetAmount;
    }

    public synchronized long getTotalBets() {
        return totalBets;
    }

    public synchronized double getPlayerProfitLoss(String playerId) {
        requireNonBlank(playerId, "playerId");
        Double value = playerProfitLoss.get(playerId);
        return value == null ? 0.0 : value;
    }

    public synchronized Map<String, Double> snapshotPlayerProfitLoss() {
        return new HashMap<String, Double>(playerProfitLoss);
    }

    public synchronized double[] getRiskMultipliers() {
        if ("low".equals(riskLevel)) {
            return LOW_MULTIPLIERS.clone();
        }
        if ("medium".equals(riskLevel)) {
            return MEDIUM_MULTIPLIERS.clone();
        }
        return HIGH_MULTIPLIERS.clone();
    }

    public synchronized GameInfo toGameInfo() {
        return new GameInfo(
                gameName,
                providerName,
                stars,
                noOfVotes,
                gameLogo,
                minBet,
                maxBet,
                riskLevel,
                betCategory,
                jackpot,
                active
        );
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeRiskLevel(String value) {
        String normalized = requireNonBlank(value, "RiskLevel").toLowerCase();
        if (!"low".equals(normalized) && !"medium".equals(normalized) && !"high".equals(normalized)) {
            throw new IllegalArgumentException("RiskLevel must be one of: low, medium, high");
        }
        return normalized;
    }

    private static String computeBetCategory(double minBet) {
        if (minBet >= 5.0) {
            return "$$$";
        }
        if (minBet >= 1.0) {
            return "$$";
        }
        return "$";
    }

    private static double computeJackpot(String riskLevel) {
        if ("low".equals(riskLevel)) {
            return 10.0;
        }
        if ("medium".equals(riskLevel)) {
            return 20.0;
        }
        return 40.0;
    }

    private static double roundMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static class BetResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String gameName;
        private final String playerId;
        private final double betAmount;
        private final double payout;
        private final double playerNetProfitLoss;
        private final double houseNetProfitLoss;
        private final boolean jackpotHit;
        private final int multiplierIndex;
        private final int randomNumber;
        private final double remainingBalance;

        public BetResult(
                String gameName,
                String playerId,
                double betAmount,
                double payout,
                double playerNetProfitLoss,
                double houseNetProfitLoss,
                boolean jackpotHit,
                int multiplierIndex,
                int randomNumber
        ) {
            this.gameName = gameName;
            this.playerId = playerId;
            this.betAmount = betAmount;
            this.payout = payout;
            this.playerNetProfitLoss = playerNetProfitLoss;
            this.houseNetProfitLoss = houseNetProfitLoss;
            this.jackpotHit = jackpotHit;
            this.multiplierIndex = multiplierIndex;
            this.randomNumber = randomNumber;
            this.remainingBalance = Double.NaN;
        }

        public BetResult(
                String gameName,
                String playerId,
                double betAmount,
                double payout,
                double playerNetProfitLoss,
                double houseNetProfitLoss,
                boolean jackpotHit,
                int multiplierIndex,
                int randomNumber,
                double remainingBalance
        ) {
            this.gameName = gameName;
            this.playerId = playerId;
            this.betAmount = betAmount;
            this.payout = payout;
            this.playerNetProfitLoss = playerNetProfitLoss;
            this.houseNetProfitLoss = houseNetProfitLoss;
            this.jackpotHit = jackpotHit;
            this.multiplierIndex = multiplierIndex;
            this.randomNumber = randomNumber;
            this.remainingBalance = remainingBalance;
        }

        public String getGameName() {
            return gameName;
        }

        public String getPlayerId() {
            return playerId;
        }

        public double getBetAmount() {
            return betAmount;
        }

        public double getPayout() {
            return payout;
        }

        public double getPlayerNetProfitLoss() {
            return playerNetProfitLoss;
        }

        public double getHouseNetProfitLoss() {
            return houseNetProfitLoss;
        }

        public boolean isJackpotHit() {
            return jackpotHit;
        }

        public int getMultiplierIndex() {
            return multiplierIndex;
        }

        public int getRandomNumber() {
            return randomNumber;
        }

        public double getRemainingBalance() {
            return remainingBalance;
        }
    }
}
