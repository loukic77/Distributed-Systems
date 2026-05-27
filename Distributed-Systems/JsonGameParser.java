import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonGameParser {
    private JsonGameParser() {
    }

    public static Game parseFromFile(String jsonPath) {
        if (jsonPath == null || jsonPath.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON path is required");
        }

        try {
            String json = new String(Files.readAllBytes(Paths.get(jsonPath.trim())));
            return parse(json);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read JSON file: " + ex.getMessage(), ex);
        }
    }

    public static Game parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON content is empty");
        }

        String gameName = extractString(json, "GameName");
        String providerName = extractString(json, "ProviderName");
        int stars = extractInt(json, "Stars");
        int noOfVotes = extractInt(json, "NoOfVotes");
        String gameLogo = extractString(json, "GameLogo");
        double minBet = extractDouble(json, "MinBet");
        double maxBet = extractDouble(json, "MaxBet");
        String riskLevel = extractString(json, "RiskLevel");
        String hashKey = extractString(json, "HashKey");

        return new Game(
                gameName,
                providerName,
                stars,
                noOfVotes,
                gameLogo,
                minBet,
                maxBet,
                riskLevel,
                hashKey
        );
    }

    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            throw new IllegalArgumentException("Missing JSON field: " + key);
        }
        return m.group(1).trim();
    }

    private static int extractInt(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9]+)");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            throw new IllegalArgumentException("Missing JSON field: " + key);
        }
        return Integer.parseInt(m.group(1));
    }

    private static double extractDouble(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            throw new IllegalArgumentException("Missing JSON field: " + key);
        }
        return Double.parseDouble(m.group(1));
    }
}
