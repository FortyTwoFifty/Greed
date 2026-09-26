package greed;

import java.util.List;

/**
 * Rook, a computer player. Holds every scoring die. Banks when the leftover
 * roll is a poor risk for the points already in hand, and only picks up a
 * continued hand when four dice are left — fewer than that starts too far
 * behind a fresh roll of five, because a continued hand starts at zero.
 */
final class Bot {
    static final String NAME = "Rook";

    record Choice(boolean yes, String reason) {}

    private Bot() {}

    static int[] hold(int[] roll) {
        int[] indexes = Scorer.scoringIndexes(roll);
        if (indexes.length == 0) {
            throw new IllegalStateException("bot was asked to hold a roll that cannot score");
        }
        return indexes;
    }

    static Choice bank(int hand, int diceLeft, boolean onBoard, int score, int opening, int winning) {
        boolean legal = onBoard || hand >= opening;
        if (!legal) {
            return new Choice(false, "rolls, still short of " + Scorer.format(opening) + " to get on the board.");
        }
        if (score + hand >= winning) {
            return new Choice(true, "banks. This hand reaches " + Scorer.format(winning) + ".");
        }
        if (!onBoard) {
            if (diceLeft >= 4 && hand < opening * 2) {
                return new Choice(false, "rolls. " + diceWord(diceLeft) + " can grow this opening hand.");
            }
            return new Choice(true, "banks to get on the board.");
        }
        int keep = switch (Math.max(diceLeft, 1)) {
            case 1 -> 0;
            case 2 -> 300;
            case 3 -> 600;
            default -> 1_000;
        };
        if (hand >= keep) {
            return new Choice(true, "banks. " + diceWord(diceLeft) + " left is a poor place to risk "
                    + Scorer.format(hand) + ".");
        }
        return new Choice(false, "rolls. " + Scorer.format(hand) + " is worth chasing with "
                + diceWord(diceLeft) + " left.");
    }

    static Choice cont(int diceLeft) {
        if (diceLeft >= 4) {
            return new Choice(true, "takes the " + diceWord(diceLeft) + " left over.");
        }
        return new Choice(false, "starts fresh. " + diceWord(diceLeft)
                + " left over is a thin start from zero.");
    }

    /** First free "Rook", then "Rook 2", and so on. */
    static String nameFor(List<Player> seated) {
        if (!taken(seated, NAME)) {
            return NAME;
        }
        int number = 2;
        while (taken(seated, NAME + " " + number)) {
            number++;
        }
        return NAME + " " + number;
    }

    private static boolean taken(List<Player> seated, String name) {
        for (Player player : seated) {
            if (player.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String diceWord(int count) {
        return count == 1 ? "1 die" : count + " dice";
    }
}
