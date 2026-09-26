package greed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Frame shape, hold checks, and key mapping. Run with: java -cp out greed.TuiTest */
public final class TuiTest {
    private static int failed;
    private static final Pattern COLOR = Pattern.compile("\u001b\\[[0-9;]*m");

    public static void main(String[] args) throws Exception {
        widths();
        narrowAndWide();
        holdSelection();
        keys();
        colorAndPlain();
        resizeMessage();
        namesAndWindow();
        rulesOverlay();
        options();
        imports();
        if (failed > 0) {
            System.out.println(failed + " failed");
            System.exit(1);
        }
        System.out.println("All tui tests passed.");
    }

    private static void widths() {
        for (Snapshot snap : List.of(hold(), bankChoice(), rollOnly(), hot(), continued(), bust(), banked(), win())) {
            assertFrame(Frame.render(snap, 80, 24), 80, 24, snap.phase + " 80x24");
            assertFrame(Frame.render(snap, 60, 20), 60, 20, snap.phase + " 60x20");
        }
        assertFrame(Frame.render(hold(), 100, 40), 100, 40, "wide tall");
        String setup = Frame.render(countSnap(), 60, 20);
        assertFrame(setup, 60, 20, "setup");
        check(setup.contains("1 = 100"), "cheat sheet shows 1 = 100");
        check(setup.contains("0 = 10"), "zero means ten players");
    }

    private static void narrowAndWide() {
        String wide = Frame.render(hold(), 80, 24);
        check(wide.contains("●"), "wide dice use pips");
        check(wide.contains("■"), "wide scoreboard uses a bar");
        check(wide.contains("at risk"), "hand at risk is on the wide frame");
        check(wide.contains("Alice"), "current player is on the wide frame");
        check(wide.contains("1-5") && wide.contains("hold best"), "hold keys are on the wide frame");
        check(wide.contains("[bot]"), "bot tag is visible");
        check(wide.contains("HELD"), "a selected scoring die is marked HELD");
        check(wide.contains("SCORES"), "an unselected scoring die is marked SCORES");

        String narrow = Frame.render(hold(), 60, 20);
        check(!narrow.contains("■"), "narrow layout hides the bar");
        check(narrow.contains("at risk"), "hand at risk is on the narrow frame");
        check(narrow.contains("5"), "narrow dice show a digit");
        check(strip(narrow).contains("HELD"), "narrow frame still says HELD");

        String choice = Frame.render(bankChoice(), 80, 24);
        check(choice.contains("choose r or b"), "two-choice hint");
        check(choice.contains("r") && choice.contains("bank"), "roll and bank keys");

        String only = Frame.render(rollOnly(), 80, 24);
        check(only.contains("r/\u23CE"), "single move shows r and enter");
        check(only.contains("roll 3 dice"), "roll-only names the dice");
        check(only.contains("need 750"), "banking closed shows the opening score");

        String hot = Frame.render(hot(), 80, 24);
        check(hot.contains("ALL FIVE SCORED"), "hot dice banner");
        check(hot.contains("r/\u23CE"), "hot dice rolls on r or enter");

        String cont = Frame.render(continued(), 80, 24);
        check(cont.contains("Rook") && cont.contains("left"), "leftover dice name the banker");
        check(cont.contains("c") && cont.contains("new hand"), "continue keys");
        check(cont.contains("keeps the") && cont.contains("600"), "continued hand shows the banked points");

        String bust = Frame.render(bust(), 80, 24);
        check(bust.contains("BUST") && bust.contains("\u2717"), "bust word and mark");
        String bank = Frame.render(banked(), 80, 24);
        check(bank.contains("BANKED") && bank.contains("ON THE BOARD"), "bank banner");
    }

    private static void holdSelection() {
        ArrayDeque<Integer> keys = new ArrayDeque<>();
        for (int key : new int[] {'2', '\n', '2', '1', '\n'}) {
            keys.add(key);
        }
        TuiView view = TuiView.scripted(keys, 80, 24, true, true);
        Player alice = new Player("Alice", false);
        Player rook = new Player("Rook", true);
        view.scores(List.of(alice, rook));
        view.startTurn(alice);
        int[] held = view.chooseHold(alice, new int[] {1, 2, 3, 4, 6});
        check(Arrays.equals(held, new int[] {0}), "confirm holds the 1");
        boolean sawDetail = false;
        boolean sawMark = false;
        for (String frame : view.frames()) {
            if (frame.contains("2 does not score on its own.")) {
                sawDetail = true;
            }
            if (frame.contains("\u2717 no score")) {
                sawMark = true;
            }
        }
        check(sawDetail, "illegal hold shows the scorer detail");
        check(sawMark, "illegal die is tagged no score");
    }

    private static void keys() {
        Snapshot hold = hold();
        hold.selected = new boolean[] {false, false, false, false, false};
        boolean[] before = hold.selectionCopy();
        check("hint".equals(TuiView.press(hold, 'x')), "unknown hold key hints");
        check(hold.phase == Snapshot.Phase.HOLD, "unknown key stays in hold");
        check(Arrays.equals(before, hold.selected), "unknown key keeps the selection");

        check("toggle".equals(TuiView.press(hold, '1')), "1 toggles the first die");
        check(hold.selected[0], "first die is selected");
        check("toggle".equals(TuiView.press(hold, '1')), "1 toggles it off");
        check(!hold.selected[0], "first die is clear");

        check("best".equals(TuiView.press(hold, 'a')), "a holds the scoring dice");
        check(hold.selected[0] && hold.selected[1], "a selects the 1 and the 5");
        check(!hold.selected[2], "a leaves the 2");

        Snapshot bad = hold();
        bad.faces = new int[] {1, 2, 3, 4, 6};
        bad.selected = new boolean[] {false, true, false, false, false};
        check("reject".equals(TuiView.press(bad, '\r')), "enter refuses a lone 2");
        check(bad.confirmFailed, "failed confirm sticks");
        check(bad.detail.contains("2 does not score on its own."), "detail reused from Scorer");
        check(bad.phase == Snapshot.Phase.HOLD, "refused confirm stays on the dice");

        Snapshot rules = hold();
        rules.selected = new boolean[] {true, false, false, false, false};
        boolean[] kept = rules.selectionCopy();
        check("rules-open".equals(TuiView.press(rules, '?')), "question opens the rules");
        check(Arrays.equals(kept, rules.selected), "rules leave the selection");
        check("noop".equals(TuiView.press(rules, '1')), "keys under the rules do nothing");
        check(Arrays.equals(kept, rules.selected), "selection survives the overlay");
        check("rules-close".equals(TuiView.press(rules, 27)), "escape closes the rules");
        check(!rules.rulesOpen, "rules are closed");

        Snapshot quit = hold();
        quit.selected = new boolean[] {true, false, false, false, false};
        boolean[] quitBits = quit.selectionCopy();
        check("quit-ask".equals(TuiView.press(quit, 'q')), "q asks before quitting");
        check(Arrays.equals(quitBits, quit.selected), "quit ask keeps the selection");
        check("quit-no".equals(TuiView.press(quit, 'n')), "n stays in the game");
        check(!quit.confirmQuit, "confirm is cleared");

        Snapshot bank = bankChoice();
        check("noop".equals(TuiView.press(bank, '\r')), "enter does not choose roll or bank");
        check(bank.phase == Snapshot.Phase.BANK_OR_ROLL, "enter leaves the choice up");
        check(Frame.render(bank, 80, 24).contains("choose r or b"), "enter hint is on the frame");
        check("roll".equals(TuiView.press(bank, 'r')), "r rolls");
        check("bank".equals(TuiView.press(bankChoice(), 'b')), "b banks");

        Snapshot only = rollOnly();
        check("roll".equals(TuiView.press(only, '\r')), "enter rolls when it is the only move");
        check("hint".equals(TuiView.press(rollOnly(), 'b')), "b does not bank under the opening");
        check(Frame.render(rollOnly(), 60, 20).contains("r/"), "narrow hint keeps r");

        Snapshot cont = continued();
        check("noop".equals(TuiView.press(cont, '\n')), "enter does not continue or start fresh");
        check(cont.phase == Snapshot.Phase.CONTINUE, "continue phase stays");
        check("continue".equals(TuiView.press(continued(), 'c')), "c continues");
        check("new".equals(TuiView.press(continued(), 'N')), "n starts a new hand");

        check("roll".equals(TuiView.press(hot(), 'r')), "hot dice accept r");
        check("roll".equals(TuiView.press(hot(), '\r')), "hot dice accept enter");
        Snapshot hot = hot();
        check("hint".equals(TuiView.press(hot, 'b')), "hot dice refuse a bank");
        check(hot.logNewer.contains("cannot bank"), "hot bank explains itself");

        Snapshot count = countSnap();
        check("count".equals(TuiView.press(count, '0')), "0 seats 10");
        check(count.countChoice == 10, "zero maps to ten");
        check("quit-now".equals(TuiView.press(countSnap(), 'q')), "setup count quits immediately");

        Snapshot done = win();
        check("again".equals(TuiView.press(done, 'n')), "n starts a new game");
        check("quit-now".equals(TuiView.press(win(), 'q')), "results quit immediately");
    }

    private static void colorAndPlain() {
        Snapshot colored = hold();
        colored.color = true;
        String frame = Frame.render(colored, 80, 24);
        check(frame.contains("\u001b[1;33m"), "current player uses bold yellow");
        check(!frame.contains("\u001b[38;5"), "no 256-color codes");
        check(!frame.contains("\u001b[38;2"), "no RGB codes");

        Snapshot plain = hold();
        plain.color = false;
        String mono = Frame.render(plain, 80, 24);
        for (String code : new String[] {"[30m", "[31m", "[32m", "[33m", "[34m", "[35m", "[36m", "[37m", "[91m", "[92m"}) {
            check(!mono.contains(code), "no-color drops " + code);
        }
        check(mono.contains("\u25B6"), "current player still has the marker");
        check(mono.contains("HELD") && mono.contains("SCORES") && mono.contains("[bot]"),
                "held, scoring, and bot survive without color");
        String bust = Frame.render(bustPlain(), 80, 24);
        check(bust.contains("BUST") && bust.contains("\u2717"), "bust is a word and a mark");
        String bank = Frame.render(bankPlain(), 80, 24);
        check(bank.contains("BANKED"), "bank is a word");

        Snapshot ascii = hold();
        ascii.unicode = false;
        ascii.color = false;
        String text = Frame.render(ascii, 80, 24);
        assertFrame(text, 80, 24, "ascii");
        check(!text.contains("\u256D"), "ascii frame skips rounded corners");
        check(text.contains("Alice"), "ascii frame names the player");
    }

    private static void resizeMessage() {
        Snapshot snap = hold();
        snap.selected = new boolean[] {true, false, false, false, false};
        String small = Frame.render(snap, 52, 18);
        assertFrame(small, 52, 18, "too small");
        check(small.contains("Make the window at least 60\u00D720 (now 52\u00D718)"), "resize message");
        check(!strip(small).contains("HELD"), "too-small frame hides the table");
        String back = Frame.render(snap, 80, 24);
        check(snap.selected[0], "selection survives a resize");
        check(back.contains("HELD"), "growing the window draws the held die");
    }

    private static void namesAndWindow() {
        String name = "ABCDEFGHIJKLMNOPQRSTUVWX";
        Snapshot snap = hold();
        Player named = new Player(name, false);
        named.onBoard = true;
        named.score = 2_000;
        snap.players.set(0, named);
        String frame = Frame.render(snap, 80, 24);
        assertFrame(frame, 80, 24, "long name");
        check(frame.contains(name), "turn panel keeps the full name");
        check(frame.contains("\u2026"), "scoreboard truncates a long name");

        Snapshot victory = win();
        Player champion = new Player(name, false);
        champion.onBoard = true;
        champion.score = 10_000;
        victory.players.set(0, champion);
        victory.winner = champion;
        String standings = Frame.render(victory, 60, 20);
        assertFrame(standings, 60, 20, "win");
        check(standings.contains(name), "win screen keeps the full name");
        check(standings.contains("WINS"), "win banner");
        check(standings.contains("n new game"), "new game key");

        Snapshot crowd = new Snapshot();
        crowd.unicode = true;
        crowd.color = true;
        crowd.phase = Snapshot.Phase.HOLD;
        crowd.faces = new int[] {1, 5, 2, 3, 4};
        crowd.selected = new boolean[] {true, false, false, false, false};
        crowd.hand = 100;
        crowd.diceLeft = 5;
        crowd.players = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Player player = new Player(i == 7 ? "CURRENT" : "P" + i, false);
            player.onBoard = true;
            player.score = i * 10;
            crowd.players.add(player);
        }
        crowd.current = 7;
        String packed = Frame.render(crowd, 60, 20);
        assertFrame(packed, 60, 20, "ten players");
        check(packed.contains("CURRENT"), "window keeps the current player");
        boolean all = true;
        for (Player player : crowd.players) {
            if (!packed.contains(player.name)) {
                all = false;
            }
        }
        if (!all) {
            check(packed.contains("more"), "hidden players are counted");
        }
        assertFrame(Frame.render(crowd, 80, 24), 80, 24, "ten players wide");
    }

    private static void rulesOverlay() {
        Snapshot snap = hold();
        snap.rulesOpen = true;
        String frame = Frame.render(snap, 80, 24);
        assertFrame(frame, 80, 24, "rules");
        check(frame.contains("Three of a kind"), "rules include the set scores");
        check(frame.contains("1 = 100"), "rules include single scoring");
        check(frame.contains("esc"), "rules say how to close");
        assertFrame(Frame.render(snap, 60, 20), 60, 20, "rules narrow");
    }

    private static void options() {
        Options plain = Options.parse(new String[] {"--plain"}, false);
        check(plain.plain && plain.color && plain.animate, "plain keeps color flags for a line game");
        Options muted = Options.parse(new String[] {"--no-color"}, false);
        check(!muted.color && !muted.animate, "no-color disables animation");
        Options env = Options.parse(new String[0], true);
        check(!env.color && !env.animate, "NO_COLOR disables color and animation");
        Options still = Options.parse(new String[] {"--no-anim"}, false);
        check(still.color && !still.animate, "no-anim keeps color");
        boolean threw = false;
        try {
            Options.parse(new String[] {"--fancy"}, false);
        } catch (IllegalArgumentException e) {
            threw = e.getMessage().contains("--fancy");
        }
        check(threw, "unknown option is rejected");
    }

    private static void imports() throws Exception {
        Path dir = Path.of("src/main/java/greed");
        check(Files.isDirectory(dir), "sources are at src/main/java/greed");
        try (var files = Files.list(dir)) {
            for (Path file : files.toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("import ") && !trimmed.startsWith("import java.")) {
                        check(false, file.getFileName() + " " + trimmed);
                    }
                }
            }
        }
    }

    private static Snapshot hold() {
        Snapshot snap = new Snapshot();
        snap.unicode = true;
        snap.color = true;
        snap.phase = Snapshot.Phase.HOLD;
        Player alice = new Player("Alice", false);
        alice.onBoard = true;
        alice.score = 2_000;
        Player rook = new Player("Rook", true);
        snap.players = new ArrayList<>(List.of(alice, rook));
        snap.current = 0;
        snap.faces = new int[] {1, 5, 2, 3, 4};
        snap.selected = new boolean[] {true, false, false, false, false};
        snap.hand = 100;
        snap.diceLeft = 5;
        return snap;
    }

    private static Snapshot bankChoice() {
        Snapshot snap = hold();
        snap.phase = Snapshot.Phase.BANK_OR_ROLL;
        snap.kept = new int[] {1, 5};
        snap.diceLeft = 2;
        snap.hand = 1_150;
        return snap;
    }

    private static Snapshot rollOnly() {
        Snapshot snap = hold();
        snap.players.get(0).onBoard = false;
        snap.players.get(0).score = 0;
        snap.phase = Snapshot.Phase.ROLL_ONLY;
        snap.kept = new int[] {5};
        snap.diceLeft = 3;
        snap.hand = 400;
        return snap;
    }

    private static Snapshot hot() {
        Snapshot snap = hold();
        snap.phase = Snapshot.Phase.HOT;
        snap.diceLeft = 5;
        snap.hand = 1_100;
        snap.banner = "ALL FIVE SCORED";
        return snap;
    }

    private static Snapshot continued() {
        Snapshot snap = hold();
        snap.phase = Snapshot.Phase.CONTINUE;
        snap.banker = "Rook";
        snap.banked = 600;
        snap.diceLeft = 2;
        snap.hand = 0;
        snap.faces = new int[0];
        return snap;
    }

    private static Snapshot bust() {
        Snapshot snap = hold();
        snap.phase = Snapshot.Phase.BUST;
        snap.pointsLost = 1_150;
        snap.hand = 1_150;
        snap.banner = "BUST · Alice loses 1,150";
        snap.faces = new int[] {2, 3, 4, 6, 6};
        return snap;
    }

    private static Snapshot bustPlain() {
        Snapshot snap = bust();
        snap.color = false;
        return snap;
    }

    private static Snapshot banked() {
        Snapshot snap = hold();
        snap.phase = Snapshot.Phase.BANK;
        snap.opened = true;
        snap.banner = "BANKED 1,150 · Alice now has 3,500 · ON THE BOARD";
        return snap;
    }

    private static Snapshot bankPlain() {
        Snapshot snap = banked();
        snap.color = false;
        return snap;
    }

    private static Snapshot win() {
        Snapshot snap = hold();
        snap.phase = Snapshot.Phase.WIN;
        snap.winner = snap.players.get(0);
        snap.players.get(0).score = 10_000;
        return snap;
    }

    private static Snapshot countSnap() {
        Snapshot snap = new Snapshot();
        snap.unicode = true;
        snap.color = true;
        snap.phase = Snapshot.Phase.SETUP_COUNT;
        return snap;
    }

    private static void assertFrame(String frame, int cols, int rows, String label) {
        String[] lines = frame.split("\n", -1);
        check(lines.length == rows, label + " has " + lines.length + " rows");
        for (int i = 0; i < lines.length; i++) {
            int width = visible(lines[i]);
            check(width == cols, label + " line " + i + " width " + width);
        }
    }

    private static int visible(String line) {
        return strip(line).codePointCount(0, strip(line).length());
    }

    private static String strip(String line) {
        return COLOR.matcher(line).replaceAll("");
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            failed++;
            System.out.println("FAIL: " + label);
        }
    }
}
