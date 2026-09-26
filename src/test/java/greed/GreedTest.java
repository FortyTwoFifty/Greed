package greed;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Scoring checks and scripted games. Run with: java -cp out greed.GreedTest */
public final class GreedTest {
    private static int failed;

    public static void main(String[] args) {
        scoring();
        banksOpeningHandAndWins();
        rejectsBankBelowOpeningThenWins();
        hotDiceThenBustLosesTheHand();
        continuationBanksOnlyTheNewHand();
        newHandRollsFiveDice();
        rejectsDieThatDoesNotScore();
        botPolicy();
        soloHumanGetsRook();
        botPressesUntilItCanOpen();
        botTakesFourDiceAndRefusesAShortLeftover();
        botHotDiceBustsTheWholeHand();
        if (failed > 0) {
            System.out.println(failed + " failed");
            System.exit(1);
        }
        System.out.println("All greed tests passed.");
    }

    private static void scoring() {
        check(Scorer.score(new int[] {1}).points() == 100, "single 1");
        check(Scorer.score(new int[] {5}).points() == 50, "single 5");
        check(Scorer.score(new int[] {1, 1}).points() == 200, "two 1s");
        check(Scorer.score(new int[] {1, 5}).points() == 150, "1 and 5");
        check(Scorer.score(new int[] {1, 1, 5}).points() == 250, "1,1,5");
        check(!Scorer.score(new int[] {1, 1, 5, 2, 3}).valid(), "non-scoring dice cannot be held");
        check(Scorer.score(new int[] {1, 1, 1}).points() == 1000, "three 1s");
        check(Scorer.score(new int[] {5, 5, 5}).points() == 500, "three 5s");
        check(Scorer.score(new int[] {3, 3, 3}).points() == 300, "three 3s");
        check(Scorer.score(new int[] {2, 2, 2}).points() == 200, "three 2s");
        check(Scorer.score(new int[] {6, 6, 6}).points() == 600, "three 6s");
        check(Scorer.score(new int[] {1, 1, 1, 1}).points() == 2000, "four 1s");
        check(Scorer.score(new int[] {5, 5, 5, 5}).points() == 1000, "four 5s");
        check(Scorer.score(new int[] {3, 3, 3, 3}).points() == 600, "four 3s");
        check(Scorer.score(new int[] {1, 1, 1, 1, 1}).points() == 4000, "five 1s");
        check(Scorer.score(new int[] {5, 5, 5, 5, 5}).points() == 2000, "five 5s");
        check(Scorer.score(new int[] {3, 3, 3, 3, 3}).points() == 1200, "five 3s");
        check(Scorer.score(new int[] {2, 2, 2, 2, 2}).points() == 800, "five 2s");
        check(Scorer.score(new int[] {6, 6, 6, 6, 6}).points() == 2400, "five 6s");
        check(Scorer.score(new int[] {1, 1, 1, 5, 5}).points() == 1100, "three 1s and two 5s");
        check(Scorer.score(new int[] {5, 1, 5, 1, 5}).points() == 700, "three 5s and two 1s");
        check(Scorer.score(new int[] {3, 3, 3, 3, 1}).points() == 700, "four 3s and a 1");
        check(Scorer.score(new int[] {2, 2, 2, 1, 5}).points() == 350, "three 2s, 1, 5");
        check(Scorer.score(new int[] {1, 1, 1, 1, 5}).points() == 2050, "four 1s and a 5");
        check(!Scorer.score(new int[] {2}).valid(), "single 2");
        check(!Scorer.score(new int[] {3, 3}).valid(), "pair");
        check(!Scorer.score(new int[] {4, 4, 4, 2}).valid(), "three 4s plus a 2");
        check(!Scorer.score(new int[] {}).valid(), "empty");
        check(!Scorer.canScore(new int[] {2, 3, 4, 6, 6}), "bust roll");
        check(Scorer.canScore(new int[] {2, 2, 2, 3, 4}), "three 2s can score");
        check(Scorer.canScore(new int[] {6, 6, 6, 6, 2}), "four 6s can score");
        check(Scorer.canScore(new int[] {2, 3, 4, 6, 1}), "a single 1 can score");
        check(Scorer.setValue(1, 3) == 1000, "three 1s base");
        check(Scorer.setValue(1, 4) == 2000, "four 1s double");
        check(Scorer.setValue(1, 5) == 4000, "five 1s double again");
        check(Scorer.setValue(5, 3) == 500, "three 5s use face value");
        check(Scorer.setValue(5, 5) == 2000, "five 5s");
    }

    private static void banksOpeningHandAndWins() {
        Run run = play(2_000, 750, lines("2", "Alice", "Bob", "1 2 3 4", "b"),
                new int[] {1, 1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, "Held four 1s for 2,000.");
        expect(run.output, "Alice banks 2,000 points.");
        expect(run.output, "Alice gets on the board with 2,000.");
        expect(run.output, "Alice wins with 2,000 points.");
    }

    private static void rejectsBankBelowOpeningThenWins() {
        Run run = play(1_050, 750, lines("2", "Alice", "Bob", "all", "b", "", "all", "b"),
                new int[] {5, 2, 3, 4, 6},
                new int[] {1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, "You cannot bank on this roll.");
        expect(run.output, "You need 750 in this hand");
        expect(run.output, "Alice banks 1,050 points.");
        expect(run.output, "Alice wins with 1,050 points.");
    }

    private static void hotDiceThenBustLosesTheHand() {
        Run run = play(1_000, 750, lines("2", "Alice", "Bob", "all", "", "all", "b"),
                new int[] {1, 1, 1, 5, 5},
                new int[] {2, 3, 4, 6, 6},
                new int[] {1, 1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, "Every die scored. You have to roll all 5 dice again before you can bank.");
        expect(run.output, "Bust. Alice loses 1,100 unbanked points.");
        expect(run.output, "Bob wins with 2,000 points.");
        int aliceBoard = run.output.indexOf("Alice");
        check(!run.output.contains("Alice banks"), "hot-dice bust must not bank");
        check(!run.output.contains("Alice gets on the board"), "bust does not open the board");
        check(aliceBoard >= 0, "alice is named");
    }

    private static void continuationBanksOnlyTheNewHand() {
        Run run = play(5_000, 100, lines("2", "Alice", "Bob", "all", "b", "c", "all", "b", "quit"),
                new int[] {1, 1, 1, 2, 3},
                new int[] {1, 4});
        run.dice.assertDrained();
        expect(run.output, "Alice banks 1,000 points.");
        expect(run.output, "Alice left 2 dice unscored.");
        expect(run.output, "A continued hand starts at 0.");
        expect(run.output, "Bob banks 100 points.");
        expect(run.output, "Bob gets on the board with 100.");
        check(!run.output.contains("Bob banks 1,100"), "continuation does not inherit the bank");
        check(!run.output.contains("Bob gets on the board with 1,100"), "bob's total is only his hand");
    }

    private static void newHandRollsFiveDice() {
        Run run = play(5_000, 100, lines("2", "Alice", "Bob", "all", "b", "n", "all", "b", "quit"),
                new int[] {1, 1, 1, 2, 3},
                new int[] {1, 1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, "Bob starts a new hand.");
        expect(run.output, "Bob banks 2,000 points.");
    }

    private static void rejectsDieThatDoesNotScore() {
        Run run = play(5_000, 100, lines("2", "Ann", "Ben", "2", "1", "b", "quit"),
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "2 does not score on its own.");
        expect(run.output, "Ann banks 100 points.");
    }

    private static void botPolicy() {
        int[] roll = {1, 5, 2, 2, 3};
        int[] held = Bot.hold(roll);
        check(held.length == 2, "holds only the 1 and the 5");
        check(scoreOf(roll, held) == 150, "held 1 and 5 score 150");
        check(scoreOf(new int[] {6, 6, 6, 2, 3}, Bot.hold(new int[] {6, 6, 6, 2, 3})) == 600,
                "holds three 6s");

        check(Bot.bank(500, 4, true, 9_600, 750, 10_000).yes(), "bank to win");
        check(!Bot.bank(400, 4, false, 0, 750, 10_000).yes(), "cannot bank under 750");
        check(Bot.bank(800, 2, false, 0, 750, 10_000).yes(), "bank an opening hand on 2 dice");
        check(!Bot.bank(800, 4, false, 0, 750, 10_000).yes(), "press an opening hand with 4 dice");
        check(Bot.bank(50, 1, true, 1_000, 750, 10_000).yes(), "bank a single leftover die");
        check(!Bot.bank(200, 3, true, 1_000, 750, 10_000).yes(), "roll three dice on a small hand");
        check(Bot.bank(600, 3, true, 1_000, 750, 10_000).yes(), "bank 600 with 3 dice left");
        check(Bot.cont(4).yes(), "continue with 4 dice");
        check(!Bot.cont(3).yes(), "decline 3 dice");
        check(!Bot.cont(1).yes(), "decline 1 die");

        List<Player> seated = new ArrayList<>();
        seated.add(new Player("Rook", false));
        check(Bot.nameFor(seated).equals("Rook 2"), "second computer is Rook 2");
    }

    private static void soloHumanGetsRook() {
        Run run = play(2_000, 750, lines("1", "Ada"),
                new int[] {2, 3, 4, 6, 6},
                new int[] {1, 1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, "Rook sits down for the computer.");
        expect(run.output, "Bust. Ada scores nothing this turn.");
        expect(run.output, "Rook holds every scoring die.");
        expect(run.output, "Rook banks 2,000 points.");
        expect(run.output, "Rook wins with 2,000 points.");
    }

    private static void botPressesUntilItCanOpen() {
        Run run = play(5_000, 750, lines("2", "bot", "Bea", "quit"),
                new int[] {5, 2, 3, 4, 6},
                new int[] {1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, "Rook sits down for the computer.");
        expect(run.output, "Rook needs 750 in this hand to get on the board.");
        expect(run.output, "Rook rolls the remaining 4 dice.");
        expect(run.output, "Rook banks 1,050 points.");
        expect(run.output, "Rook gets on the board with 1,050.");
        check(!run.output.contains("Rook wins"), "1,050 does not win a 5,000 game");
    }

    private static void botTakesFourDiceAndRefusesAShortLeftover() {
        Run taken = play(5_000, 100, lines("2", "Alice", "bot", "1", "b", "quit"),
                new int[] {1, 2, 3, 4, 6},
                new int[] {1, 1, 1, 2});
        taken.dice.assertDrained();
        expect(taken.output, "Rook takes the 4 dice left over.");
        expect(taken.output, "Rook rolls the 4 dice left unscored.");
        expect(taken.output, "Rook banks 1,000 points.");

        Run refused = play(5_000, 100, lines("2", "Alice", "bot", "all", "b", "quit"),
                new int[] {1, 1, 1, 1, 2},
                new int[] {6, 6, 6, 4, 3});
        refused.dice.assertDrained();
        expect(refused.output, "Rook starts fresh.");
        expect(refused.output, "Rook starts a new hand.");
        expect(refused.output, "Rook banks 600 points.");
    }

    private static void botHotDiceBustsTheWholeHand() {
        Run run = play(5_000, 750, lines("2", "bot", "Bea", "quit"),
                new int[] {1, 1, 1, 5, 5},
                new int[] {2, 3, 4, 6, 6},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "Rook rolls all 5.");
        expect(run.output, "Bust. Rook loses 1,100 unbanked points.");
        check(!run.output.contains("Rook banks"), "a hot-dice bust banks nothing");
    }

    private static void doubleOnTwoDiceGivesAReroll() {
        // Non-scoring doubles on faces 2, 3, 4, 6 should all reroll (no bust).
        assertTrue(outputForDoubleReroll(5_000, 100, "You", new int[]{2, 2}), "\" You \" got a double");
        assertTrue(outputForDoubleReroll(5_000, 100, "You", new int[]{3, 3}), "\" You \" got a double");
        assertTrue(outputForDoubleReroll(5_000, 100, "You", new int[]{4, 4}), "\" You \" got a double");
        assertTrue(outputForDoubleReroll(5_000, 100, "You", new int[]{6, 6}), "\" You \" got a double");

        // Non-double non-scoring two dice still bust (no reroll message).
        assertFalse(outputForDoubleReroll(5_000, 100, "You", new int[]{2, 3}), "\" You \" got a double");

        // Scoring doubles are held normally (double-1 = 200, double-5 = 100), no reroll.
        assertFalse(outputForDoubleReroll(5_000, 100, "You", new int[]{1, 1}), "\" You \" got a double");
        assertFalse(outputForDoubleReroll(5_000, 100, "You", new int[]{5, 5}), "\" You \" got a double");

        // After a reroll that scores, the player can bank.
        Run banking = play(5_000, 100, lines("2", "You", "all", "b"),
                new int[]{6, 6},   // non-scoring double → reroll message printed
                new int[]{3, 5});  // scores (5=50), held as one hand
        banking.dice.assertDrained();
        check(banking.output.contains("\" You \" got a double"), "banking test shows double message");
    }

    private static String outputForDoubleReroll(int winning, int opening, String name, int[] rerollFace) {
        // When the double triggers a reroll, Game calls dice.roll(2) again for the second round.
        // Give two rolls: first produces the double, second produces whatever (even non-scoring).
        ScriptedDice dice = new ScriptedDice(rerollFace, new int[]{3, 4});
        StringWriter out = new StringWriter();
        String input = name + "\nall\nb\nquit\n";
        Game game = new Game(new java.io.StringReader(input), out, dice, winning, opening);
        try {
            game.play();
        } catch (Quit | java.io.UncheckedIOException e) {}
        return out.toString();
    }

    private static void assertFalse(String output, String shouldNotContain) {
        if (output.contains(shouldNotContain)) {
            failed++;
            System.out.println("FAIL: output unexpectedly contains '" + shouldNotContain + "'");
            System.out.println("---\n" + output);
        }
    }

    private static void assertTrue(String output, String shouldContain) {
        if (!output.contains(shouldContain)) {
            failed++;
            System.out.println("FAIL: output missing '" + shouldContain + "'");
            System.out.println("---\n" + output);
        }
    }

    private static int scoreOf(int[] roll, int[] indexes) {
        int[] faces = new int[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            faces[i] = roll[indexes[i]];
        }
        return Scorer.score(faces).points();
    }

    private static Run play(int winning, int opening, String input, int[]... rolls) {
        ScriptedDice dice = new ScriptedDice(rolls);
        StringWriter output = new StringWriter();
        new Game(new StringReader(input), output, dice, winning, opening).play();
        return new Run(output.toString(), dice);
    }

    private static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static void expect(String output, String needle) {
        if (!output.contains(needle)) {
            failed++;
            System.out.println("MISSING: " + needle);
            System.out.println("--- output ---");
            System.out.println(output);
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            failed++;
            System.out.println("FAIL: " + label);
        }
    }

    private record Run(String output, ScriptedDice dice) {}

    private static final class ScriptedDice implements DieSource {
        private final ArrayDeque<int[]> rolls = new ArrayDeque<>();

        ScriptedDice(int[]... script) {
            for (int[] roll : script) {
                rolls.addLast(roll.clone());
            }
        }

        @Override
        public int[] roll(int count) {
            if (rolls.isEmpty()) {
                throw new IllegalStateException("no scripted roll for " + count + " dice");
            }
            int[] next = rolls.removeFirst();
            if (next.length != count) {
                throw new IllegalStateException("wanted " + count + " dice but script has "
                        + next.length + ": " + Arrays.toString(next));
            }
            return next.clone();
        }

        void assertDrained() {
            if (!rolls.isEmpty()) {
                throw new IllegalStateException(rolls.size() + " scripted rolls were not used");
            }
        }
    }
}
