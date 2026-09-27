package greed.view;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import greed.rules.DieSource;
import greed.rules.Game;
import greed.rules.Player;
import greed.rules.Scorer;
import greed.rules.View;

/**
 * Hot dice must keep every earlier roll in the hand. A bank of only the re-roll fails.
 * ONE_AND_FIVE keeps a dead 6 so the roll is not the straight 1-2-3-4-5.
 * Run with: java -cp out greed.view.HotDiceTest
 */
public final class HotDiceTest {
    private static int failed;

    /** Five 1s. All five score, so the turn is hot. */
    private static final int[] FIVE_ONES = {1, 1, 1, 1, 1};
    /** Three 4s and two dice that do not score. */
    private static final int[] THREE_FOURS = {4, 4, 4, 2, 6};
    /** A 1 and a 5 among dead dice. The 6 keeps this off the straight. */
    private static final int[] ONE_AND_FIVE = {1, 5, 2, 3, 6};
    /** Three 1s, the whole roll. */
    private static final int[] THREE_ONES = {1, 1, 1};

    public static void main(String[] args) {
        hotThenScoreThenBank();
        hotThenPartialThenBank();
        hotHoldAllVersusToggle();
        botHotDice();
        hotBeforeOnTheBoard();
        hotAfterContinue();
        if (failed > 0) {
            System.out.println(failed + " failed");
            System.exit(1);
        }
        System.out.println("All hot dice tests passed.");
    }

    /** The bank is the hot hold plus the re-roll hold. */
    private static void hotThenScoreThenBank() {
        int[][] rolls = {FIVE_ONES, THREE_FOURS};
        int banked = bankOf(scoringFaces(FIVE_ONES), scoringFaces(THREE_FOURS));
        int hot = points(scoringFaces(FIVE_ONES));
        String[] line = {"2", "0", "Alice", "Bob", "all", "", "all", "b", "quit"};
        playLine("line hot then score then bank", 10_000, 100, rolls, line, "Alice", banked, hot);
        playTui("tui hot then score then bank", 10_000, 100, rolls, "Alice", banked, hot, keys -> {
            seat(keys, "Alice", "Bob");
            holdBest(keys);
            roll(keys);
            holdBest(keys);
            bank(keys);
            quit(keys);
        });
    }

    /** After the hot roll, hold only the 1 and leave the 5. */
    private static void hotThenPartialThenBank() {
        int[][] rolls = {FIVE_ONES, ONE_AND_FIVE};
        int banked = bankOf(scoringFaces(FIVE_ONES), new int[] {1});
        int hot = points(scoringFaces(FIVE_ONES));
        String[] line = {"2", "0", "Alice", "Bob", "all", "", "1", "b", "quit"};
        playLine("line hot then partial then bank", 10_000, 100, rolls, line, "Alice", banked, hot);
        playTui("tui hot then partial then bank", 10_000, 100, rolls, "Alice", banked, hot, keys -> {
            seat(keys, "Alice", "Bob");
            holdBest(keys);
            roll(keys);
            keys.add((int) '1');
            confirm(keys);
            bank(keys);
            quit(keys);
        });
    }

    /** Hold-all and five separate toggles both keep the first roll. */
    private static void hotHoldAllVersusToggle() {
        int[][] rolls = {FIVE_ONES, THREE_FOURS};
        int banked = bankOf(scoringFaces(FIVE_ONES), scoringFaces(THREE_FOURS));
        int hot = points(scoringFaces(FIVE_ONES));
        String[] all = {"2", "0", "Alice", "Bob", "all", "", "all", "b", "quit"};
        String[] toggled = {"2", "0", "Alice", "Bob", "1 2 3 4 5", "", "1 2 3", "b", "quit"};
        playLine("line hold-all both rolls", 10_000, 100, rolls, all, "Alice", banked, hot);
        playLine("line toggle each die", 10_000, 100, rolls, toggled, "Alice", banked, hot);
        playTui("tui hold-all both rolls", 10_000, 100, rolls, "Alice", banked, hot, keys -> {
            seat(keys, "Alice", "Bob");
            holdBest(keys);
            roll(keys);
            holdBest(keys);
            bank(keys);
            quit(keys);
        });
        playTui("tui toggle each die", 10_000, 100, rolls, "Alice", banked, hot, keys -> {
            seat(keys, "Alice", "Bob");
            toggle(keys, "12345");
            confirm(keys);
            roll(keys);
            toggle(keys, "123");
            confirm(keys);
            bank(keys);
            quit(keys);
        });
    }

    /** Rook holds every scoring die and banks the hand. */
    private static void botHotDice() {
        int[][] rolls = {FIVE_ONES, THREE_FOURS};
        int banked = bankOf(scoringFaces(FIVE_ONES), scoringFaces(THREE_FOURS));
        int hot = points(scoringFaces(FIVE_ONES));
        String[] line = {"2", "0", "bot", "Bea", "quit"};
        playLine("line bot hot dice", 10_000, 750, rolls, line, "Rook", banked, hot);
        playTui("tui bot hot dice", 10_000, 750, rolls, "Rook", banked, hot, keys -> {
            keys.add((int) '2');
            keys.add((int) '0');
            type(keys, "bot");
            type(keys, "Bea");
            quit(keys);
        });
    }

    /**
     * The re-roll by itself is under the opening score.
     * Both holds together are enough to bank.
     */
    private static void hotBeforeOnTheBoard() {
        int opening = 750;
        int reroll = points(scoringFaces(THREE_FOURS));
        check(reroll < opening, "re-roll alone is short of the opening score");
        int[][] rolls = {FIVE_ONES, THREE_FOURS};
        int banked = bankOf(scoringFaces(FIVE_ONES), scoringFaces(THREE_FOURS));
        int hot = points(scoringFaces(FIVE_ONES));
        String[] line = {"2", "0", "Alice", "Bob", "all", "", "all", "b", "quit"};
        playLine("line hot before on the board", 10_000, opening, rolls, line, "Alice", banked, hot);
        playTui("tui hot before on the board", 10_000, opening, rolls, "Alice", banked, hot, keys -> {
            seat(keys, "Alice", "Bob");
            holdBest(keys);
            roll(keys);
            holdBest(keys);
            bank(keys);
            quit(keys);
        });
    }

    /**
     * Alice banks the 1 and the 5 and leaves 3 dice. Bob continues from that
     * bank, scores those three (the hand is already hot), then scores the re-roll.
     * The other three dice on Alice's roll are dead, so she does not hold a straight.
     */
    private static void hotAfterContinue() {
        int[][] rolls = {ONE_AND_FIVE, THREE_ONES, THREE_FOURS};
        int alice = points(scoringFaces(ONE_AND_FIVE));
        int hot = alice + points(THREE_ONES);
        int banked = hot + points(scoringFaces(THREE_FOURS));
        String[] line = {"2", "0", "Alice", "Bob", "all", "b", "c", "all", "", "all", "b", "quit"};
        Result lineResult = playLine("line hot after continue", 20_000, 100, rolls, line, "Bob", banked, hot);
        if (lineResult != null) {
            check(score(lineResult, "Alice") == alice, "line hot after continue keeps Alice's bank");
        }
        Result tuiResult = playTui("tui hot after continue", 20_000, 100, rolls, "Bob", banked, hot, keys -> {
            seat(keys, "Alice", "Bob");
            holdBest(keys);
            bank(keys);
            keys.add((int) 'c');
            holdBest(keys);
            roll(keys);
            holdBest(keys);
            bank(keys);
            quit(keys);
        });
        if (tuiResult != null) {
            check(score(tuiResult, "Alice") == alice, "tui hot after continue keeps Alice's bank");
        }
    }

    private static Result playLine(String label, int winning, int opening, int[][] rolls, String[] input,
                                   String banker, int banked, int hotHand) {
        ScriptedDice dice = new ScriptedDice(rolls);
        StringWriter output = new StringWriter();
        LineView line = new LineView(new StringReader(String.join("\n", input) + "\n"), output, winning, opening);
        Recording recording = new Recording(line);
        if (!run(label, winning, opening, dice, recording, output)) {
            return null;
        }
        return expect(label, dice, recording, output.toString(), banker, banked, hotHand);
    }

    private static Result playTui(String label, int winning, int opening, int[][] rolls, String banker,
                                  int banked, int hotHand, KeyScript script) {
        ScriptedDice dice = new ScriptedDice(rolls);
        ArrayDeque<Integer> keys = new ArrayDeque<>();
        script.write(keys);
        TuiView tui = TuiView.scripted(keys, 80, 24, true, true, winning, opening);
        Recording recording = new Recording(tui);
        StringWriter sink = new StringWriter();
        if (!run(label, winning, opening, dice, recording, sink)) {
            System.out.println("--- frames ---");
            System.out.println(String.join("\n", tui.frames()));
            return null;
        }
        return expect(label, dice, recording, String.join("\n", tui.frames()), banker, banked, hotHand);
    }

    private static boolean run(String label, int winning, int opening, ScriptedDice dice, Recording recording,
                               StringWriter unused) {
        try {
            new Game(recording, dice, winning, opening).play();
            return true;
        } catch (RuntimeException e) {
            failed++;
            System.out.println("FAIL: " + label + " threw " + e.getMessage());
            e.printStackTrace(System.out);
            if (unused.getBuffer().length() > 0) {
                System.out.println("--- output ---");
                System.out.println(unused);
            }
            return false;
        }
    }

    private static Result expect(String label, ScriptedDice dice, Recording recording, String shown,
                                 String banker, int banked, int hotHand) {
        String money = Scorer.format(banked);
        boolean announced = shown.contains("banks " + money) || shown.contains("BANKED " + money);
        check(announced, label + " announces " + money);
        check(recording.hotHands.contains(hotHand), label + " hot hand still has " + Scorer.format(hotHand));
        Player player = find(recording.players, banker);
        check(player != null && player.score == banked, label + " " + banker + " score is " + money
                + (player == null ? " (missing)" : " (was " + player.score + ")"));
        check(player != null && player.onBoard, label + " " + banker + " is on the board");
        try {
            dice.assertDrained();
        } catch (RuntimeException e) {
            failed++;
            System.out.println("FAIL: " + label + " " + e.getMessage());
        }
        if (!announced) {
            System.out.println("--- output ---");
            System.out.println(shown);
        }
        return new Result(recording.players);
    }

    private static void seat(ArrayDeque<Integer> keys, String first, String second) {
        keys.add((int) '2');
        keys.add((int) '0');
        type(keys, first);
        type(keys, second);
    }

    private static void type(ArrayDeque<Integer> keys, String text) {
        for (int i = 0; i < text.length(); i++) {
            keys.add((int) text.charAt(i));
        }
        keys.add((int) '\n');
    }

    private static void holdBest(ArrayDeque<Integer> keys) {
        keys.add((int) 'a');
        confirm(keys);
    }

    private static void confirm(ArrayDeque<Integer> keys) {
        keys.add((int) '\n');
    }

    private static void roll(ArrayDeque<Integer> keys) {
        keys.add((int) 'r');
    }

    private static void bank(ArrayDeque<Integer> keys) {
        keys.add((int) 'b');
    }

    private static void quit(ArrayDeque<Integer> keys) {
        keys.add((int) 'q');
        keys.add((int) 'y');
    }

    private static void toggle(ArrayDeque<Integer> keys, String digits) {
        for (int i = 0; i < digits.length(); i++) {
            keys.add((int) digits.charAt(i));
        }
    }

    private static int points(int[] faces) {
        return Scorer.score(faces).points();
    }

    private static int[] scoringFaces(int[] roll) {
        return LineView.facesAt(roll, Scorer.scoringIndexes(roll));
    }

    private static int bankOf(int[]... holds) {
        int total = 0;
        for (int[] hold : holds) {
            total += points(hold);
        }
        return total;
    }

    private static int score(Result result, String name) {
        Player player = find(result.players, name);
        return player == null ? -1 : player.score;
    }

    private static Player find(List<Player> players, String name) {
        if (players == null) {
            return null;
        }
        for (Player player : players) {
            if (player.name.equals(name)) {
                return player;
            }
        }
        return null;
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            failed++;
            System.out.println("FAIL: " + label);
        }
    }

    @FunctionalInterface
    private interface KeyScript {
        void write(ArrayDeque<Integer> keys);
    }

    private record Result(List<Player> players) {}

    /** Remembers the live player list and every hot-dice hand total. Forwards every call. */
    private static final class Recording implements View {
        private final View next;
        private List<Player> players = List.of();
        private final List<Integer> hotHands = new ArrayList<>();

        private Recording(View next) {
            this.next = next;
        }

        @Override
        public void intro() {
            next.intro();
        }

        @Override
        public int readPlayerCount() {
            return next.readPlayerCount();
        }

        @Override
        public int readBotCount(int seats) {
            return next.readBotCount(seats);
        }

        @Override
        public void soloAgainstComputer() {
            next.soloAgainstComputer();
        }

        @Override
        public Player readSeat(int seat, List<Player> seated, boolean onlyHuman) {
            players = seated;
            return next.readSeat(seat, seated, onlyHuman);
        }

        @Override
        public void seatComputer(Player player) {
            next.seatComputer(player);
        }

        @Override
        public void goesFirst(Player player) {
            next.goesFirst(player);
        }

        @Override
        public void scores(List<Player> players) {
            this.players = players;
            next.scores(players);
        }

        @Override
        public void startTurn(Player player) {
            next.startTurn(player);
        }

        @Override
        public void startContinued(Player player, int diceLeft) {
            next.startContinued(player, diceLeft);
        }

        @Override
        public void startNewHand(Player player) {
            next.startNewHand(player);
        }

        @Override
        public void showRoll(Player player, int hand, int[] faces, int[] kept) {
            next.showRoll(player, hand, faces, kept);
        }

        @Override
        public int[] chooseHold(Player player, int[] faces) {
            return next.chooseHold(player, faces);
        }

        @Override
        public int[] botHold(Player player, int[] faces) {
            return next.botHold(player, faces);
        }

        @Override
        public void hotDice(Player player, int hand) {
            hotHands.add(hand);
            next.hotDice(player, hand);
        }

        @Override
        public void mustRoll(Player player, int hand, int left, int[] kept) {
            next.mustRoll(player, hand, left, kept);
        }

        @Override
        public boolean chooseBank(Player player, int hand, int left, int[] kept) {
            return next.chooseBank(player, hand, left, kept);
        }

        @Override
        public boolean chooseContinue(Player player, String banker, int diceLeft, int banked) {
            return next.chooseContinue(player, banker, diceLeft, banked);
        }

        @Override
        public void bust(Player player, int pointsLost) {
            next.bust(player, pointsLost);
        }

        @Override
        public void doubleReRoll(Player player) {
            next.doubleReRoll(player);
        }

        @Override
        public void banked(Player player, int amount, int scoreBefore, boolean opened) {
            next.banked(player, amount, scoreBefore, opened);
        }

        @Override
        public boolean win(Player winner, List<Player> players) {
            this.players = players;
            return next.win(winner, players);
        }

        @Override
        public void goodbye() {
            next.goodbye();
        }

        @Override
        public void flush() {
            next.flush();
        }
    }

    private static final class ScriptedDice implements DieSource {
        private final ArrayDeque<int[]> rolls = new ArrayDeque<>();

        private ScriptedDice(int[][] script) {
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

        private void assertDrained() {
            if (!rolls.isEmpty()) {
                throw new IllegalStateException(rolls.size() + " scripted rolls were not used");
            }
        }
    }
}
