package greed;

import java.util.List;

/**
 * Presentation and human input. {@link Game} owns the rules.
 * {@code winningScore} and {@code openingScore} are fixed when the view is built.
 */
public interface View {
    void intro();

    /** Line transcript prompts until 1..10. The screen reads one key; 0 means 10. */
    int readPlayerCount();

    /** The line "One human sits with the computer. Rook takes the other seat." */
    void soloAgainstComputer();

    Player readSeat(int seat, List<Player> seated, boolean onlyHuman);

    /** "{name} sits down for the computer." */
    void seatComputer(Player player);

    void goesFirst(Player player);

    void scores(List<Player> players);

    void startTurn(Player player);

    void startContinued(Player player, int diceLeft);

    void startNewHand(Player player);

    /** Faces just rolled. {@code kept} are dice already held earlier this hand. */
    void showRoll(Player player, int hand, int[] faces, int[] kept);

    /** Human. Returns indexes {@link Scorer} accepts. Owns the "Held …." line. */
    int[] chooseHold(Player player, int[] faces);

    /**
     * Bot. Owns "holds every scoring die." and "Held ….".
     * Throws if the scoring set is illegal.
     */
    int[] botHold(Player player, int[] faces);

    /** Hot dice. Human acknowledges; a bot announces the forced reroll. */
    void hotDice(Player player, int hand);

    /** Banking is closed because the hand is under the opening score. */
    void mustRoll(Player player, int hand, int left, int[] kept);

    /** @return true to bank */
    boolean chooseBank(Player player, int hand, int left, int[] kept);

    /**
     * Prints the continue preamble and asks.
     * {@code banked} is for the screen sentence. The line transcript ignores the amount.
     * @return true to take the leftover dice
     */
    boolean chooseContinue(Player player, String banker, int diceLeft, int banked);

    void bust(Player player, int pointsLost);

    void banked(Player player, int amount, int scoreBefore, boolean opened);

    /**
     * @return true if the human wants another game.
     * The line transcript prints the win block and returns false without reading.
     */
    boolean win(Player winner, List<Player> players);

    /** Line transcript: a blank line and "Goodbye." The screen restores the terminal first. */
    void goodbye();

    void flush();
}
