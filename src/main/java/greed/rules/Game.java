package greed.rules;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import greed.view.LineView;
import greed.view.Terminal;
import greed.view.TuiView;

/**
 * Console game of Greed for two or more players.
 * A turn that scores every die still in hand must roll all five again
 * before banking. The next player may roll dice the banker left unscored;
 * that continued hand starts at the banked total and can itself be passed on.
 * A bust loses it.
 */
public final class Game {
    public static final int WINNING_SCORE = 10_000;
    public static final int OPENING_SCORE = 750;

    private final View view;
    private final DieSource dice;
    private final int winningScore;
    private final int openingScore;
    private final Random random;

    public Game(Reader in, Writer out, DieSource dice) {
        this(in, out, dice, WINNING_SCORE, OPENING_SCORE, new Random(0));
    }

    public Game(Reader in, Writer out, DieSource dice, Random random) {
        this(in, out, dice, WINNING_SCORE, OPENING_SCORE, random);
    }

    public Game(Reader in, Writer out, DieSource dice, int winningScore, int openingScore) {
        this(in, out, dice, winningScore, openingScore, new Random(0));
    }

    public Game(Reader in, Writer out, DieSource dice, int winningScore, int openingScore, Random random) {
        this(new LineView(in, out, winningScore, openingScore), dice, winningScore, openingScore, random);
    }

    public Game(View view, DieSource dice, int winningScore, int openingScore) {
        this(view, dice, winningScore, openingScore, new Random(0));
    }

    public Game(View view, DieSource dice, int winningScore, int openingScore, Random random) {
        if (openingScore <= 0 || winningScore < openingScore) {
            throw new IllegalArgumentException("winning score must be at least the opening score");
        }
        this.view = view;
        this.dice = dice;
        this.winningScore = winningScore;
        this.openingScore = openingScore;
        this.random = random;
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
        Random random = new Random();
        DieSource dice = new RandomDice(random);
        if (options.plain || System.console() == null) {
            new Game(new InputStreamReader(System.in), new OutputStreamWriter(System.out), dice, random).play();
            return;
        }
        Terminal terminal = new Terminal();
        try {
            terminal.enter();
        } catch (RuntimeException failed) {
            terminal.restore();
            new Game(new InputStreamReader(System.in), new OutputStreamWriter(System.out), dice, random).play();
            return;
        }
        terminal.installHooks();
        try {
            boolean again = true;
            while (again) {
                TuiView view = new TuiView(terminal, options, WINNING_SCORE, OPENING_SCORE);
                again = new Game(view, dice, WINNING_SCORE, OPENING_SCORE, random).play();
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
        Bot.source = random;
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
            rook.personality = Bot.assign();
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
            int carried = 0;
            int diceToRoll = 5;
            if (pending != null) {
                if (view.chooseContinue(player, pending.banker, pending.diceLeft, pending.banked)) {
                    view.startContinued(player, pending.diceLeft);
                    diceToRoll = pending.diceLeft;
                    carried = pending.banked;
                } else {
                    view.startNewHand(player);
                }
                pending = null;
            } else {
                view.startTurn(player);
            }

            Turn turn = playTurn(player, diceToRoll, carried);
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
                return endGame(players, current, winningScore);
            }
            pending = new Pending(player.label(), turn.diceLeft, turn.banked);
            current = next(current, players.size());
        }
    }

    /** When a player crosses the threshold, give every other player one final turn.
     *  The leader is only declared once all others have had their shot.
     *  The final turn is forced: all scoring dice are held, rest are kept, the hand is banked. */
    private boolean endGame(List<Player> players, int leaderIdx, int winningScore) {
        Player leader = players.get(leaderIdx);

        // Store leader's score (leader doesn't play a final turn)
        int leaderScore = leader.score;

        // Let every other player one final turn
        int first = next(leaderIdx, players.size());
        int round = 0;
        int roundMax = players.size() - 1;
        for (int i = first; round < roundMax; i = next(i, players.size()), round++) {
            int other = i % players.size();
            if (other == leaderIdx) {
                other = next(other, players.size());
            }
            Player opponent = players.get(other);
            Turn turn = playTurnForced(opponent, 5, 0);
            if (turn.busted) {
                view.bust(opponent, turn.pointsLost);
                continue;
            }
            opponent.score += turn.banked;
            view.banked(opponent, turn.banked, 0, false);
            if (opponent.score > leaderScore) {
                leader = opponent;
                leaderScore = opponent.score;
            }
        }

        return view.win(leader, players);
    }

    /** Like {@link #playTurn} but forces the player to hold every scoring die and bank immediately.
     *  This is used for end-game final turns where no user input is available. */
    private Turn playTurnForced(Player player, int diceCount, int hand) {
        int[] kept = new int[0];
        while (true) {
            int[] roll = dice.roll(diceCount);
            view.showRoll(player, hand, roll, kept);
            if (diceCount == 2 && isNonScoringDouble(roll)) {
                view.doubleReRoll(player);
                continue;
            }
            if (!Scorer.canScore(roll)) {
                return Turn.bust(hand);
            }

            int[] held = Scorer.scoringIndexes(roll);
            Scorer.Scoring scoring = Scorer.score(LineView.facesAt(roll, held));
            if (!scoring.valid()) {
                throw new IllegalStateException("forced hold did not score: " + scoring.detail());
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

            // Force bank: bank the current total, leave remaining dice
            return Turn.bank(hand, left);
        }
    }

    private Turn playTurn(Player player, int diceCount, int hand) {
        int[] kept = new int[0];
        while (true) {
            int[] roll = dice.roll(diceCount);
            view.showRoll(player, hand, roll, kept);
            if (diceCount == 2 && isNonScoringDouble(roll)) {
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
