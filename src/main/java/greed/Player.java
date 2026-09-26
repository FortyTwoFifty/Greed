package greed;

final class Player {
    final String name;
    final boolean computer;
    int score;
    boolean onBoard;

    Player(String name, boolean computer) {
        this.name = name;
        this.computer = computer;
    }
}
