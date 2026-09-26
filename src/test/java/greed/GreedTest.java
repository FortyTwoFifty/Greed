package greed;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Scoring checks and scripted games. Run with: java -cp out greed.GreedTest */
public final class GreedTest {
    private static int failed;
    private static final String ROOK = "Rook (Steady)";

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
        computerRerollsANonScoringDouble();
        straightIsHotDice();
        fullHouseIsHotDice();
        fourRunIsNotHeld();
        straightDoesNotAssembleAcrossRolls();
        fullHouseDoesNotAssembleAcrossRolls();
        botHoldsFullHouse();
        carriedPotPassesTwiceThenHotDice();
        bustLosesTheCarriedPot();
        personalitiesComeFromTheSeed();
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
        check(Scorer.score(new int[] {1, 1, 1, 5, 5}).points() == 1250, "three 1s and two 5s");
        check(Scorer.score(new int[] {5, 1, 5, 1, 5}).points() == 1250, "three 5s and two 1s");
        check(!Scorer.score(new int[] {1, 1, 1, 5, 5}).detail().contains("1,000"),
                "full house replaces the part scores");
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
        check(Scorer.score(new int[] {1, 2, 3, 4, 5}).points() == 1500, "straight");
        check(Scorer.score(new int[] {5, 4, 3, 2, 1}).points() == 1500, "straight reversed");
        check(Scorer.score(new int[] {3, 3, 3, 2, 2}).points() == 1250, "full house of 3s and 2s");
        check(Scorer.canScore(new int[] {3, 3, 3, 2, 2}), "full house can score");
        check(Scorer.score(new int[] {6, 6, 2, 2, 2}).points() == 1250, "full house of 2s and 6s");
        check(Scorer.canScore(new int[] {6, 6, 2, 2, 2}), "full house of 2s can score");
        check(Scorer.scoringIndexes(new int[] {3, 3, 3, 2, 2}).length == 5, "full house holds every die");
        check(Scorer.scoringIndexes(new int[] {1, 2, 3, 4, 5}).length == 5, "straight holds every die");
        check(!Scorer.score(new int[] {1, 2, 3, 4}).valid(), "four-die run is not a straight");
        check(!Scorer.score(new int[] {1, 2, 3, 4, 6}).valid(), "1-2-3-4-6 is not a straight");
        check(!Scorer.score(new int[] {2, 3, 4, 5, 6}).valid(), "2-3-4-5-6 is not a straight");
        check(Scorer.scoringIndexes(new int[] {1, 2, 3, 4, 6}).length == 1, "only the 1 scores in 1-2-3-4-6");
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
        expect(run.output, "Bust. Alice loses 1,250 unbanked points.");
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
        expect(run.output, "A continued hand starts at 1,000.");
        expect(run.output, "Bob banks 1,100 points.");
        expect(run.output, "Bob gets on the board with 1,100.");
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
        int[] house = {3, 3, 3, 2, 2};
        check(Bot.hold(house).length == 5, "bot holds a full house");
        check(scoreOf(house, Bot.hold(house)) == 1250, "bot full house scores 1,250");
        int[] straight = {1, 2, 3, 4, 5};
        check(Bot.hold(straight).length == 5, "bot holds a straight");
        check(scoreOf(straight, Bot.hold(straight)) == 1500, "bot straight scores 1,500");

        Bot.Personality steady = Bot.Personality.STEADY;
        check(Bot.bank(500, 4, true, 9_600, 750, 10_000, steady).yes(), "bank to win");
        check(!Bot.bank(400, 4, false, 0, 750, 10_000, steady).yes(), "cannot bank under 750");
        check(Bot.bank(800, 2, false, 0, 750, 10_000, steady).yes(), "bank an opening hand on 2 dice");
        check(!Bot.bank(800, 4, false, 0, 750, 10_000, steady).yes(), "press an opening hand with 4 dice");
        check(Bot.bank(50, 1, true, 1_000, 750, 10_000, steady).yes(), "bank a single leftover die");
        check(!Bot.bank(200, 3, true, 1_000, 750, 10_000, steady).yes(), "roll three dice on a small hand");
        check(Bot.bank(600, 3, true, 1_000, 750, 10_000, steady).yes(), "bank 600 with 3 dice left");
        check(Bot.bank(500, 4, true, 1_000, 750, 10_000, Bot.Personality.CAUTIOUS).yes(), "cautious banks early");
        check(!Bot.bank(500, 4, true, 1_000, 750, 10_000, steady).yes(), "steady still rolls 500 with 4 dice");
        check(!Bot.bank(500, 4, true, 1_000, 750, 10_000, Bot.Personality.BOLD).yes(), "bold keeps rolling");
        check(Bot.bank(800, 4, false, 0, 750, 10_000, Bot.Personality.CAUTIOUS).yes(),
                "cautious banks an opening hand");
        passedDice();

        List<Player> seated = new ArrayList<>();
        seated.add(new Player("Rook", false));
        check(Bot.nameFor(seated).equals("Rook 2"), "second computer is Rook 2");
    }

    /** Take or decline a passed hand at 1, 3, and 4 dice for each personality. */
    private static void passedDice() {
        Bot.Personality cautious = Bot.Personality.CAUTIOUS;
        Bot.Personality steady = Bot.Personality.STEADY;
        Bot.Personality bold = Bot.Personality.BOLD;
        takes(cautious, 4, 100, 750, true, "cautious takes 4 dice");
        takes(cautious, 3, 2_000, 750, false, "cautious declines 3 dice");
        takes(cautious, 1, 2_000, 750, false, "cautious declines 1 die");
        takes(steady, 4, 50, 750, true, "steady takes 4 dice");
        takes(steady, 3, 0, 750, true, "steady takes 3 dice");
        takes(steady, 1, 50, 750, false, "steady declines 1 die under the opening");
        takes(steady, 1, 2_000, 100, true, "steady takes 1 die once the pot opens");
        takes(steady, 2, 800, 750, true, "steady takes 2 dice at the opening");
        takes(steady, 2, 100, 750, false, "steady declines 2 dice under the opening");
        takes(bold, 4, 50, 750, true, "bold takes 4 dice");
        takes(bold, 3, 50, 750, true, "bold takes 3 dice with a pot");
        takes(bold, 3, 0, 750, false, "bold declines 3 dice with an empty pot");
        takes(bold, 1, 50, 750, false, "bold declines 1 die under the opening");
        takes(bold, 1, 2_000, 750, true, "bold takes 1 die at the opening");
        takes(bold, 2, 100, 750, true, "bold takes 2 dice with any pot");
        check(Bot.cont(steady, 4, 100, 750).reason().startsWith("takes the 4 dice left over, starting at 100"),
                "accept reason names the dice and the pot");
        check(Bot.cont(cautious, 1, 2_000, 750).reason().startsWith("starts fresh. 1 die left over is a thin way to risk 2,000"),
                "decline reason names the pot at risk");
    }

    private static void takes(Bot.Personality style, int dice, int carried, int opening, boolean yes, String label) {
        check(Bot.cont(style, dice, carried, opening).yes() == yes, label);
    }

    private static void soloHumanGetsRook() {
        Run run = playAs(Bot.Personality.STEADY, 2_000, 750, lines("1", "Ada"),
                new int[] {2, 3, 4, 6, 6},
                new int[] {1, 1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, ROOK + " sits down for the computer.");
        expect(run.output, "Bust. Ada scores nothing this turn.");
        expect(run.output, ROOK + " holds every scoring die.");
        expect(run.output, ROOK + " banks 2,000 points.");
        expect(run.output, ROOK + " wins with 2,000 points.");
    }

    private static void botPressesUntilItCanOpen() {
        Run run = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "bot", "Bea", "quit"),
                new int[] {5, 2, 3, 4, 6},
                new int[] {1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, ROOK + " sits down for the computer.");
        expect(run.output, ROOK + " needs 750 in this hand to get on the board.");
        expect(run.output, ROOK + " rolls the remaining 4 dice.");
        expect(run.output, ROOK + " banks 1,050 points.");
        expect(run.output, ROOK + " gets on the board with 1,050.");
        check(!run.output.contains(ROOK + " wins"), "1,050 does not win a 5,000 game");
    }

    private static void botTakesFourDiceAndRefusesAShortLeftover() {
        Run taken = playAs(Bot.Personality.STEADY, 5_000, 100, lines("2", "Alice", "bot", "1", "b", "quit"),
                new int[] {1, 2, 3, 4, 6},
                new int[] {1, 1, 1, 2});
        taken.dice.assertDrained();
        expect(taken.output, ROOK + " takes the 4 dice left over");
        expect(taken.output, "starting at 100");
        expect(taken.output, ROOK + " rolls the 4 dice left unscored.");
        expect(taken.output, ROOK + " banks 1,100 points.");

        Run busted = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "Alice", "bot", "all", "b", "quit"),
                new int[] {1, 1, 1, 1, 2},
                new int[] {2},
                new int[] {1, 2, 3, 4, 6});
        busted.dice.assertDrained();
        expect(busted.output, "starting at 2,000");
        expect(busted.output, "Bust. " + ROOK + " loses 2,000");
        check(!busted.output.contains("starts a new hand"), "a pot at the opening score is not declined");
    }

    private static void botHotDiceBustsTheWholeHand() {
        Run run = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "bot", "Bea", "quit"),
                new int[] {1, 1, 1, 5, 5},
                new int[] {2, 3, 4, 6, 6},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, ROOK + " rolls all 5.");
        expect(run.output, "Bust. " + ROOK + " loses 1,250 unbanked points.");
        check(!run.output.contains(ROOK + " banks"), "a hot-dice bust banks nothing");
    }

    private static void straightIsHotDice() {
        Run run = play(10_000, 100, lines("2", "Ada", "Bob", "all", "", "all", "b", "quit"),
                new int[] {1, 2, 3, 4, 5},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "Held a straight for 1,500.");
        expect(run.output, "Every die scored. You have to roll all 5 dice again before you can bank.");
        expect(run.output, "Ada banks 1,600");
    }

    private static void fullHouseIsHotDice() {
        Run run = play(10_000, 100, lines("2", "Ada", "Bob", "all", "", "all", "b", "quit"),
                new int[] {3, 3, 3, 2, 2},
                new int[] {5, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "Held a full house for 1,250.");
        expect(run.output, "Ada banks 1,300");
    }

    private static void fourRunIsNotHeld() {
        Run run = play(10_000, 100, lines("2", "Ada", "Bob", "1 2 3 4 5", "1", "b", "quit"),
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "2, 3, 4 and 6 do not score on their own.");
        expect(run.output, "Ada banks 100");
    }

    private static void straightDoesNotAssembleAcrossRolls() {
        Run run = play(10_000, 100, lines("2", "Ada", "Bob", "all", "r", "all", "b", "quit"),
                new int[] {1, 2, 3, 4, 6},
                new int[] {5, 2, 3, 6});
        run.dice.assertDrained();
        expect(run.output, "Ada banks 150 points.");
        check(!run.output.contains("for 1,500"), "a straight does not assemble across rolls");
    }

    private static void fullHouseDoesNotAssembleAcrossRolls() {
        Run run = play(10_000, 100, lines("2", "Ada", "Bob", "all", "r", "all", "b", "quit"),
                new int[] {3, 3, 3, 2, 6},
                new int[] {5, 2});
        run.dice.assertDrained();
        expect(run.output, "three 3s for 300");
        expect(run.output, "Ada banks 350");
        check(!run.output.contains("full house"), "a full house does not assemble across rolls");
    }

    private static void botHoldsFullHouse() {
        Run run = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "bot", "Bea", "quit"),
                new int[] {3, 3, 3, 2, 2},
                new int[] {2, 3, 4, 6, 6},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, ROOK + " rolls all 5.");
        expect(run.output, "Bust. " + ROOK + " loses 1,250");
    }

    private static void carriedPotPassesTwiceThenHotDice() {
        Run run = play(20_000, 750, lines("3", "Alice", "Bob", "Cara",
                "all", "b", "c", "all", "b", "c", "all", "", "all", "b"),
                new int[] {1, 1, 1, 2, 3},
                new int[] {1, 4},
                new int[] {5},
                new int[] {1, 1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, "starts at 1,000");
        expect(run.output, "Bob banks 1,100");
        expect(run.output, "starts at 1,100");
        expect(run.output, "Hand total: 1,150");
        expect(run.output, "Every die scored. You have to roll all 5 dice again before you can bank.");
        expect(run.output, "Cara banks 3,150");
    }

    private static void bustLosesTheCarriedPot() {
        Run run = play(20_000, 750, lines("2", "Alice", "Bob", "all", "b", "c", "quit"),
                new int[] {1, 1, 1, 2, 3},
                new int[] {2, 3},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "Bust. Bob loses 1,000");
        check(!run.output.contains("Bob banks"), "a bust does not bank the carried pot");
        int bustAt = run.output.indexOf("Bust. Bob loses 1,000");
        String later = bustAt < 0 ? "" : run.output.substring(bustAt + "Bust. Bob loses 1,000".length());
        check(later.contains("Alice") && later.contains("1,000") && later.contains("on the board"),
                "alice keeps the 1,000 she banked");
    }

    /**
     * Rook holds three 2s, then a non-scoring double. The double is rolled again
     * and the next roll is what busts, so the script is empty afterwards.
     */
    private static void computerRerollsANonScoringDouble() {
        Run run = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "bot", "Bea", "quit"),
                new int[] {2, 2, 2, 3, 4},
                new int[] {6, 6},
                new int[] {2, 3},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "\"" + ROOK + "\" got a double");
        expect(run.output, "Bust. " + ROOK + " loses 200");
        check(!run.output.contains("scores nothing this turn"), "the double is not the bust");
    }

    /** Two computers draw from the game's random, in seat order, and a second seed matches. */
    private static void personalitiesComeFromTheSeed() {
        Bot.forced = null;
        Random probe = new Random(99);
        Bot.Personality first = Bot.Personality.pick(probe);
        Bot.Personality second = Bot.Personality.pick(probe);
        Run run = play(500, 100, lines("2", "bot", "bot"), new Random(99),
                new int[] {1, 1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, "Rook (" + first.adjective() + ") sits down for the computer.");
        expect(run.output, "Rook 2 (" + second.adjective() + ") sits down for the computer.");
        Random again = new Random(99);
        check(Bot.Personality.pick(again) == first, "same seed draws the same first personality");
        check(Bot.Personality.pick(again) == second, "same seed draws the same second personality");
    }

    private static int scoreOf(int[] roll, int[] indexes) {
        int[] faces = new int[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            faces[i] = roll[indexes[i]];
        }
        return Scorer.score(faces).points();
    }

    private static Run play(int winning, int opening, String input, int[]... rolls) {
        return play(winning, opening, input, new Random(0), rolls);
    }

    private static Run play(int winning, int opening, String input, Random random, int[]... rolls) {
        ScriptedDice dice = new ScriptedDice(rolls);
        StringWriter output = new StringWriter();
        new Game(new StringReader(input), output, dice, winning, opening, random).play();
        return new Run(output.toString(), dice);
    }

    private static Run playAs(Bot.Personality personality, int winning, int opening, String input, int[]... rolls) {
        Bot.forced = personality;
        try {
            return play(winning, opening, input, rolls);
        } finally {
            Bot.forced = null;
        }
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
