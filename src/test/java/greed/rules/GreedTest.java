package greed.rules;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import greed.view.LineView;

/** Scoring checks and scripted games. Run with: java -cp out greed.rules.GreedTest */
public final class GreedTest {
    private static int failed;
    private static final String ROOK = "Rook";

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
        bankCutoffsScaleWithTheWinningScore();
        personalityStaysHiddenUnlessDev();
        countedBotsSitAfterTheHumans();
        tokenAddsAComputerBesideTheCount();
        soloRejectsTheBotToken();
        badBotCountAsksAgain();
        botsUseTheConfiguredOpening();
        cautiousBotKeepsItsPersonality();
        customOpeningAndWinning();
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
        Run run = play(2_000, 750, lines("2", "0", "Alice", "Bob", "1 2 3 4", "b"),
                new int[] {1, 1, 1, 1, 2},
                new int[] {2, 3, 4, 4, 5});
        run.dice.assertDrained();
        expect(run.output, "Held four 1s for 2,000.");
        expect(run.output, "Alice banks 2,000 points.");
        expect(run.output, "Alice gets on the board with 2,000.");
        expect(run.output, "Alice wins with 2,000 points.");
    }

    private static void rejectsBankBelowOpeningThenWins() {
        Run run = play(1_050, 750, lines("2", "0", "Alice", "Bob", "all", "b", "", "all", "b"),
                new int[] {5, 2, 3, 4, 6},
                new int[] {1, 1, 1, 2},
                new int[] {2, 3, 4, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "You cannot bank on this roll.");
        expect(run.output, "You need 750 in this hand");
        expect(run.output, "Alice banks 1,050 points.");
        expect(run.output, "Alice wins with 1,050 points.");
    }

    private static void hotDiceThenBustLosesTheHand() {
        Run run = play(1_000, 750, lines("2", "0", "Alice", "Bob", "all", "", "all", "b"),
                new int[] {1, 1, 1, 5, 5},
                new int[] {2, 3, 4, 6, 6},
                new int[] {1, 1, 1, 1, 2},
                new int[] {2, 3, 4, 6, 6});
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
        Run run = play(5_000, 100, lines("2", "0", "Alice", "Bob", "all", "b", "c", "all", "b", "quit"),
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
        Run run = play(5_000, 100, lines("2", "0", "Alice", "Bob", "all", "b", "n", "all", "b", "quit"),
                new int[] {1, 1, 1, 2, 3},
                new int[] {1, 1, 1, 1, 2});
        run.dice.assertDrained();
        expect(run.output, "Bob starts a new hand.");
        expect(run.output, "Bob banks 2,000 points.");
    }

    private static void rejectsDieThatDoesNotScore() {
        Run run = play(5_000, 100, lines("2", "0", "Ann", "Ben", "2", "1", "b", "quit"),
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
                new int[] {1, 1, 1, 1, 2},
                new int[] {2, 3, 4, 6, 6});
        run.dice.assertDrained();
        expect(run.output, ROOK + " sits down for the computer.");
        expect(run.output, "Bust. Ada scores nothing this turn.");
        expect(run.output, ROOK + " holds every scoring die.");
        expect(run.output, ROOK + " banks 2,000 points.");
        expect(run.output, ROOK + " wins with 2,000 points.");
    }

    private static void botPressesUntilItCanOpen() {
        Run run = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "0", "bot", "Bea", "quit"),
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
        Run taken = playAs(Bot.Personality.STEADY, 5_000, 100, lines("2", "0", "Alice", "bot", "1", "b", "quit"),
                new int[] {1, 2, 3, 4, 6},
                new int[] {1, 1, 1, 2});
        taken.dice.assertDrained();
        expect(taken.output, ROOK + " takes the 4 dice left over");
        expect(taken.output, "starting at 100");
        expect(taken.output, ROOK + " rolls the 4 dice left unscored.");
        expect(taken.output, ROOK + " banks 1,100 points.");

        Run busted = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "0", "Alice", "bot", "all", "b", "quit"),
                new int[] {1, 1, 1, 1, 2},
                new int[] {2},
                new int[] {1, 2, 3, 4, 6});
        busted.dice.assertDrained();
        expect(busted.output, "starting at 2,000");
        expect(busted.output, "Bust. " + ROOK + " loses 2,000");
        check(!busted.output.contains("starts a new hand"), "a pot at the opening score is not declined");
    }

    private static void botHotDiceBustsTheWholeHand() {
        Run run = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "0", "bot", "Bea", "quit"),
                new int[] {1, 1, 1, 5, 5},
                new int[] {2, 3, 4, 6, 6},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, ROOK + " rolls all 5.");
        expect(run.output, "Bust. " + ROOK + " loses 1,250 unbanked points.");
        check(!run.output.contains(ROOK + " banks"), "a hot-dice bust banks nothing");
    }

    private static void straightIsHotDice() {
        Run run = play(10_000, 100, lines("2", "0", "Ada", "Bob", "all", "", "all", "b", "quit"),
                new int[] {1, 2, 3, 4, 5},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "Held a straight for 1,500.");
        expect(run.output, "Every die scored. You have to roll all 5 dice again before you can bank.");
        expect(run.output, "Ada banks 1,600");
    }

    private static void fullHouseIsHotDice() {
        Run run = play(10_000, 100, lines("2", "0", "Ada", "Bob", "all", "", "all", "b", "quit"),
                new int[] {3, 3, 3, 2, 2},
                new int[] {5, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "Held a full house for 1,250.");
        expect(run.output, "Ada banks 1,300");
    }

    private static void fourRunIsNotHeld() {
        Run run = play(10_000, 100, lines("2", "0", "Ada", "Bob", "1 2 3 4 5", "1", "b", "quit"),
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "2, 3, 4 and 6 do not score on their own.");
        expect(run.output, "Ada banks 100");
    }

    private static void straightDoesNotAssembleAcrossRolls() {
        Run run = play(10_000, 100, lines("2", "0", "Ada", "Bob", "all", "r", "all", "b", "quit"),
                new int[] {1, 2, 3, 4, 6},
                new int[] {5, 2, 3, 6});
        run.dice.assertDrained();
        expect(run.output, "Ada banks 150 points.");
        check(!run.output.contains("for 1,500"), "a straight does not assemble across rolls");
    }

    private static void fullHouseDoesNotAssembleAcrossRolls() {
        Run run = play(10_000, 100, lines("2", "0", "Ada", "Bob", "all", "r", "all", "b", "quit"),
                new int[] {3, 3, 3, 2, 6},
                new int[] {5, 2});
        run.dice.assertDrained();
        expect(run.output, "three 3s for 300");
        expect(run.output, "Ada banks 350");
        check(!run.output.contains("full house"), "a full house does not assemble across rolls");
    }

    private static void botHoldsFullHouse() {
        Run run = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "0", "bot", "Bea", "quit"),
                new int[] {3, 3, 3, 2, 2},
                new int[] {2, 3, 4, 6, 6},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, ROOK + " rolls all 5.");
        expect(run.output, "Bust. " + ROOK + " loses 1,250");
    }

    private static void carriedPotPassesTwiceThenHotDice() {
        Run run = play(20_000, 750, lines("3", "0", "Alice", "Bob", "Cara",
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
        Run run = play(20_000, 750, lines("2", "0", "Alice", "Bob", "all", "b", "c", "quit"),
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
        Run run = playAs(Bot.Personality.STEADY, 5_000, 750, lines("2", "0", "bot", "Bea", "quit"),
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
        int[][] rolls = {new int[] {1, 1, 1, 1, 2}, new int[] {2, 3, 4, 6, 6}};
        Run hidden = play(500, 100, lines("2", "0", "bot", "bot"), new Random(99), rolls);
        hidden.dice.assertDrained();
        expect(hidden.output, "Rook sits down for the computer.");
        expect(hidden.output, "Rook 2 sits down for the computer.");
        check(!hidden.output.contains("Rook (") && !hidden.output.contains("Rook 2 ("),
                "a normal game hides both personalities");
        expect(hidden.output, "wins with");
        check(!hidden.output.contains("Hold dice by number"),
                "an all-bot game never asks a human to hold");

        StringWriter shown = new StringWriter();
        LineView view = new LineView(new StringReader(lines("2", "0", "bot", "bot")), shown, 500, 100, true);
        ScriptedDice dice = new ScriptedDice(rolls);
        new Game(view, dice, 500, 100, new Random(99)).play();
        dice.assertDrained();
        expect(shown.toString(), "Rook (" + first.adjective() + ") sits down for the computer.");
        expect(shown.toString(), "Rook 2 (" + second.adjective() + ") sits down for the computer.");
        Random again = new Random(99);
        check(Bot.Personality.pick(again) == first, "same seed draws the same first personality");
        check(Bot.Personality.pick(again) == second, "same seed draws the same second personality");
    }

    /** At 10,000 the cutoffs are the table. At 2,000 they scale and round to the nearest 50. */
    private static void bankCutoffsScaleWithTheWinningScore() {
        Bot.Personality[] styles = {
            Bot.Personality.CAUTIOUS, Bot.Personality.STEADY, Bot.Personality.BOLD
        };
        int[][] at10000 = {
            {0, 100, 200, 400},
            {0, 300, 600, 1_000},
            {200, 800, 1_500, 2_500}
        };
        int[][] at2000 = {
            {0, 50, 50, 100},
            {0, 50, 100, 200},
            {50, 150, 300, 500}
        };
        for (int s = 0; s < styles.length; s++) {
            for (int dice = 1; dice <= 4; dice++) {
                int full = styles[s].bankAt(dice, 10_000);
                int shortGame = styles[s].bankAt(dice, 2_000);
                check(full == at10000[s][dice - 1],
                        styles[s] + " with " + dice + " at 10,000 is " + at10000[s][dice - 1]
                                + " (was " + full + ")");
                check(shortGame == at2000[s][dice - 1],
                        styles[s] + " with " + dice + " at 2,000 is " + at2000[s][dice - 1]
                                + " (was " + shortGame + ")");
            }
            check(styles[s].bankAt(5, 10_000) == at10000[s][3], styles[s] + " uses bank-at-4 for 5 dice");
            check(styles[s].bankAt(5, 2_000) == at2000[s][3], styles[s] + " scales 5 dice like 4");
        }
        Bot.Personality steady = Bot.Personality.STEADY;
        check(Bot.bank(500, 4, true, 1_000, 750, 10_000, steady).reason().startsWith("rolls."),
                "steady rolls a 500 hand with 4 dice at 10,000");
        check(Bot.bank(500, 4, true, 1_000, 750, 2_000, steady).reason().startsWith("banks."),
                "steady banks that same 500 hand at 2,000");
    }

    private static void personalityStaysHiddenUnlessDev() {
        Bot.forced = Bot.Personality.STEADY;
        try {
            Seating hidden = seat(false, "2", "1", "Ada");
            expect(hidden.output, "Rook sits down for the computer.");
            check(!hidden.output.contains("Cautious") && !hidden.output.contains("Steady")
                            && !hidden.output.contains("Bold") && !hidden.output.contains("(Steady)"),
                    "dev off hides the adjective");
            Player rook = computerNamed(hidden.players, "Rook");
            check(rook != null && rook.personality == Bot.Personality.STEADY && !rook.revealPersonality,
                    "hidden Rook is still Steady");

            Seating shown = seat(true, "2", "1", "Ada");
            expect(shown.output, "Rook (Steady) sits down for the computer.");
            Player revealed = computerNamed(shown.players, "Rook");
            check(revealed != null && revealed.revealPersonality, "dev reveals Rook");
        } finally {
            Bot.forced = null;
        }
    }

    private static void countedBotsSitAfterTheHumans() {
        for (Bot.Personality style : List.of(Bot.Personality.STEADY, Bot.Personality.CAUTIOUS)) {
            Bot.forced = style;
            try {
                Seating seating = seat(false, "4", "2", "Ann", "Bea");
                expect(seating.output, "Rook sits down for the computer.");
                expect(seating.output, "Rook 2 sits down for the computer.");
                expect(seating.output, "Ann goes first.");
                check(seating.players.size() == 4, "four seats");
                check(!seating.players.get(0).computer && seating.players.get(0).name.equals("Ann"),
                        "Ann sits first");
                check(!seating.players.get(1).computer && seating.players.get(1).name.equals("Bea"),
                        "Bea is the other human");
                check(computerNamed(seating.players, "Rook") != null
                                && computerNamed(seating.players, "Rook").personality == style,
                        "Rook takes the forced personality");
                check(computerNamed(seating.players, "Rook 2") != null
                                && computerNamed(seating.players, "Rook 2").personality == style,
                        "Rook 2 takes the forced personality");
            } finally {
                Bot.forced = null;
            }
        }
    }

    /** 3 seats, 1 counted computer, then Ann and the bot token: two computers and one human. */
    private static void tokenAddsAComputerBesideTheCount() {
        Bot.forced = null;
        Seating seating = seat(false, "3", "1", "Ann", "bot");
        check(seating.players.size() == 3, "three seats filled");
        check(!seating.players.get(0).computer && seating.players.get(0).name.equals("Ann"), "Ann is the human");
        Player rook = seating.players.get(1);
        Player second = seating.players.get(2);
        check(rook.computer && rook.name.equals("Rook") && rook.personality != null, "token seats Rook");
        check(second.computer && second.name.equals("Rook 2") && second.personality != null,
                "the counted computer is Rook 2");
        expect(seating.output, "Rook sits down for the computer.");
        expect(seating.output, "Rook 2 sits down for the computer.");
        expect(seating.output, "Ann goes first.");
        check(!seating.output.contains("Cautious") && !seating.output.contains("Steady")
                        && !seating.output.contains("Bold"),
                "the extra computer stays unlabeled");

        Seating computer = seat(false, "2", "0", "Computer", "Bea");
        check(computer.players.get(0).computer && computer.players.get(0).name.equals("Rook")
                        && computer.players.get(0).personality != null,
                "Computer, any case, seats Rook");
    }

    private static void soloRejectsTheBotToken() {
        Run run = play(10_000, 750, lines("1", "bot", "Ada", "quit"), new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "Type your name. Rook takes the other seat.");
        expect(run.output, "One human sits with the computer. Rook takes the other seat.");
        expect(run.output, "Ada goes first.");
        check(!run.output.contains("How many computer players?"), "solo does not ask for a bot count");
    }

    private static void badBotCountAsksAgain() {
        Seating seating = seat(false, "4", "no", "9", "2", "Ann", "Bea");
        expect(seating.output, "Enter a number from 0 to 3.");
        check(seating.players.size() == 4, "a bad count does not change the seat total");
        Run solo = play(10_000, 750, lines("1", "Ada", "quit"), new int[] {1, 2, 3, 4, 6});
        solo.dice.assertDrained();
        check(!solo.output.contains("How many computer players?"), "one seat skips the bot question");
    }

    /** Steady takes 1 die at 300 when the opening score is 200. At 750 that pot is declined. */
    private static void botsUseTheConfiguredOpening() {
        Run run = playAs(Bot.Personality.STEADY, 5_000, 200, lines("2", "1", "Ann", "all", "b", "quit"),
                new int[] {1, 1, 5, 5, 2},
                new int[] {2},
                new int[] {1, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "takes the 1 die left over, starting at 300.");
    }

    /**
     * Five 2s are 800 and hot, so the next 5 adds 50 and leaves 4 dice.
     * Cautious banks that opening hand. Steady would press it.
     */
    private static void cautiousBotKeepsItsPersonality() {
        check(Bot.bank(800, 4, false, 0, 750, 10_000, Bot.Personality.CAUTIOUS).reason()
                        .startsWith("banks to get on the board."),
                "cautious banks 800 with 4 dice");
        check(Bot.bank(800, 4, false, 0, 750, 10_000, Bot.Personality.STEADY).reason()
                        .contains("can grow this opening hand."),
                "steady would press 800 with 4 dice");
        Run run = playAs(Bot.Personality.CAUTIOUS, 5_000, 750, lines("2", "1", "Bea", "quit"),
                new int[] {2, 3, 4, 6, 6},
                new int[] {2, 2, 2, 2, 2},
                new int[] {5, 2, 3, 4, 6});
        run.dice.assertDrained();
        expect(run.output, "banks to get on the board.");
        check(!run.output.contains("can grow this opening hand."),
                "the seated Cautious bot does not press like Steady");
    }

    private static void customOpeningAndWinning() {
        Run below = play(5_000, 300, lines("2", "0", "Alice", "Bob", "1", "b", "quit"),
                new int[] {5, 2, 3, 4, 6});
        below.dice.assertDrained();
        expect(below.output, "You need 300 in this hand");
        expect(below.output, "You cannot bank on this roll.");
        check(!below.output.contains("You need 750"), "the opening line uses 300");

        Run won = play(400, 100, lines("2", "0", "Alice", "Bob", "1 2 3 4", "b"),
                new int[] {5, 5, 5, 5, 2},
                new int[] {2, 3, 4, 4, 5});
        won.dice.assertDrained();
        expect(won.output, "Alice banks 1,000 points.");
        expect(won.output, "Alice gets on the board with 1,000.");
        expect(won.output, "Alice wins with 1,000 points.");
    }

    private static Seating seat(boolean dev, String... input) {
        StringWriter output = new StringWriter();
        LineView view = new LineView(new StringReader(lines(input)), output, 10_000, 750, dev);
        Game game = new Game(view, new ScriptedDice(), 10_000, 750);
        return new Seating(game.readPlayers(), output.toString());
    }

    private static Player computerNamed(List<Player> players, String name) {
        for (Player player : players) {
            if (player.computer && player.name.equals(name)) {
                return player;
            }
        }
        return null;
    }

    private record Seating(List<Player> players, String output) {}

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
