package greed.rules;

import java.util.List;
import java.util.Random;

import greed.view.LineView;

/**
 * A computer player. Holds every scoring die. Each computer is assigned one
 * personality when the game starts. Cautious banks early and takes a passed
 * hand only when four dice are left. Steady takes three or more, or any
 * leftover once the carried pot reaches the opening score, and banks on the
 * middle thresholds. Bold takes a pot above zero with two dice, or a pot at
 * the opening score with one die, and keeps rolling longer.
 */
public final class Bot {
    static final String NAME = "Rook";

    /**
     * How a computer takes a passed hand and when it banks.
     * Thresholds are dice counts and point totals. A dice threshold of
     * {@link Integer#MAX_VALUE} turns that clause off.
     */
    public enum Personality {
        CAUTIOUS(4, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 100, 200, 400, 6, 1),
        STEADY(3, 1, Integer.MAX_VALUE, 0, 300, 600, 1_000, 4, 2),
        BOLD(Integer.MAX_VALUE, 1, 2, 200, 800, 1_500, 2_500, 2, 4);

        /** Take the passed dice whenever at least this many remain. */
        final int takeAtDice;
        /** Also take when the carried pot is at least the opening score and this many dice remain. */
        final int richAtDice;
        /** Also take when the carried pot is above zero and this many dice remain. */
        final int anyPotAtDice;
        final int bankAt1;
        final int bankAt2;
        final int bankAt3;
        final int bankAt4;
        /** Keep rolling an opening hand when at least this many dice remain. */
        final int openingPressAt;
        /** And the hand is still under this multiple of the opening score. */
        final int openingMultiple;

        Personality(int takeAtDice, int richAtDice, int anyPotAtDice,
                    int bankAt1, int bankAt2, int bankAt3, int bankAt4,
                    int openingPressAt, int openingMultiple) {
            this.takeAtDice = takeAtDice;
            this.richAtDice = richAtDice;
            this.anyPotAtDice = anyPotAtDice;
            this.bankAt1 = bankAt1;
            this.bankAt2 = bankAt2;
            this.bankAt3 = bankAt3;
            this.bankAt4 = bankAt4;
            this.openingPressAt = openingPressAt;
            this.openingMultiple = openingMultiple;
        }

        boolean takes(int diceLeft, int carried, int opening) {
            if (diceLeft >= takeAtDice) {
                return true;
            }
            if (diceLeft >= richAtDice && carried >= opening) {
                return true;
            }
            return diceLeft >= anyPotAtDice && carried > 0;
        }

        int bankAt(int diceLeft) {
            return switch (Math.max(diceLeft, 1)) {
                case 1 -> bankAt1;
                case 2 -> bankAt2;
                case 3 -> bankAt3;
                default -> bankAt4;
            };
        }

        /** "Bold", "Steady", "Cautious" — the word shown beside the name. */
        String adjective() {
            return switch (this) {
                case CAUTIOUS -> "Cautious";
                case STEADY -> "Steady";
                case BOLD -> "Bold";
            };
        }

        static Personality pick(Random random) {
            Personality[] all = values();
            return all[random.nextInt(all.length)];
        }
    }

    public record Choice(boolean yes, String reason) {}

    /**
     * When set, every computer seated by {@link #assign()} gets this personality
     * instead of a draw. Tests clear it afterwards.
     */
    static Personality forced;

    /** The game's random, set before players sit. Seeded tests stay repeatable. */
    public static Random source = new Random(0);

    private Bot() {}

    public static Personality assign() {
        if (forced != null) {
            return forced;
        }
        return Personality.pick(source);
    }

    static int[] hold(int[] roll) {
        int[] indexes = Scorer.scoringIndexes(roll);
        if (indexes.length == 0) {
            throw new IllegalStateException("bot was asked to hold a roll that cannot score");
        }
        return indexes;
    }

    public static Choice bank(int hand, int diceLeft, boolean onBoard, int score, int opening, int winning,
                       Personality personality) {
        Personality style = personality == null ? Personality.STEADY : personality;
        boolean legal = onBoard || hand >= opening;
        if (!legal) {
            return new Choice(false, "rolls, still short of " + Scorer.format(opening) + " to get on the board.");
        }
        if (score + hand >= winning) {
            return new Choice(true, "banks. This hand reaches " + Scorer.format(winning) + ".");
        }
        if (!onBoard) {
            if (diceLeft >= style.openingPressAt && hand < opening * style.openingMultiple) {
                return new Choice(false, "rolls. " + LineView.diceWord(diceLeft) + " can grow this opening hand.");
            }
            return new Choice(true, "banks to get on the board.");
        }
        if (hand >= style.bankAt(diceLeft)) {
            return new Choice(true, "banks. " + LineView.diceWord(diceLeft) + " left is a poor place to risk "
                    + Scorer.format(hand) + ".");
        }
        return new Choice(false, "rolls. " + Scorer.format(hand) + " is worth chasing with "
                + LineView.diceWord(diceLeft) + " left.");
    }

    public static Choice cont(Personality personality, int diceLeft, int carried, int opening) {
        Personality style = personality == null ? Personality.STEADY : personality;
        if (style.takes(diceLeft, carried, opening)) {
            return new Choice(true, "takes the " + LineView.diceWord(diceLeft) + " left over, starting at "
                    + Scorer.format(carried) + ".");
        }
        return new Choice(false, "starts fresh. " + LineView.diceWord(diceLeft)
                + " left over is a thin way to risk " + Scorer.format(carried) + ".");
    }

    /** First free "Rook", then "Rook 2", and so on. */
    public static String nameFor(List<Player> seated) {
        if (!Player.taken(seated, NAME)) {
            return NAME;
        }
        int number = 2;
        while (Player.taken(seated, NAME + " " + number)) {
            number++;
        }
        return NAME + " " + number;
    }
}
