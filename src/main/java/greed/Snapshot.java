package greed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Everything one frame needs. Input state lives here, so a resize does not clear it. */
final class Snapshot {
    enum Phase {
        SETUP_COUNT, SETUP_NAMES,
        HOLD, BANK_OR_ROLL, ROLL_ONLY, HOT, CONTINUE, DOUBLE_REROLL,
        BUST, BANK, WIN
    }

    int winning = Game.WINNING_SCORE;
    int opening = Game.OPENING_SCORE;
    boolean color = true;
    boolean unicode = true;
    /** Rules overlay draws the table behind it in dim. */
    boolean dim;

    List<Player> players = new ArrayList<>();
    int current;

    Phase phase = Phase.HOLD;

    int hand;
    int pointsLost;
    int[] faces = new int[0];
    boolean[] selected = new boolean[0];
    boolean confirmFailed;
    String detail = "";
    int[] kept = new int[0];
    int diceLeft = 5;
    String banker = "";
    int banked;
    boolean opened;
    String banner = "";

    String logOlder = "";
    String logNewer = "";

    boolean confirmQuit;
    boolean rulesOpen;
    boolean fastForward;

    boolean solo;
    boolean onlyHuman;
    int namingSeat = 1;
    final StringBuilder nameBuf = new StringBuilder();
    String fieldError = "";
    String resultName = "";
    boolean resultBot;
    int countChoice;

    Player winner;

    /** Count-up override. Index -1 uses the real score. */
    int shownScore = -1;
    int shownScoreIndex = -1;

    void log(String message) {
        logOlder = logNewer;
        logNewer = message == null ? "" : message;
    }

    Snapshot copy() {
        Snapshot copy = new Snapshot();
        copy.winning = winning;
        copy.opening = opening;
        copy.color = color;
        copy.unicode = unicode;
        copy.dim = dim;
        copy.players = players;
        copy.current = current;
        copy.phase = phase;
        copy.hand = hand;
        copy.pointsLost = pointsLost;
        copy.faces = faces;
        copy.selected = selected == null ? new boolean[0] : selected.clone();
        copy.confirmFailed = confirmFailed;
        copy.detail = detail;
        copy.kept = kept == null ? new int[0] : kept.clone();
        copy.diceLeft = diceLeft;
        copy.banker = banker;
        copy.banked = banked;
        copy.opened = opened;
        copy.banner = banner;
        copy.logOlder = logOlder;
        copy.logNewer = logNewer;
        copy.confirmQuit = confirmQuit;
        copy.rulesOpen = rulesOpen;
        copy.fastForward = fastForward;
        copy.solo = solo;
        copy.onlyHuman = onlyHuman;
        copy.namingSeat = namingSeat;
        copy.nameBuf.append(nameBuf);
        copy.fieldError = fieldError;
        copy.resultName = resultName;
        copy.resultBot = resultBot;
        copy.countChoice = countChoice;
        copy.winner = winner;
        copy.shownScore = shownScore;
        copy.shownScoreIndex = shownScoreIndex;
        return copy;
    }

    int[] selectedIndexes() {
        int count = 0;
        for (boolean bit : selected) {
            if (bit) {
                count++;
            }
        }
        int[] indexes = new int[count];
        int write = 0;
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                indexes[write++] = i;
            }
        }
        return indexes;
    }

    void selectOnly(int[] indexes) {
        selected = new boolean[faces.length];
        for (int index : indexes) {
            if (index >= 0 && index < selected.length) {
                selected[index] = true;
            }
        }
    }

    void clearSelection() {
        selected = new boolean[faces.length];
        confirmFailed = false;
        detail = "";
    }

    int scoreOf(int index) {
        if (index == shownScoreIndex && shownScore >= 0) {
            return shownScore;
        }
        return players.get(index).score;
    }

    boolean soleHuman() {
        int humans = 0;
        for (Player player : players) {
            if (!player.computer) {
                humans++;
            }
        }
        return humans == 1;
    }

    static boolean isBotToken(String name) {
        return name.equalsIgnoreCase("bot") || name.equalsIgnoreCase("computer");
    }

    static boolean taken(List<Player> seated, String name) {
        for (Player player : seated) {
            if (player.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Selection after a scripted edit, for tests that clone the bits. */
    boolean[] selectionCopy() {
        return selected == null ? new boolean[0] : Arrays.copyOf(selected, selected.length);
    }
}
