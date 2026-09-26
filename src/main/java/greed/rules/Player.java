package greed.rules;

import java.util.List;

public final class Player {
    public final String name;
    public final boolean computer;
    /** Set once for a computer, when the game starts. Humans stay null. */
    public Bot.Personality personality;
    public int score;
    public boolean onBoard;

    public Player(String name, boolean computer) {
        this.name = name;
        this.computer = computer;
    }

    /** The name as it is shown. A computer includes its personality. */
    public String label() {
        if (computer && personality != null) {
            return name + " (" + personality.adjective() + ")";
        }
        return name;
    }

    public static boolean taken(List<Player> seated, String name) {
        for (Player player : seated) {
            if (player.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
