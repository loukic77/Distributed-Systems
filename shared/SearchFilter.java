package shared;

import java.io.Serializable;

public class SearchFilter implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int minStars;
    private final String riskLevel;
    private final String betCategory;

    public SearchFilter(int minStars, String riskLevel, String betCategory) {
        this.minStars = minStars;
        this.riskLevel = normalizeNullable(riskLevel);
        this.betCategory = normalizeNullable(betCategory);
    }

    public int getMinStars() {
        return minStars;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getBetCategory() {
        return betCategory;
    }

    public boolean matches(GameInfo info) {
        if (info.getStars() < minStars) {
            return false;
        }
        if (riskLevel != null && !riskLevel.equals(info.getRiskLevel())) {
            return false;
        }
        if (betCategory != null && !betCategory.equals(info.getBetCategory())) {
            return false;
        }
        return true;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) {
            return null;
        }
        return trimmed.toLowerCase();
    }
}
