package greed.view;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import greed.rules.Bot;
import greed.rules.Options;
import greed.rules.Player;

/** Frame shape, hold checks, and key mapping. Run with: java -cp out greed.view.TuiTest */
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
        layout();
        options();
        configuredRules();
        personalityTag();
        botCountScreen();
        countedBotsOnTheScreen();
        tokenBesideTheBotCount();
        scriptedDevShowsThePersonality();
        soloStillRejectsBot();
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
        check(setup.contains("How many players?"), "setup asks how many players");
        check(setup.contains("0 ten"), "zero means ten players");
        check(!setup.contains("0 = 10"), "setup hint does not repeat a bracket count");
        check(setup.contains("A set scores only when those dice come from one roll."),
                "setup says a set comes from one roll");
        check(!setup.contains("straight") && !setup.contains("full house"),
                "setup legend has no straight or full house");
        check(setup.contains("\u2588"), "setup banner uses the block font");
        String ask = strip(lineContaining(setup, "How many players?"));
        check(ask.indexOf("How many players?") > 8, "setup prompt is centered");
        String setupHint = lineContaining(setup, "0 ten");
        check(strip(setupHint).contains("1-9 players  0 ten  q quit"), "setup hint is one key row");
        check(setupHint.contains("\u001b[1m0") && setupHint.contains("\u001b[2m ten"),
                "setup hint styles the key and the label");
        Snapshot plainCount = countSnap();
        plainCount.unicode = false;
        String asciiSetup = Frame.render(plainCount, 80, 24);
        assertFrame(asciiSetup, 80, 24, "setup ascii");
        check(asciiSetup.contains("#") && !asciiSetup.contains("\u2588"),
                "plain setup banner uses hash marks");
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
        check(countOf(hot, "ALL FIVE SCORED") == 1, "hot dice banner is only on the summary");
        check(lineContaining(hot, "ALL FIVE SCORED").contains("\u001b[1;33m"), "hot banner is bold yellow");
        check(hot.contains("\u2605 ALL FIVE SCORED \u00B7 roll all 5 again \u2605"), "hot banner text");
        check(hot.contains("r/\u23CE"), "hot dice rolls on r or enter");
        check(!hot.contains("\u25CF"), "unrolled hot dice are blank");
        String wideDie = "\u256D\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u256E";
        check(countOf(strip(hot), wideDie) == 5, "hot dice stay large");
        check(hot.contains("\u2502       \u2502"), "hot dice show an empty face");

        String cont = Frame.render(continued(), 80, 24);
        check(cont.contains("CONTINUING ROOK'S 2 DICE"), "continue title names the banker");
        String carried = strip(lineContaining(cont, "Starts at 600"));
        check(carried.contains("Rook keeps 600"), "continue turn is one line");
        check(cont.contains("Continue Rook's 2 dice from 600, or start a new hand with 5"),
                "continue summary");
        check(cont.contains("continue (2)") && cont.contains("new hand (5)"), "continue hint");
        check(!cont.contains("Selected") && !cont.contains("keeps the") && !cont.contains("left"),
                "continue drops the floating copy");
        check(!cont.contains("rolls"), "continue does not announce a roll");
        check(countOf(strip(cont), wideDie) == 2, "continued dice stay large");
        check(!cont.contains("\u25CF"), "continued dice are blank");
        int holdRow = firstDieRow(Frame.render(hold(), 80, 24));
        check(holdRow == firstDieRow(hot) && holdRow == firstDieRow(cont),
                "hot and continue dice stay on the hold row");

        String bust = Frame.render(bust(), 80, 24);
        check(bust.contains("BUST \u00B7 Alice loses 1,150"), "bust summary names the loss");
        check(lineContaining(bust, "BUST").contains("\u001b[1;31m"), "bust summary is bold red");
        check(bust.contains("Hand lost") && !bust.contains("Hand at risk"), "bust says the hand was lost");
        String lost = lineContaining(bust, "Hand lost");
        check(lost.contains("\u001b[1;31m"), "lost amount is bold red");
        check(strip(lost).endsWith("1,150 \u2502"), "lost amount is on the right");
        check(bust.contains("\u25CF") && !bust.contains("\u2717"), "bust dice keep their pips");
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
        ArrayDeque<Integer> continueKeys = new ArrayDeque<>();
        continueKeys.add((int) 'c');
        TuiView continueView = TuiView.scripted(continueKeys, 80, 24, true, true);
        Player brandon = new Player("Brandon", false);
        continueView.scores(List.of(brandon, new Player("Rook", true)));
        check(continueView.chooseContinue(brandon, "Rook", 2, 600), "c continues from the script");
        check(!continueView.frames().isEmpty(), "continue draws a frame before the choice");
        boolean announcedRoll = false;
        for (String frame : continueView.frames()) {
            if (strip(frame).contains("rolls")) {
                announcedRoll = true;
            }
        }
        check(!announcedRoll, "the log does not roll before continue is chosen");

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
        check(bust.contains("BUST") && bust.contains("Hand lost") && !bust.contains("\u2717"),
                "bust is a word without a cross");
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
        check(standings.contains("\u2588"), "win banner uses the block font");
        check(!standings.contains("WINS"), "win banner is the block font");
        String winnerRow = lineContaining(standings, "\u2605");
        check(winnerRow.contains(name) && winnerRow.contains("\u001b[1m"), "winner row is bold");
        check(strip(winnerRow).indexOf('\u2605') > 4, "winner row is centered");
        check(strip(winnerRow).replace("\u2502", "").trim().codePointCount(0,
                strip(winnerRow).replace("\u2502", "").trim().length()) == 40,
                "standings are 40 columns");
        int winBanner = firstRow(standings, "\u2588");
        check(winBanner > 3, "win block is centered vertically");
        String winHint = lineContaining(standings, "new game");
        check(strip(winHint).contains("n new game  q quit"), "win hint separates the pairs");
        check(winHint.contains("\u001b[1mn") && winHint.contains("\u001b[2m new game"),
                "win hint styles the key and the label");
        Snapshot asciiWin = win();
        asciiWin.unicode = false;
        String hashWin = Frame.render(asciiWin, 80, 24);
        check(hashWin.contains("#") && !hashWin.contains("\u2588"), "plain win banner uses hash marks");

        Snapshot crowd = new Snapshot();
        crowd.unicode = true;
        crowd.color = true;
        crowd.phase = Snapshot.Phase.HOLD;
        crowd.faces = new int[] {1, 5, 2, 3, 6};
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
        check(!all && packed.contains("+5 more"), "ten players at 60x20 window the rest");
        check(packed.contains("\u001b[2mSCOREBOARD"), "windowed scoreboard keeps the dim title");
        check(lineContaining(packed, "+5 more").contains("\u001b[2m"), "the extra count is dim");
        assertFrame(Frame.render(crowd, 80, 24), 80, 24, "ten players wide");
    }

    private static void rulesOverlay() {
        Snapshot snap = hold();
        snap.rulesOpen = true;
        String frame = Frame.render(snap, 80, 24);
        assertFrame(frame, 80, 24, "rules");
        check(frame.contains("Three of a kind"), "rules include the set scores");
        check(frame.contains("1 = 100"), "rules include single scoring");
        check(frame.contains("come from one roll"), "rules say a set comes from one roll");
        check(frame.contains("Full house (three of a kind and a pair) = 1,250"),
                "rules include a full house");
        check(frame.contains("Straight (1 2 3 4 5) = 1,500"),
                "rules include a straight");
        check(frame.contains("esc"), "rules say how to close");
        assertFrame(Frame.render(snap, 60, 20), 60, 20, "rules narrow");
    }

    private static void layout() {
        String wide = Frame.render(hold(), 80, 24);
        String[] wideLines = wide.split("\n", -1);
        String header = strip(wideLines[0]);
        check(header.startsWith("\u256D\u2500 GREED ") && header.endsWith(" to 10,000 \u2500\u256E"),
                "header is a single dashed bar");
        check(header.contains("\u2500 GREED \u2500"), "one dash and a space around GREED");
        check(!strip(wideLines[1]).contains("\u251C"), "no rule under the header");
        check(strip(wideLines[1]).contains("SCOREBOARD") && strip(wideLines[1]).contains("ALICE'S TURN"),
                "scoreboard title is level with the turn title");
        check(wideLines[1].contains("\u001b[2mSCOREBOARD"), "scoreboard title is dim");
        check(countChar(wideLines[1], '\u2502') == 3, "the split runs through the status band");
        check(!strip(wide).contains("to go") && !strip(wide).contains("to get on"),
                "on-board turn omits the opening line");
        assertGaps(wide, "wide");
        assertGaps(Frame.render(hold(), 60, 20), "narrow");
        assertValueColumn(wide);

        String narrow = Frame.render(hold(), 60, 20);
        check(narrow.contains("SCORE") && !narrow.contains("HELD 100") && !narrow.contains("SCORESHELD"),
                "narrow tags are HELD and SCORE");
        check(wide.contains("HELD 100"), "wide tags keep the point value");

        String off = Frame.render(rollOnly(), 80, 24);
        String alice = lineContaining(off, "Alice");
        check(alice.contains("\u00B7\u00B7\u00B7\u00B7\u00B7") && alice.contains("needs 750"),
                "off the board uses dots and needs");
        check(!alice.contains(" 0"), "off the board drops the zero score");
        check(off.contains("350 more to get on"), "a partial hand says how many more");
        check(!off.contains("to go") && !off.contains("Needs 750"), "need line does not repeat the opening");
        Snapshot fresh = rollOnly();
        fresh.hand = 0;
        check(Frame.render(fresh, 80, 24).contains("Needs 750 to get on"),
                "a scoreless hand names the opening score");
        Snapshot enough = rollOnly();
        enough.hand = 800;
        String opened = Frame.render(enough, 80, 24);
        check(!opened.contains("to get on") && !opened.contains("to go"),
                "a hand that can open omits the need line");

        Snapshot set = hold();
        set.faces = new int[] {4, 1, 4, 4, 2};
        set.selected = new boolean[5];
        check(Frame.render(set, 80, 24).contains("Best hold: 4, 4, 4, 1 = 500"), "best hold lists the set first");
        Snapshot singles = hold();
        singles.faces = new int[] {5, 1, 5, 2, 3};
        singles.selected = new boolean[5];
        check(Frame.render(singles, 80, 24).contains("Best hold: 1, 5, 5 = 200"),
                "best hold lists 1s before 5s");

        Snapshot straight = hold();
        straight.faces = new int[] {1, 2, 3, 4, 5};
        straight.selected = new boolean[] {false, false, false, false, false};
        check("best".equals(TuiView.press(straight, 'a')), "a holds a straight");
        check(straight.selected[0] && straight.selected[1] && straight.selected[2]
                        && straight.selected[3] && straight.selected[4],
                "a selects every die of a straight");
        Snapshot house = hold();
        house.faces = new int[] {3, 3, 3, 2, 2};
        house.selected = new boolean[] {false, false, false, false, false};
        check("best".equals(TuiView.press(house, 'a')), "a holds a full house");
        check(house.selected[0] && house.selected[1] && house.selected[2]
                        && house.selected[3] && house.selected[4],
                "a selects every die of a full house");
        check(Frame.render(house, 80, 24).contains("Best hold: 3, 3, 3, 2, 2 = 1,250"),
                "full house best hold lists the set, then the pair");

        String hint = strip(lineContaining(wide, "1-5"));
        check(hint.contains("1-5 toggle  a hold best"), "hint pairs are separated by two spaces");
        check(hint.indexOf('?') > hint.indexOf("confirm"), "rules and quit sit on the right");

        Snapshot empty = hold();
        empty.selected = new boolean[] {false, false, false, false, false};
        check("reject".equals(TuiView.press(empty, '\r')), "empty enter is refused");
        String refused = Frame.render(empty, 80, 24);
        String summary = lineContaining(refused, "Hold at least one scoring die.");
        check(summary.contains("\u001b[1;31m"), "empty enter is red in the summary");
        check("toggle".equals(TuiView.press(empty, '1')), "a die toggle clears the refusal");
        check(!empty.confirmFailed, "confirm flag clears on toggle");
        check(!Frame.render(empty, 80, 24).contains("Hold at least one scoring die."),
                "the refusal leaves the frame");

        Snapshot carried = hold();
        carried.hand = 4_000;
        carried.faces = new int[] {3, 3, 3, 2, 4};
        carried.selected = new boolean[] {true, true, true, false, false};
        String added = strip(lineContaining(Frame.render(carried, 80, 24), "Hand at risk"));
        check(added.contains("4,300"), "hand at risk adds a valid selection to the carried total");
        carried.selected = new boolean[] {false, false, false, false, false};
        String onlyCarried = strip(lineContaining(Frame.render(carried, 80, 24), "Hand at risk"));
        check(onlyCarried.contains("4,000") && !onlyCarried.contains("4,300"),
                "an empty selection shows the carried total");
        carried.selected = new boolean[] {false, false, false, true, false};
        String illegal = strip(lineContaining(Frame.render(carried, 80, 24), "Hand at risk"));
        check(illegal.contains("4,000") && !illegal.contains("4,300"),
                "an illegal selection shows the carried total");
    }

    private static void assertGaps(String frame, String label) {
        String[] lines = frame.split("\n", -1);
        int die = -1;
        int selected = -1;
        for (int i = 0; i < lines.length; i++) {
            if (die < 0 && strip(lines[i]).contains("\u256D\u2500\u2500\u2500")) {
                die = i;
            }
            if (strip(lines[i]).contains("Selected:")) {
                selected = i;
            }
        }
        check(die > 0 && blankRow(lines[die - 1]), label + " leaves a blank row above the dice");
        check(selected > 0 && blankRow(lines[selected - 1]), label + " leaves a blank row above the summary");
        check(strip(lines[die]).startsWith("\u2502 "), label + " pads the dice one column");
    }

    private static void assertValueColumn(String frame) {
        String risk = strip(lineContaining(frame, "Hand at risk"));
        String dice = strip(lineContaining(frame, "Dice to roll"));
        int riskEnd = risk.lastIndexOf("200") + "200".length();
        int diceEnd = dice.lastIndexOf("5") + 1;
        check(risk.endsWith("200 \u2502") && riskEnd == diceEnd && riskEnd > 1,
                "hand at risk includes the selected 1 and shares the value column");
    }

    private static boolean blankRow(String raw) {
        String text = strip(raw).replace("\u2502", "").replace("|", "");
        return !strip(raw).contains("\u251C") && text.trim().isEmpty();
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int from = 0;
        while (from <= text.length()) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
        return count;
    }

    private static int firstDieRow(String frame) {
        return firstRow(frame, "\u256D\u2500\u2500\u2500");
    }

    private static int firstRow(String frame, String needle) {
        String[] lines = frame.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (strip(lines[i]).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static String lineContaining(String frame, String needle) {
        for (String line : frame.split("\n", -1)) {
            if (strip(line).contains(needle)) {
                return line.contains(needle) ? line : strip(line);
            }
        }
        return "";
    }

    private static int countChar(String text, char needle) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    private static void options() {
        Options plain = Options.parse(new String[] {"--plain"}, false);
        check(plain.plain && plain.color && plain.animate, "plain keeps color flags for a line game");
        check(!plain.dev && plain.opening == 750 && plain.winning == 10_000,
                "defaults stay hidden, 750, and 10,000");
        Options muted = Options.parse(new String[] {"--no-color"}, false);
        check(!muted.color && !muted.animate, "no-color disables animation");
        Options env = Options.parse(new String[0], true);
        check(!env.color && !env.animate, "NO_COLOR disables color and animation");
        Options still = Options.parse(new String[] {"--no-anim"}, false);
        check(still.color && !still.animate, "no-anim keeps color");
        Options dev = Options.parse(new String[] {"--dev"}, false);
        check(dev.dev && dev.color && dev.animate && !dev.plain, "dev leaves color and animation alone");
        Options low = Options.parse(new String[] {"--board", "300", "--winning", "2000"}, false);
        check(low.opening == 300 && low.winning == 2_000, "board then winning stores both");
        Options flipped = Options.parse(new String[] {"--winning", "2000", "--board", "300"}, false);
        check(flipped.opening == 300 && flipped.winning == 2_000, "winning then board stores both");
        rejects(new String[] {"--fancy"}, "Unknown option: --fancy");
        rejects(new String[] {"--board"}, "--board needs a number");
        rejects(new String[] {"--winning"}, "--winning needs a number");
        rejects(new String[] {"--board", "foo"}, "--board must be a whole number");
        rejects(new String[] {"--winning", "10_000"}, "--winning must be a whole number");
        rejects(new String[] {"--board", "--dev"}, "--board must be a whole number");
        rejects(new String[] {"--board", "0"}, "--board must be greater than 0");
        rejects(new String[] {"--board", "-3"}, "--board must be greater than 0");
        rejects(new String[] {"--winning", "-1"}, "--winning must be greater than 0");
        rejects(new String[] {"--board", "5000", "--winning", "1000"}, "--board must be less than --winning");
        rejects(new String[] {"--board", "1000", "--winning", "1000"}, "--board must be less than --winning");
        rejects(new String[] {"--winning", "500"}, "--board must be less than --winning");
        rejects(new String[] {"--board=300"}, "Unknown option: --board=300");
    }

    private static void rejects(String[] args, String message) {
        try {
            Options.parse(args, false);
            check(false, "accepted " + String.join(" ", args));
        } catch (IllegalArgumentException e) {
            check(message.equals(e.getMessage()), message + " (was " + e.getMessage() + ")");
        }
    }

    private static void configuredRules() {
        String rules = String.join("\n", LineView.rulesText(2_000, 300));
        check(rules.contains("First player to 2,000 points wins."), "rules name the winning score");
        check(rules.contains("at least 300."), "rules name the opening score");
        check(rules.contains("Choose how many computer players at setup, or type bot as a name to seat one."),
                "rules mention the bot count and the token");

        Snapshot overlay = hold();
        overlay.rulesOpen = true;
        overlay.winning = 2_000;
        overlay.opening = 300;
        String open = Frame.render(overlay, 80, 24);
        assertFrame(open, 80, 24, "custom rules");
        check(open.contains("First player to 2,000 points wins."), "overlay names 2,000");
        check(open.contains("at least 300."), "overlay names 300");

        Snapshot defaults = hold();
        defaults.rulesOpen = true;
        String stock = Frame.render(defaults, 80, 24);
        check(stock.contains("10,000") && stock.contains("750"), "the default overlay keeps 10,000 and 750");

        Snapshot count = countSnap();
        count.opening = 300;
        count.winning = 2_000;
        String setup = Frame.render(count, 80, 24);
        check(setup.contains("first bank must be 300+"), "cheat sheet uses the opening score");
        check(setup.contains("First to 2,000."), "count screen names the winning score");
    }

    private static void personalityTag() {
        Snapshot hidden = hold();
        hidden.players.get(1).personality = Bot.Personality.STEADY;
        hidden.players.get(1).revealPersonality = false;
        String plain = Frame.render(hidden, 80, 24);
        check(plain.contains("[bot]"), "a hidden computer keeps the bot tag");
        check(!plain.contains("(Steady)"), "a hidden computer omits the adjective");

        Snapshot shown = hold();
        shown.players.get(1).personality = Bot.Personality.STEADY;
        shown.players.get(1).revealPersonality = true;
        String dev = Frame.render(shown, 80, 24);
        check(dev.contains("(Steady)"), "dev shows the adjective");
        check(!dev.contains("[bot]"), "dev drops the bot tag");
    }

    private static void botCountScreen() {
        Snapshot three = botSnap(3);
        String frame = Frame.render(three, 80, 24);
        assertFrame(frame, 80, 24, "bot count");
        check(frame.contains("How many computer players?"), "bot screen asks");
        check(frame.contains("0 to 3"), "bot screen shows the range");
        String hint = strip(frame);
        check(hint.contains("0 none") && hint.contains("1-3 bots") && hint.contains("q quit"),
                "bot hint lists none, the range, and quit");
        check(!frame.contains("0 ten"), "bot screen does not say ten");

        String one = Frame.render(botSnap(1), 60, 20);
        assertFrame(one, 60, 20, "one bot max");
        String oneHint = strip(one);
        check(oneHint.contains("0 none") && oneHint.contains("1 bot") && oneHint.contains("q quit"),
                "one computer is labeled bot");
        check(!one.contains("0 ten"), "one-computer hint is not the player hint");

        Snapshot keys = botSnap(3);
        check("bots".equals(TuiView.press(keys, '0')) && keys.countChoice == 0, "0 means no computers");
        check("bots".equals(TuiView.press(keys, '2')) && keys.countChoice == 2, "2 seats two computers");
        check("hint".equals(TuiView.press(keys, '4')) && keys.logNewer.contains("Press 0-3"),
                "too many computers is refused");
        check("quit-now".equals(TuiView.press(botSnap(3), 'q')), "bot count quits immediately");

        Snapshot naming = new Snapshot();
        naming.unicode = true;
        naming.color = true;
        naming.phase = Snapshot.Phase.SETUP_NAMES;
        naming.nameBuf.append("bot");
        String named = Frame.render(naming, 80, 24);
        assertFrame(named, 80, 24, "bot token");
        check(named.contains("seats the computer"), "typing bot still seats the computer");
    }

    private static Snapshot botSnap(int max) {
        Snapshot snap = new Snapshot();
        snap.unicode = true;
        snap.color = true;
        snap.phase = Snapshot.Phase.SETUP_BOTS;
        snap.botMax = max;
        return snap;
    }

    private static void countedBotsOnTheScreen() {
        ArrayDeque<Integer> keys = new ArrayDeque<>();
        keys.add((int) '4');
        keys.add((int) '2');
        type(keys, "Ann");
        type(keys, "Bea");
        TuiView view = TuiView.scripted(keys, 80, 24, true, true);
        List<Player> players = takeSeats(view);
        view.goesFirst(players.get(0));
        view.scores(players);
        view.startTurn(players.get(0));
        String joined = String.join("\n", view.frames());
        check(countOf(joined, "[bot]") >= 2, "two computer rows");
        check(joined.contains("Ann goes first."), "the first human leads");
        check(players.get(0).name.equals("Ann") && !players.get(0).computer, "Ann is seated first");
        check(players.get(2).computer && players.get(3).computer, "the computers sit last");
    }

    private static void tokenBesideTheBotCount() {
        ArrayDeque<Integer> keys = new ArrayDeque<>();
        keys.add((int) '3');
        keys.add((int) '1');
        type(keys, "Ann");
        type(keys, "bot");
        List<Player> players = takeSeats(TuiView.scripted(keys, 80, 24, true, true));
        check(players.size() == 3, "tui fills three seats");
        check(!players.get(0).computer && players.get(0).name.equals("Ann"), "tui Ann is human");
        check(players.get(1).computer && players.get(1).name.equals("Rook") && players.get(1).personality != null,
                "tui token seats Rook");
        check(players.get(2).computer && players.get(2).name.equals("Rook 2") && players.get(2).personality != null,
                "tui counted computer is Rook 2");
        String frame = board(players);
        check(countOf(frame, "[bot]") == 2, "tui shows both computers");
        check(!frame.contains("(Steady)") && !frame.contains("(Cautious)") && !frame.contains("(Bold)"),
                "tui hides both personalities");
    }

    private static void scriptedDevShowsThePersonality() {
        Bot.forced = Bot.Personality.STEADY;
        try {
            List<Player> devPlayers = takeSeats(scriptedSeat(true));
            String dev = board(devPlayers);
            check(dev.contains("(Steady)"), "a dev script shows Steady");
            check(!dev.contains("[bot]"), "a dev script drops the bot tag");
            check(devPlayers.get(1).computer && devPlayers.get(1).personality == Bot.Personality.STEADY
                            && devPlayers.get(1).revealPersonality,
                    "dev seating stores Steady and reveals it");

            List<Player> plainPlayers = takeSeats(scriptedSeat(false));
            String plain = board(plainPlayers);
            check(plain.contains("[bot]"), "a normal script keeps the bot tag");
            check(!plain.contains("(Steady)"), "a normal script hides Steady");
            check(plainPlayers.get(1).personality == Bot.Personality.STEADY && !plainPlayers.get(1).revealPersonality,
                    "a normal script still stores Steady");
        } finally {
            Bot.forced = null;
        }
    }

    private static void soloStillRejectsBot() {
        ArrayDeque<Integer> keys = new ArrayDeque<>();
        keys.add((int) '1');
        type(keys, "bot");
        keys.add(127);
        keys.add(127);
        keys.add(127);
        type(keys, "Ada");
        TuiView view = TuiView.scripted(keys, 80, 24, true, true);
        check(view.readPlayerCount() == 1, "solo is one seat");
        view.soloAgainstComputer();
        Player ada = view.readSeat(1, new ArrayList<>(), true);
        check("Ada".equals(ada.name) && !ada.computer, "solo keeps the human");
        boolean rejected = false;
        boolean asked = false;
        for (String frame : view.frames()) {
            if (frame.contains("Type your name. Rook takes the other seat.")) {
                rejected = true;
            }
            if (frame.contains("How many computer players?")) {
                asked = true;
            }
        }
        check(rejected, "solo rejects bot");
        check(!asked, "solo skips the bot count");
    }

    private static TuiView scriptedSeat(boolean dev) {
        ArrayDeque<Integer> keys = new ArrayDeque<>();
        keys.add((int) '2');
        keys.add((int) '1');
        type(keys, "Ann");
        return TuiView.scripted(keys, 80, 24, true, true, 10_000, 750, dev);
    }

    private static List<Player> takeSeats(TuiView view) {
        int seats = view.readPlayerCount();
        int bots = seats == 1 ? 0 : view.readBotCount(seats);
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= seats - bots; i++) {
            players.add(view.readSeat(i, players, seats == 1));
        }
        int counted = bots + (seats == 1 ? 1 : 0);
        for (int i = 0; i < counted; i++) {
            Player rook = new Player(Bot.nameFor(players), true);
            rook.personality = Bot.assign();
            players.add(rook);
            view.seatComputer(rook);
        }
        return players;
    }

    private static String board(List<Player> players) {
        Snapshot snap = hold();
        snap.players = new ArrayList<>(players);
        snap.current = 0;
        return Frame.render(snap, 80, 24);
    }

    private static void type(ArrayDeque<Integer> keys, String text) {
        for (int i = 0; i < text.length(); i++) {
            keys.add((int) text.charAt(i));
        }
        keys.add((int) '\n');
    }

    private static void imports() throws Exception {
        Path dir = Path.of("src/main/java/greed");
        check(Files.isDirectory(dir), "sources are at src/main/java/greed");
        try (var files = Files.walk(dir)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("import ")) {
                        continue;
                    }
                    boolean jdk = trimmed.startsWith("import java.");
                    boolean local = trimmed.startsWith("import greed.");
                    if (!jdk && !local) {
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
        snap.faces = new int[] {1, 5, 2, 3, 6};
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
