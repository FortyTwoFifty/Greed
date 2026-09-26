package greed;

final class Player {
    final String name;
    final boolean computer;
    /** Set once for a computer, when the game starts. Humans stay null. */
    Bot.Personality personality;
    int score;
    boolean onBoard;

    Player(String name, boolean computer) {
        this.name = name;
        this.computer = computer;
    }

    /** The name as it is shown. A computer includes its personality. */
    String label() {
        if (computer && personality != null) {
            return name + " (" + personality.adjective() + ")";
        }
        return name;
    }
}
