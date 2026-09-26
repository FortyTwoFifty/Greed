package greed;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Full-screen table. One key does one thing. The frame is built, then written once. */
final class TuiView implements View {
    private final Terminal terminal;
    private final Options options;
    private final Snapshot snap = new Snapshot();
    private final List<String> frames = new ArrayList<>();
    private final ArrayDeque<Integer> keys;
    private final Random animation = new Random();
    private int cols = 80;
    private int rows = 24;

    TuiView(Terminal terminal, Options options, int winning, int opening) {
        this.terminal = terminal;
        this.options = options;
        this.keys = null;
        snap.winning = winning;
        snap.opening = opening;
        snap.color = options.color;
        snap.unicode = terminal.unicode();
        if (terminal.rows() > 0 && terminal.cols() > 0) {
            rows = terminal.rows();
            cols = terminal.cols();
        }
    }

    /** Fixed size, scripted keys, no terminal and no sleeps. */
    static TuiView scripted(ArrayDeque<Integer> keys, int cols, int rows, boolean color, boolean unicode) {
        TuiView view = new TuiView(keys, cols, rows, color, unicode);
        return view;
    }

    private TuiView(ArrayDeque<Integer> keys, int cols, int rows, boolean color, boolean unicode) {
        this.terminal = null;
        this.options = Options.parse(color ? new String[0] : new String[] {"--no-color"}, !color);
        this.keys = keys;
        this.cols = cols;
        this.rows = rows;
        snap.winning = Game.WINNING_SCORE;
        snap.opening = Game.OPENING_SCORE;
        snap.color = color;
        snap.unicode = unicode;
    }

    List<String> frames() {
        return frames;
    }

    @Override
    public void intro() {
        snap.phase = Snapshot.Phase.SETUP_COUNT;
        redraw();
    }

    @Override
    public int readPlayerCount() {
        snap.phase = Snapshot.Phase.SETUP_COUNT;
        redraw();
        while (true) {
            String effect = press(snap, readKey());
            if ("count".equals(effect)) {
                return snap.countChoice;
            }
            if ("quit-now".equals(effect) || "quit-yes".equals(effect)) {
                throw new Quit();
            }
            redraw();
        }
    }

    @Override
    public void soloAgainstComputer() {
        snap.solo = true;
        snap.log("One human sits with the computer. Rook takes the other seat.");
    }

    @Override
    public Player readSeat(int seat, List<Player> seated, boolean onlyHuman) {
        snap.players = seated;
        snap.namingSeat = seat;
        snap.onlyHuman = onlyHuman;
        snap.nameBuf.setLength(0);
        snap.fieldError = "";
        snap.phase = Snapshot.Phase.SETUP_NAMES;
        redraw();
        while (true) {
            String effect = press(snap, readKey());
            if ("commit".equals(effect)) {
                Player player = new Player(snap.resultName, snap.resultBot);
                if (player.computer) {
                    snap.log(player.name + " sits down for the computer.");
                }
                return player;
            }
            if ("quit-now".equals(effect) || "quit-yes".equals(effect)) {
                throw new Quit();
            }
            redraw();
        }
    }

    @Override
    public void seatComputer(Player player) {
        snap.log(player.name + " sits down for the computer.");
    }

    @Override
    public void goesFirst(Player player) {
        snap.log(player.name + " goes first.");
    }

    @Override
    public void scores(List<Player> players) {
        snap.players = players;
        if (tablePhase(snap.phase)) {
            redraw();
        }
    }

    @Override
    public void startTurn(Player player) {
        focus(player);
        snap.phase = Snapshot.Phase.HOLD;
        snap.faces = new int[0];
        snap.clearSelection();
        snap.diceLeft = 5;
        redraw();
    }

    @Override
    public void startContinued(Player player, int diceLeft) {
        focus(player);
        snap.diceLeft = diceLeft;
        snap.log(player.name + " rolls the " + LineView.diceWord(diceLeft) + " left unscored.");
        redraw();
    }

    @Override
    public void startNewHand(Player player) {
        focus(player);
        snap.diceLeft = 5;
        snap.log(player.name + " starts a new hand.");
        redraw();
    }

    @Override
    public void showRoll(Player player, int hand, int[] faces, int[] kept) {
        focus(player);
        snap.hand = hand;
        snap.kept = kept.clone();
        snap.faces = faces.clone();
        snap.clearSelection();
        snap.diceLeft = faces.length;
        snap.phase = Snapshot.Phase.HOLD;
        if (options.animate && keys == null) {
            animate(faces);
        }
        redraw();
        if (player.computer) {
            botBeat();
        }
    }

    @Override
    public int[] chooseHold(Player player, int[] faces) {
        focus(player);
        snap.phase = Snapshot.Phase.HOLD;
        snap.faces = faces.clone();
        snap.clearSelection();
        snap.diceLeft = faces.length;
        redraw();
        while (true) {
            String effect = press(snap, readKey());
            if ("confirm".equals(effect)) {
                return snap.selectedIndexes();
            }
            if ("quit-yes".equals(effect) || "quit-now".equals(effect)) {
                throw new Quit();
            }
            redraw();
        }
    }

    @Override
    public int[] botHold(Player player, int[] faces) {
        int[] every = Scorer.scoringIndexes(faces);
        Scorer.Scoring preview = Scorer.score(LineView.facesAt(faces, every));
        if (!preview.valid()) {
            throw new IllegalStateException("bot hold did not score: " + preview.detail());
        }
        focus(player);
        snap.phase = Snapshot.Phase.HOLD;
        snap.faces = faces.clone();
        snap.selectOnly(every);
        snap.diceLeft = faces.length;
        snap.log(player.name + " holds every scoring die.");
        snap.log("Held " + preview.detail() + ".");
        redraw();
        botBeat();
        return every;
    }

    @Override
    public void hotDice(Player player, int hand) {
        focus(player);
        snap.phase = Snapshot.Phase.HOT;
        snap.hand = hand;
        snap.diceLeft = 5;
        snap.banner = "ALL FIVE SCORED";
        redraw();
        if (player.computer) {
            snap.log(player.name + " rolls all 5.");
            redraw();
            botBeat();
            return;
        }
        await("roll", "You cannot bank on this roll.");
    }

    @Override
    public void mustRoll(Player player, int hand, int left, int[] kept) {
        arm(player, hand, left, kept, Snapshot.Phase.ROLL_ONLY);
        snap.log(openingBlock(snap));
        redraw();
        if (player.computer) {
            snap.log(player.name + " rolls the remaining " + LineView.diceWord(left) + ".");
            redraw();
            botBeat();
            return;
        }
        await("roll", null);
    }

    @Override
    public boolean chooseBank(Player player, int hand, int left, int[] kept) {
        arm(player, hand, left, kept, Snapshot.Phase.BANK_OR_ROLL);
        if (player.computer) {
            Bot.Choice choice = Bot.bank(hand, left, player.onBoard, player.score, snap.opening, snap.winning);
            snap.log(player.name + " " + choice.reason());
            redraw();
            botBeat();
            return choice.yes();
        }
        return awaitChoice("bank", "roll");
    }

    @Override
    public boolean chooseContinue(Player player, String banker, int diceLeft, int banked) {
        focus(player);
        snap.phase = Snapshot.Phase.CONTINUE;
        snap.banker = banker;
        snap.diceLeft = diceLeft;
        snap.banked = banked;
        snap.faces = new int[0];
        snap.log(player.name + ", " + banker + " left " + LineView.diceWord(diceLeft) + " unscored.");
        redraw();
        if (player.computer) {
            Bot.Choice choice = Bot.cont(diceLeft);
            snap.log(player.name + " " + choice.reason());
            redraw();
            botBeat();
            return choice.yes();
        }
        return awaitChoice("continue", "new");
    }

    @Override
    public void bust(Player player, int pointsLost) {
        focus(player);
        snap.phase = Snapshot.Phase.BUST;
        snap.pointsLost = pointsLost;
        snap.banner = pointsLost > 0
                ? "BUST · " + player.name + " loses " + Scorer.format(pointsLost)
                : "BUST · no points this turn";
        snap.log(pointsLost > 0
                ? "Bust. " + player.name + " loses " + Scorer.format(pointsLost) + " unbanked points."
                : "Bust. " + player.name + " scores nothing this turn.");
        redraw();
        if (keys != null) {
            return;
        }
        if (player.computer) {
            botBeat(1_200);
            return;
        }
        while (true) {
            String effect = press(snap, readKey());
            if ("quit-yes".equals(effect) || "quit-now".equals(effect)) {
                throw new Quit();
            }
            if (!snap.confirmQuit && !snap.rulesOpen) {
                return;
            }
            redraw();
        }
    }

    @Override
    public void banked(Player player, int amount, int scoreBefore, boolean opened) {
        focus(player);
        snap.phase = Snapshot.Phase.BANK;
        snap.opened = opened;
        snap.banner = "BANKED " + Scorer.format(amount) + " · " + player.name
                + " now has " + Scorer.format(player.score)
                + (opened ? " · ON THE BOARD" : "");
        snap.log(snap.banner);
        if (options.animate && keys == null && amount > 0) {
            int steps = 6;
            for (int step = 1; step <= steps; step++) {
                snap.shownScoreIndex = snap.current;
                snap.shownScore = scoreBefore + (player.score - scoreBefore) * step / steps;
                redraw();
                if (keyDuring(50)) {
                    break;
                }
            }
            snap.shownScore = -1;
            snap.shownScoreIndex = -1;
        }
        redraw();
        if (player.computer) {
            botBeat();
        }
    }

    @Override
    public boolean win(Player winner, List<Player> players) {
        snap.players = players;
        snap.winner = winner;
        snap.phase = Snapshot.Phase.WIN;
        snap.confirmQuit = false;
        redraw();
        while (true) {
            String effect = press(snap, readKey());
            if ("again".equals(effect)) {
                return true;
            }
            if ("quit-now".equals(effect) || "quit-yes".equals(effect)) {
                throw new Quit();
            }
            redraw();
        }
    }

    @Override
    public void goodbye() {
        if (terminal != null) {
            terminal.goodbye();
        }
    }

    @Override
    public void flush() {
        // The frame is flushed on every write.
    }

    /**
     * One key against the current phase. Returns an effect name and does not
     * change phase. An unknown key logs a hint and leaves the selection alone.
     */
    static String press(Snapshot snap, int key) {
        if (snap.confirmQuit) {
            if (key == 'y' || key == 'Y') {
                return "quit-yes";
            }
            if (key == 'n' || key == 'N') {
                snap.confirmQuit = false;
                return "quit-no";
            }
            return "noop";
        }
        if (snap.rulesOpen) {
            if (key == '?' || key == 27) {
                snap.rulesOpen = false;
                return "rules-close";
            }
            return "noop";
        }
        if (key == '?') {
            snap.rulesOpen = true;
            return "rules-open";
        }
        if (snap.phase == Snapshot.Phase.SETUP_NAMES) {
            if ((key == 'q' || key == 'Q') && snap.nameBuf.isEmpty()) {
                return "quit-now";
            }
            return pressName(snap, key);
        }
        if (key == 'q' || key == 'Q') {
            if (snap.phase == Snapshot.Phase.SETUP_COUNT || snap.phase == Snapshot.Phase.WIN) {
                return "quit-now";
            }
            snap.confirmQuit = true;
            return "quit-ask";
        }
        return switch (snap.phase) {
            case HOLD -> pressHold(snap, key);
            case BANK_OR_ROLL -> pressTwo(snap, key, 'r', "roll", 'b', "bank", "choose r or b");
            case ROLL_ONLY -> pressOnly(snap, key, true);
            case HOT -> pressOnly(snap, key, false);
            case CONTINUE -> pressTwo(snap, key, 'c', "continue", 'n', "new", "choose c or n");
            case BUST, BANK -> "ack";
            case WIN -> pressWin(key);
            case SETUP_COUNT -> pressCount(snap, key);
            case SETUP_NAMES -> pressName(snap, key);
        };
    }

    private static String pressHold(Snapshot snap, int key) {
        if (key >= '1' && key <= '5') {
            int index = key - '1';
            if (index >= snap.faces.length) {
                snap.log("Use 1-" + snap.faces.length + ", a, or Enter");
                return "hint";
            }
            snap.selected[index] = !snap.selected[index];
            releaseHoldError(snap);
            return "toggle";
        }
        if (key == 'a' || key == 'A') {
            snap.selectOnly(Scorer.scoringIndexes(snap.faces));
            releaseHoldError(snap);
            return "best";
        }
        if (key == '\r' || key == '\n' || key == ' ') {
            int[] indexes = snap.selectedIndexes();
            Scorer.Scoring scoring = Scorer.score(LineView.facesAt(snap.faces, indexes));
            if (!scoring.valid()) {
                snap.confirmFailed = true;
                snap.detail = scoring.detail();
                snap.log(scoring.detail());
                return "reject";
            }
            snap.confirmFailed = false;
            snap.detail = "";
            snap.log("Held " + scoring.detail() + ".");
            return "confirm";
        }
        snap.log("Use 1-" + Math.max(1, snap.faces.length) + ", a, or Enter");
        return "hint";
    }

    /** The red summary owns a refusal. A later toggle takes that sentence off the log. */
    private static void releaseHoldError(Snapshot snap) {
        if (snap.detail != null && snap.detail.equals(snap.logNewer)) {
            snap.logNewer = "";
        }
        snap.confirmFailed = false;
        snap.detail = "";
    }

    private static String pressTwo(Snapshot snap, int key, char yesKey, String yes, char noKey, String no,
                                    String enterHint) {
        if (key == yesKey || key == Character.toUpperCase(yesKey)) {
            return yes;
        }
        if (key == noKey || key == Character.toUpperCase(noKey)) {
            return no;
        }
        if (key == '\r' || key == '\n') {
            snap.log(enterHint);
            return "noop";
        }
        snap.log(enterHint);
        return "hint";
    }

    /** Enter rolls when it is the only legal move. */
    private static String pressOnly(Snapshot snap, int key, boolean opening) {
        if (key == 'r' || key == 'R' || key == '\r' || key == '\n') {
            return "roll";
        }
        if (key == 'b' || key == 'B') {
            if (opening) {
                snap.log(openingBlock(snap));
            } else {
                snap.log("You cannot bank on this roll.");
            }
            return "hint";
        }
        snap.log("press r to roll");
        return "hint";
    }

    private static String pressWin(int key) {
        if (key == 'n' || key == 'N') {
            return "again";
        }
        return "hint";
    }

    private static String pressCount(Snapshot snap, int key) {
        if (key >= '1' && key <= '9') {
            snap.countChoice = key - '0';
            return "count";
        }
        if (key == '0') {
            snap.countChoice = 10;
            return "count";
        }
        snap.log("Press 1-9, or 0 for 10");
        return "hint";
    }

    private static String pressName(Snapshot snap, int key) {
        if (key == 127 || key == 8) {
            if (!snap.nameBuf.isEmpty()) {
                snap.nameBuf.deleteCharAt(snap.nameBuf.length() - 1);
            }
            refreshName(snap);
            return "edit";
        }
        if (key == '\r' || key == '\n') {
            return commitName(snap);
        }
        if (key >= 32 && key != 127) {
            if (snap.nameBuf.length() >= 24) {
                snap.fieldError = "Use 24 characters or fewer.";
                return "reject-name";
            }
            snap.nameBuf.appendCodePoint(key);
            refreshName(snap);
            return "edit";
        }
        snap.log("Type a name, then Enter");
        return "hint";
    }

    private static void refreshName(Snapshot snap) {
        String typed = snap.nameBuf.toString().trim();
        if (typed.length() > 24) {
            snap.fieldError = "Use 24 characters or fewer.";
        } else if (!typed.isEmpty() && !Snapshot.isBotToken(typed) && Snapshot.taken(snap.players, typed)) {
            snap.fieldError = typed + " is already in the game.";
        } else {
            snap.fieldError = "";
        }
    }

    private static String commitName(Snapshot snap) {
        String name = snap.nameBuf.toString().trim();
        if (Snapshot.isBotToken(name)) {
            if (snap.onlyHuman) {
                snap.fieldError = "Type your name. Rook takes the other seat.";
                return "reject-name";
            }
            snap.resultBot = true;
            snap.resultName = Bot.nameFor(snap.players);
            snap.fieldError = "";
            return "commit";
        }
        if (name.isEmpty()) {
            name = "Player " + snap.namingSeat;
        }
        if (name.length() > 24) {
            snap.fieldError = "Use 24 characters or fewer.";
            return "reject-name";
        }
        if (Snapshot.taken(snap.players, name)) {
            snap.fieldError = name + " is already in the game.";
            return "reject-name";
        }
        snap.resultBot = false;
        snap.resultName = name;
        snap.fieldError = "";
        return "commit";
    }

    private static String openingBlock(Snapshot snap) {
        Player player = currentPlayer(snap);
        String who;
        if (player.computer) {
            who = player.name + " needs ";
        } else if (snap.soleHuman()) {
            who = "You need ";
        } else {
            who = player.name + " needs ";
        }
        return who + Scorer.format(snap.opening) + " in this hand to get on the board. Banking "
                + Scorer.format(snap.hand) + " is not allowed yet.";
    }

    private static Player currentPlayer(Snapshot snap) {
        if (snap.players == null || snap.players.isEmpty()) {
            return new Player("You", false);
        }
        int index = Math.max(0, Math.min(snap.current, snap.players.size() - 1));
        return snap.players.get(index);
    }

    private void await(String accept, String bankLog) {
        while (true) {
            int key = readKey();
            if (bankLog != null && (key == 'b' || key == 'B') && !snap.rulesOpen && !snap.confirmQuit) {
                snap.log(bankLog);
                redraw();
                continue;
            }
            String effect = press(snap, key);
            if (accept.equals(effect)) {
                return;
            }
            if ("quit-yes".equals(effect) || "quit-now".equals(effect)) {
                throw new Quit();
            }
            redraw();
        }
    }

    private boolean awaitChoice(String yes, String no) {
        while (true) {
            String effect = press(snap, readKey());
            if (yes.equals(effect)) {
                return true;
            }
            if (no.equals(effect)) {
                return false;
            }
            if ("quit-yes".equals(effect) || "quit-now".equals(effect)) {
                throw new Quit();
            }
            redraw();
        }
    }

    private void arm(Player player, int hand, int left, int[] kept, Snapshot.Phase phase) {
        focus(player);
        snap.phase = phase;
        snap.hand = hand;
        snap.diceLeft = left;
        snap.kept = kept.clone();
        redraw();
    }

    private void focus(Player player) {
        if (snap.players != null) {
            for (int i = 0; i < snap.players.size(); i++) {
                if (snap.players.get(i) == player) {
                    snap.current = i;
                    break;
                }
            }
        }
        if (!player.computer) {
            snap.fastForward = false;
        }
    }

    private void animate(int[] faces) {
        int[] shown = faces.clone();
        for (int frame = 0; frame < 6; frame++) {
            for (int i = 0; i < shown.length; i++) {
                shown[i] = animation.nextInt(6) + 1;
            }
            snap.faces = shown.clone();
            redraw();
            if (keyDuring(40)) {
                break;
            }
        }
        snap.faces = faces.clone();
    }

    private void botBeat() {
        botBeat(600);
    }

    private void botBeat(int ms) {
        if (keys != null || snap.fastForward || terminal == null) {
            return;
        }
        int key = waitKey(ms);
        if (key < 0) {
            return;
        }
        if (key == 'q' || key == 'Q') {
            snap.confirmQuit = true;
            redraw();
            while (true) {
                String effect = press(snap, readKey());
                if ("quit-yes".equals(effect)) {
                    throw new Quit();
                }
                if ("quit-no".equals(effect)) {
                    snap.fastForward = true;
                    return;
                }
                redraw();
            }
        }
        snap.fastForward = true;
    }

    private int readKey() {
        if (keys != null) {
            if (keys.isEmpty()) {
                throw new IllegalStateException("no scripted key");
            }
            return keys.remove();
        }
        while (true) {
            if (terminal.pollSize()) {
                rows = Math.max(1, terminal.rows());
                cols = Math.max(1, terminal.cols());
                redraw();
            }
            int code = terminal.readCodePoint();
            if (code == -2) {
                continue;
            }
            if (code < 0) {
                continue;
            }
            if (cols < 60 || rows < 20) {
                if (code == 'q' || code == 'Q') {
                    throw new Quit();
                }
                continue;
            }
            return code;
        }
    }

    /** @return a key, or -1 when the wait ends with none */
    private int waitKey(int ms) {
        terminal.setWaitTenths(0);
        try {
            long end = System.currentTimeMillis() + ms;
            while (System.currentTimeMillis() < end) {
                int code = terminal.readCodePoint();
                if (code >= 0) {
                    return code;
                }
                sleep(15);
            }
            return -1;
        } finally {
            terminal.setWaitTenths(5);
        }
    }

    private boolean keyDuring(int ms) {
        return waitKey(ms) >= 0;
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Quit();
        }
    }

    private static boolean tablePhase(Snapshot.Phase phase) {
        return switch (phase) {
            case HOLD, BANK_OR_ROLL, ROLL_ONLY, HOT, CONTINUE, BUST, BANK -> true;
            default -> false;
        };
    }

    private void redraw() {
        String frame = Frame.render(snap, cols, rows);
        if (keys != null) {
            frames.add(frame);
        }
        if (terminal != null) {
            terminal.write(Frame.place(frame));
        }
    }
}
