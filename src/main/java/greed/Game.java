package greed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Console game of Greed for two or more players.
 * A turn that scores every die still in hand must roll all five again
 * before banking. The next player may roll dice the banker left unscored;
 * that continued hand starts at zero and can itself be passed on.
 */
public final class Game {
    public static final int WINNING_SCORE = 10_000;
    public static final int OPENING_SCORE = 750;

    private final BufferedReader in;
    private final PrintWriter out;
    private final DieSource dice;
    private final int winningScore;
    private final int openingScore;

    public Game(Reader in, Writer out, DieSource dice) {
        this(in, out, dice, WINNING_SCORE, OPENING_SCORE);
    }

    public Game(Reader in, Writer out, DieSource dice, int winningScore, int openingScore) {
        if (openingScore <= 0 || winningScore < openingScore) {
            throw new IllegalArgumentException("winning score must be at least the opening score");
        }
        this.in = in instanceof BufferedReader buffered ? buffered : new BufferedReader(in);
        this.out = out instanceof PrintWriter printer ? printer : new PrintWriter(out, true);
        this.dice = dice;
        this.winningScore = winningScore;
        this.openingScore = openingScore;
    }

    public static void main(String[] args) {
        new Game(
                new InputStreamReader(System.in),
                new OutputStreamWriter(System.out),
                new RandomDice(new Random()))
                .play();
    }

    public void play() {
        try {
            printIntro();
            run(readPlayers());
        } catch (InputEnded ended) {
            out.println();
            out.println("Goodbye.");
        } finally {
            out.flush();
        }
    }

    private void run(List<Player> players) {
        int current = 0;
        Pending pending = null;
        while (true) {
            Player player = players.get(current);
            out.println();
            printScores(players);
            int diceToRoll = 5;
            if (pending != null) {
                if (wantsContinue(player, pending)) {
                    out.println(player.name + " rolls the " + diceWord(pending.diceLeft) + " left unscored.");
                    diceToRoll = pending.diceLeft;
                } else {
                    out.println(player.name + " starts a new hand.");
                }
                pending = null;
            } else {
                out.println(player.name + "'s turn.");
            }

            Turn turn = playTurn(player, diceToRoll);
            if (turn.busted) {
                if (turn.pointsLost > 0) {
                    out.println("Bust. " + player.name + " loses " + Scorer.format(turn.pointsLost) + " unbanked points.");
                } else {
                    out.println("Bust. " + player.name + " scores nothing this turn.");
                }
                current = next(current, players.size());
                continue;
            }

            player.score += turn.banked;
            out.println(player.name + " banks " + Scorer.format(turn.banked) + " points.");
            if (!player.onBoard) {
                player.onBoard = true;
                out.println(player.name + " gets on the board with " + Scorer.format(player.score) + ".");
            } else {
                out.println(player.name + " now has " + Scorer.format(player.score) + ".");
            }
            if (player.score >= winningScore) {
                out.println();
                out.println(player.name + " wins with " + Scorer.format(player.score) + " points.");
                out.println();
                printScores(players);
                return;
            }
            pending = new Pending(player.name, turn.diceLeft);
            current = next(current, players.size());
        }
    }

    private Turn playTurn(Player player, int diceCount) {
        int hand = 0;
        while (true) {
            out.println();
            out.println("Hand total: " + Scorer.format(hand));
            out.println("Rolling " + diceWord(diceCount) + ":");
            int[] roll = dice.roll(diceCount);
            printDice(roll);
            if (!Scorer.canScore(roll)) {
                out.println("No 1, no 5, and no three of a kind.");
                return Turn.bust(hand);
            }

            int[] held = chooseHeld(player, roll);
            Scorer.Scoring scoring = Scorer.score(facesAt(roll, held));
            hand += scoring.points();
            int left = diceCount - held.length;
            out.println("Hand total: " + Scorer.format(hand) + ".");

            if (left == 0) {
                out.println("Every die scored. You have to roll all 5 dice again before you can bank.");
                if (player.computer) {
                    out.println(player.name + " rolls all 5.");
                } else {
                    acknowledge("Press Enter to roll.");
                }
                diceCount = 5;
                continue;
            }

            out.println(diceWord(left) + " " + (left == 1 ? "remains." : "remain."));
            if (player.onBoard || hand >= openingScore) {
                if (wantsBank(player, hand, left)) {
                    return Turn.bank(hand, left);
                }
            } else {
                String who = player.computer ? player.name : "You";
                String verb = player.computer ? " needs " : " need ";
                out.println(who + verb + Scorer.format(openingScore)
                        + " in this hand to get on the board. Banking "
                        + Scorer.format(hand) + " is not allowed yet.");
                if (player.computer) {
                    out.println(player.name + " rolls the remaining " + diceWord(left) + ".");
                } else {
                    acknowledge("Press Enter to roll the remaining " + diceWord(left) + ".");
                }
            }
            diceCount = left;
        }
    }

    private int[] chooseHeld(Player player, int[] roll) {
        int[] every = Scorer.scoringIndexes(roll);
        Scorer.Scoring preview = Scorer.score(facesAt(roll, every));
        if (player.computer) {
            if (!preview.valid()) {
                throw new IllegalStateException("bot hold did not score: " + preview.detail());
            }
            out.println(player.name + " holds every scoring die.");
            out.println("Held " + preview.detail() + ".");
            return every;
        }
        while (true) {
            out.println("Hold dice by number (example: 1,3), or 'all'.");
            if (preview.valid()) {
                out.println("Every scoring die: " + preview.detail() + ".");
            }
            prompt("> ");
            String line = readLine();
            if (line.equals("?")) {
                printIntro();
                continue;
            }
            int[] indexes = line.equalsIgnoreCase("all") || line.equalsIgnoreCase("a")
                    ? every
                    : parseIndexes(line, roll.length);
            if (indexes == null) {
                out.println("Use the die numbers shown, separated by spaces or commas.");
                continue;
            }
            if (indexes.length == 0) {
                out.println("Hold at least one scoring die.");
                continue;
            }
            Scorer.Scoring scoring = Scorer.score(facesAt(roll, indexes));
            if (!scoring.valid()) {
                out.println(scoring.detail());
                continue;
            }
            out.println("Held " + scoring.detail() + ".");
            return indexes;
        }
    }

    private boolean wantsBank(Player player, int hand, int left) {
        String question = "[R]oll the remaining " + diceWord(left) + ", or [B]ank " + Scorer.format(hand) + "?";
        if (player.computer) {
            out.println(question);
            Bot.Choice choice = Bot.bank(hand, left, player.onBoard, player.score, openingScore, winningScore);
            out.println(player.name + " " + choice.reason());
            return choice.yes();
        }
        while (true) {
            out.println(question);
            prompt("> ");
            String line = readLine();
            if (line.equals("?")) {
                printIntro();
                continue;
            }
            if (line.equalsIgnoreCase("r") || line.equalsIgnoreCase("roll")) {
                return false;
            }
            if (line.equalsIgnoreCase("b") || line.equalsIgnoreCase("bank")) {
                return true;
            }
            out.println("Type R to roll or B to bank.");
        }
    }

    private boolean wantsContinue(Player player, Pending pending) {
        out.println(player.name + ", " + pending.banker + " left " + diceWord(pending.diceLeft) + " unscored.");
        out.println("A continued hand starts at 0. " + pending.banker + " keeps the points just banked.");
        if (player.computer) {
            Bot.Choice choice = Bot.cont(pending.diceLeft);
            out.println(player.name + " " + choice.reason());
            return choice.yes();
        }
        while (true) {
            out.println("[C]ontinue by rolling " + (pending.diceLeft == 1 ? "that die" : "those dice")
                    + ", or [N]ew hand with 5 dice?");
            prompt("> ");
            String line = readLine();
            if (line.equals("?")) {
                printIntro();
                continue;
            }
            if (line.equalsIgnoreCase("c") || line.equalsIgnoreCase("continue")) {
                return true;
            }
            if (line.equalsIgnoreCase("n") || line.equalsIgnoreCase("new")) {
                return false;
            }
            out.println("Type C to continue or N to start a new hand.");
        }
    }

    /** Enter continues. B explains why banking is closed. ? reprints the rules. */
    private void acknowledge(String message) {
        while (true) {
            out.println(message);
            prompt("> ");
            String line = readLine();
            if (line.equals("?")) {
                printIntro();
                continue;
            }
            if (line.equalsIgnoreCase("b") || line.equalsIgnoreCase("bank")) {
                out.println("You cannot bank on this roll.");
                continue;
            }
            return;
        }
    }

    private List<Player> readPlayers() {
        int count = askPlayerCount();
        if (count == 1) {
            out.println("One human sits with the computer. Rook takes the other seat.");
        }
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            players.add(askSeat(i, players, count == 1));
        }
        if (count == 1) {
            Player rook = new Player(Bot.nameFor(players), true);
            players.add(rook);
            out.println(rook.name + " sits down for the computer.");
        }
        out.println(players.get(0).name + " goes first.");
        return players;
    }

    private Player askSeat(int seat, List<Player> players, boolean onlyHuman) {
        while (true) {
            prompt("Name for player " + seat + " (bot seats the computer): ");
            String name = readLine();
            if (isBotToken(name)) {
                if (onlyHuman) {
                    out.println("Type your name. Rook takes the other seat.");
                    continue;
                }
                String botName = Bot.nameFor(players);
                out.println(botName + " sits down for the computer.");
                return new Player(botName, true);
            }
            if (name.isEmpty()) {
                name = "Player " + seat;
            }
            if (name.length() > 24) {
                out.println("Use 24 characters or fewer.");
                continue;
            }
            if (taken(players, name)) {
                out.println(name + " is already in the game.");
                continue;
            }
            return new Player(name, false);
        }
    }

    private int askPlayerCount() {
        while (true) {
            prompt("How many players? (1-10) ");
            String line = readLine();
            try {
                int count = Integer.parseInt(line);
                if (count >= 1 && count <= 10) {
                    return count;
                }
            } catch (NumberFormatException ignored) {
                // Ask again below.
            }
            out.println("Enter a number from 1 to 10.");
        }
    }

    private void printScores(List<Player> players) {
        int nameWidth = 0;
        for (Player player : players) {
            nameWidth = Math.max(nameWidth, player.name.length());
        }
        out.println("Scoreboard");
        for (Player player : players) {
            String standing = player.onBoard
                    ? "on the board"
                    : "needs " + Scorer.format(openingScore) + " to get on";
            if (player.computer) {
                standing = standing + " · bot";
            }
            out.printf(java.util.Locale.US, "  %-" + nameWidth + "s  %7s   %s%n",
                    player.name, Scorer.format(player.score), standing);
        }
        out.println();
    }

    private void printIntro() {
        out.println("GREED");
        out.println("First player to " + Scorer.format(winningScore) + " points wins.");
        out.println();
        out.println("Roll dice and hold what scores:");
        out.println("  1 = 100    5 = 50");
        out.println("  Three of a kind = face × 100, except three 1s = 1,000");
        out.println("  Four of a kind = twice the three-of-a-kind score");
        out.println("  Five of a kind = twice the four-of-a-kind score");
        out.println("A roll with no 1, 5, or three of a kind is a bust. Unbanked points from the hand are lost.");
        out.println("If you score every die still in the hand, you roll all 5 again before you can bank.");
        out.println("The first bank that puts you on the board must be at least " + Scorer.format(openingScore) + ".");
        out.println("After that you may bank any hand. The next player can roll the dice you left,");
        out.println("starting from 0, or throw all 5 dice as a new hand.");
        out.println("Type bot as a player's name to seat the computer. One player sits with Rook.");
        out.println("Type ? for these rules, or quit to leave.");
        out.println();
    }

    private void printDice(int[] faces) {
        StringBuilder top = new StringBuilder();
        StringBuilder mid = new StringBuilder();
        StringBuilder bot = new StringBuilder();
        StringBuilder idx = new StringBuilder();
        for (int i = 0; i < faces.length; i++) {
            if (i > 0) {
                top.append(' ');
                mid.append(' ');
                bot.append(' ');
                idx.append(' ');
            }
            top.append("┌───┐");
            mid.append(String.format("│ %d │", faces[i]));
            bot.append("└───┘");
            idx.append(String.format("  %d  ", i + 1));
        }
        out.println(top);
        out.println(mid);
        out.println(bot);
        out.println(idx);
    }

    private String readLine() {
        try {
            String line = in.readLine();
            if (line == null) {
                throw new InputEnded();
            }
            line = line.trim();
            if (line.equalsIgnoreCase("quit")) {
                throw new InputEnded();
            }
            return line;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void prompt(String text) {
        out.print(text);
        out.flush();
    }

    private static int next(int current, int count) {
        return (current + 1) % count;
    }

    private static boolean taken(List<Player> players, String name) {
        for (Player player : players) {
            if (player.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBotToken(String name) {
        return name.equalsIgnoreCase("bot") || name.equalsIgnoreCase("computer");
    }

    private static int[] facesAt(int[] roll, int[] indexes) {
        int[] faces = new int[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            faces[i] = roll[indexes[i]];
        }
        return faces;
    }

    /** @return zero-based indexes, or null when the text is not a selection */
    private static int[] parseIndexes(String line, int max) {
        if (line.isEmpty()) {
            return null;
        }
        String[] tokens = line.split("[,\\s]+");
        int[] indexes = new int[tokens.length];
        boolean[] seen = new boolean[max];
        for (int i = 0; i < tokens.length; i++) {
            int number;
            try {
                number = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (number < 1 || number > max || seen[number - 1]) {
                return null;
            }
            seen[number - 1] = true;
            indexes[i] = number - 1;
        }
        return indexes;
    }

    private static String diceWord(int count) {
        return count == 1 ? "1 die" : count + " dice";
    }

    private record Pending(String banker, int diceLeft) {}

    private record Turn(boolean busted, int banked, int diceLeft, int pointsLost) {
        static Turn bust(int pointsLost) {
            return new Turn(true, 0, 0, pointsLost);
        }

        static Turn bank(int banked, int diceLeft) {
            return new Turn(false, banked, diceLeft, 0);
        }
    }

    private static final class InputEnded extends RuntimeException {}
}
