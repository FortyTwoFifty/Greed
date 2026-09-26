package greed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Today's scrolling transcript. Strings here are the ones {@code GreedTest} matches. */
final class LineView implements View {
    private final BufferedReader in;
    private final PrintWriter out;
    private final int winningScore;
    private final int openingScore;

    LineView(Reader in, Writer out, int winningScore, int openingScore) {
        this.in = in instanceof BufferedReader buffered ? buffered : new BufferedReader(in);
        this.out = out instanceof PrintWriter printer ? printer : new PrintWriter(out, true);
        this.winningScore = winningScore;
        this.openingScore = openingScore;
    }

    /** The rules block, one string per line. Blank lines are empty strings. */
    static List<String> rulesText(int winningScore, int openingScore) {
        List<String> lines = new ArrayList<>();
        lines.add("GREED");
        lines.add("First player to " + Scorer.format(winningScore) + " points wins.");
        lines.add("");
        lines.add("Roll dice and hold what scores:");
        lines.add("  1 = 100    5 = 50");
        lines.add("  Three of a kind = face × 100, except three 1s = 1,000");
        lines.add("  Four of a kind = twice the three-of-a-kind score");
        lines.add("  Five of a kind = twice the four-of-a-kind score");
        lines.add("A roll with no 1, 5, or three of a kind is a bust. Unbanked points from the hand are lost.");
        lines.add("If you score every die still in the hand, you roll all 5 again before you can bank.");
        lines.add("The first bank that puts you on the board must be at least " + Scorer.format(openingScore) + ".");
        lines.add("After that you may bank any hand. The next player can roll the dice you left,");
        lines.add("starting from 0, or throw all 5 dice as a new hand.");
        lines.add("Type bot as a player's name to seat the computer. One player sits with Rook.");
        lines.add("Type ? for these rules, or quit to leave.");
        lines.add("");
        return lines;
    }

    @Override
    public void intro() {
        for (String line : rulesText(winningScore, openingScore)) {
            out.println(line);
        }
    }

    @Override
    public int readPlayerCount() {
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

    @Override
    public void soloAgainstComputer() {
        out.println("One human sits with the computer. Rook takes the other seat.");
    }

    @Override
    public Player readSeat(int seat, List<Player> seated, boolean onlyHuman) {
        while (true) {
            prompt("Name for player " + seat + " (bot seats the computer): ");
            String name = readLine();
            if (isBotToken(name)) {
                if (onlyHuman) {
                    out.println("Type your name. Rook takes the other seat.");
                    continue;
                }
                String botName = Bot.nameFor(seated);
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
            if (taken(seated, name)) {
                out.println(name + " is already in the game.");
                continue;
            }
            return new Player(name, false);
        }
    }

    @Override
    public void seatComputer(Player player) {
        out.println(player.name + " sits down for the computer.");
    }

    @Override
    public void goesFirst(Player player) {
        out.println(player.name + " goes first.");
    }

    @Override
    public void scores(List<Player> players) {
        out.println();
        printScores(players);
    }

    @Override
    public void startTurn(Player player) {
        out.println(player.name + "'s turn.");
    }

    @Override
    public void startContinued(Player player, int diceLeft) {
        out.println(player.name + " rolls the " + diceWord(diceLeft) + " left unscored.");
    }

    @Override
    public void startNewHand(Player player) {
        out.println(player.name + " starts a new hand.");
    }

    @Override
    public void showRoll(Player player, int hand, int[] faces, int[] kept) {
        out.println();
        out.println("Hand total: " + Scorer.format(hand));
        out.println("Rolling " + diceWord(faces.length) + ":");
        printDice(faces);
    }

    @Override
    public int[] chooseHold(Player player, int[] roll) {
        int[] every = Scorer.scoringIndexes(roll);
        Scorer.Scoring preview = Scorer.score(facesAt(roll, every));
        while (true) {
            out.println("Hold dice by number (example: 1,3), or 'all'.");
            if (preview.valid()) {
                out.println("Every scoring die: " + preview.detail() + ".");
            }
            prompt("> ");
            String line = readLine();
            if (line.equals("?")) {
                intro();
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

    @Override
    public int[] botHold(Player player, int[] roll) {
        int[] every = Scorer.scoringIndexes(roll);
        Scorer.Scoring preview = Scorer.score(facesAt(roll, every));
        if (!preview.valid()) {
            throw new IllegalStateException("bot hold did not score: " + preview.detail());
        }
        out.println(player.name + " holds every scoring die.");
        out.println("Held " + preview.detail() + ".");
        return every;
    }

    @Override
    public void hotDice(Player player, int hand) {
        out.println("Hand total: " + Scorer.format(hand) + ".");
        out.println("Every die scored. You have to roll all 5 dice again before you can bank.");
        if (player.computer) {
            out.println(player.name + " rolls all 5.");
        } else {
            acknowledge("Press Enter to roll.");
        }
    }

    @Override
    public void mustRoll(Player player, int hand, int left, int[] kept) {
        out.println("Hand total: " + Scorer.format(hand) + ".");
        out.println(remain(left));
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

    @Override
    public boolean chooseBank(Player player, int hand, int left, int[] kept) {
        out.println("Hand total: " + Scorer.format(hand) + ".");
        out.println(remain(left));
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
                intro();
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

    @Override
    public boolean chooseContinue(Player player, String banker, int diceLeft, int banked) {
        out.println(player.name + ", " + banker + " left " + diceWord(diceLeft) + " unscored.");
        out.println("A continued hand starts at 0. " + banker + " keeps the points just banked.");
        if (player.computer) {
            Bot.Choice choice = Bot.cont(diceLeft);
            out.println(player.name + " " + choice.reason());
            return choice.yes();
        }
        while (true) {
            out.println("[C]ontinue by rolling " + (diceLeft == 1 ? "that die" : "those dice")
                    + ", or [N]ew hand with 5 dice?");
            prompt("> ");
            String line = readLine();
            if (line.equals("?")) {
                intro();
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

    @Override
    public void bust(Player player, int pointsLost) {
        out.println("No 1, no 5, and no three of a kind.");
        if (pointsLost > 0) {
            out.println("Bust. " + player.name + " loses " + Scorer.format(pointsLost) + " unbanked points.");
        } else {
            out.println("Bust. " + player.name + " scores nothing this turn.");
        }
    }

    @Override
    public void banked(Player player, int amount, int scoreBefore, boolean opened) {
        out.println(player.name + " banks " + Scorer.format(amount) + " points.");
        if (opened) {
            out.println(player.name + " gets on the board with " + Scorer.format(player.score) + ".");
        } else {
            out.println(player.name + " now has " + Scorer.format(player.score) + ".");
        }
    }

    @Override
    public boolean win(Player winner, List<Player> players) {
        out.println();
        out.println(winner.name + " wins with " + Scorer.format(winner.score) + " points.");
        out.println();
        printScores(players);
        return false;
    }

    @Override
    public void goodbye() {
        out.println();
        out.println("Goodbye.");
    }

    @Override
    public void flush() {
        out.flush();
    }

    private void acknowledge(String message) {
        while (true) {
            out.println(message);
            prompt("> ");
            String line = readLine();
            if (line.equals("?")) {
                intro();
                continue;
            }
            if (line.equalsIgnoreCase("b") || line.equalsIgnoreCase("bank")) {
                out.println("You cannot bank on this roll.");
                continue;
            }
            return;
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
            out.printf(Locale.US, "  %-" + nameWidth + "s  %7s   %s%n",
                    player.name, Scorer.format(player.score), standing);
        }
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
                throw new Quit();
            }
            line = line.trim();
            if (line.equalsIgnoreCase("quit")) {
                throw new Quit();
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

    static int[] facesAt(int[] roll, int[] indexes) {
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

    static String diceWord(int count) {
        return count == 1 ? "1 die" : count + " dice";
    }

    private static String remain(int left) {
        return diceWord(left) + " " + (left == 1 ? "remains." : "remain.");
    }
}
