package greed;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

    private final View view;
    private final DieSource dice;
    private final int winningScore;
    private final int openingScore;

    public Game(Reader in, Writer out, DieSource dice) {
        this(in, out, dice, WINNING_SCORE, OPENING_SCORE);
    }

    public Game(Reader in, Writer out, DieSource dice, int winningScore, int openingScore) {
        this(new LineView(in, out, winningScore, openingScore), dice, winningScore, openingScore);
    }

    public Game(View view, DieSource dice, int winningScore, int openingScore) {
        if (openingScore <= 0 || winningScore < openingScore) {
            throw new IllegalArgumentException("winning score must be at least the opening score");
        }
        this.view = view;
        this.dice = dice;
        this.winningScore = winningScore;
        this.openingScore = openingScore;
    }

    public static void main(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException rejected) {
            System.err.println(rejected.getMessage());
            System.exit(2);
            return;
        }
        DieSource dice = new RandomDice(new Random());
        if (options.plain || System.console() == null) {
            new Game(new InputStreamReader(System.in), new OutputStreamWriter(System.out), dice).play();
            return;
        }
        Terminal terminal = new Terminal();
        try {
            terminal.enter();
        } catch (RuntimeException failed) {
            terminal.restore();
            new Game(new InputStreamReader(System.in), new OutputStreamWriter(System.out), dice).play();
            return;
        }
        terminal.installHooks();
        try {
            boolean again = true;
            while (again) {
                TuiView view = new TuiView(terminal, options, WINNING_SCORE, OPENING_SCORE);
                again = new Game(view, dice, WINNING_SCORE, OPENING_SCORE).play();
            }
        } finally {
            terminal.restore();
        }
    }

    /** @return true when the player asks for another game */
    public boolean play() {
        try {
            view.intro();
            return run(readPlayers());
        } catch (Quit ended) {
            view.goodbye();
            return false;
        } finally {
            view.flush();
        }
    }

    private List<Player> readPlayers() {
        int count = view.readPlayerCount();
        if (count == 1) {
            view.soloAgainstComputer();
        }
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            players.add(view.readSeat(i, players, count == 1));
        }
        if (count == 1) {
            Player rook = new Player(Bot.nameFor(players), true);
            players.add(rook);
            view.seatComputer(rook);
        }
        view.goesFirst(players.get(0));
        return players;
    }

    private boolean run(List<Player> players) {
        int current = 0;
        Pending pending = null;
        while (true) {
            Player player = players.get(current);
            view.scores(players);
            int diceToRoll = 5;
            if (pending != null) {
                if (view.chooseContinue(player, pending.banker, pending.diceLeft, pending.banked)) {
                    view.startContinued(player, pending.diceLeft);
                    diceToRoll = pending.diceLeft;
                } else {
                    view.startNewHand(player);
                }
                pending = null;
            } else {
                view.startTurn(player);
            }

            Turn turn = playTurn(player, diceToRoll);
            if (turn.busted) {
                view.bust(player, turn.pointsLost);
                current = next(current, players.size());
                continue;
            }

            int scoreBefore = player.score;
            player.score += turn.banked;
            boolean opened = !player.onBoard;
            if (opened) {
                player.onBoard = true;
            }
            view.banked(player, turn.banked, scoreBefore, opened);
            if (player.score >= winningScore) {
                return view.win(player, players);
            }
            pending = new Pending(player.name, turn.diceLeft, turn.banked);
            current = next(current, players.size());
        }
    }

    private Turn playTurn(Player player, int diceCount) {
        int hand = 0;
        int[] kept = new int[0];
        while (true) {
            int[] roll = dice.roll(diceCount);
            view.showRoll(player, hand, roll, kept);
            if (diceCount == 2 && isNonScoringDouble(roll) && !player.computer) {
                view.doubleReRoll(player);
                continue;
            }
            if (!Scorer.canScore(roll)) {
                return Turn.bust(hand);
            }

            int[] held = player.computer ? view.botHold(player, roll) : view.chooseHold(player, roll);
            Scorer.Scoring scoring = Scorer.score(LineView.facesAt(roll, held));
            if (!scoring.valid()) {
                throw new IllegalStateException("hold did not score: " + scoring.detail());
            }
            hand += scoring.points();
            kept = concat(kept, LineView.facesAt(roll, held));
            int left = diceCount - held.length;

            if (left == 0) {
                view.hotDice(player, hand);
                diceCount = 5;
                kept = new int[0];
                continue;
            }
            if (player.onBoard || hand >= openingScore) {
                if (view.chooseBank(player, hand, left, kept)) {
                    return Turn.bank(hand, left);
                }
            } else {
                view.mustRoll(player, hand, left, kept);
            }
            diceCount = left;
        }
    }

    /** A double of two dice that doesn't normally score (2, 3, 4, or 6). */
    private static boolean isNonScoringDouble(int[] roll) {
        if (roll.length != 2 || roll[0] != roll[1]) {
            return false;
        }
        int face = roll[0];
        return face == 2 || face == 3 || face == 4 || face == 6;
    }

    private static int[] concat(int[] left, int[] right) {
        int[] both = new int[left.length + right.length];
        System.arraycopy(left, 0, both, 0, left.length);
        System.arraycopy(right, 0, both, left.length, right.length);
        return both;
    }

    private static int next(int current, int count) {
        return (current + 1) % count;
    }

    private record Pending(String banker, int diceLeft, int banked) {}

    private record Turn(boolean busted, int banked, int diceLeft, int pointsLost) {
        static Turn bust(int pointsLost) {
            return new Turn(true, 0, 0, pointsLost);
        }

        static Turn bank(int banked, int diceLeft) {
            return new Turn(false, banked, diceLeft, 0);
        }
    }
}
