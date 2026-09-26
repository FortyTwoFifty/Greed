package greed.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import greed.rules.Bot;
import greed.rules.Player;
import greed.rules.Scorer;

/** Pure frame renderer. Every line's visible width is the column count. */
final class Frame {
    private Frame() {}

    static String render(Snapshot snap, int cols, int rows) {
        return String.join("\n", lines(snap, cols, rows));
    }

    /** Cursor-home the finished frame. No newline after the last row, so the screen does not scroll. */
    static String place(String rendered) {
        String[] rows = rendered.split("\n", -1);
        StringBuilder out = new StringBuilder();
        out.append("\u001b[H");
        for (int i = 0; i < rows.length; i++) {
            out.append("\u001b[").append(i + 1).append(";1H");
            out.append(rows[i]);
            out.append("\u001b[K");
        }
        return out.toString();
    }

    static List<String> lines(Snapshot snap, int cols, int rows) {
        if (cols < 1) {
            cols = 1;
        }
        if (rows < 1) {
            rows = 1;
        }
        if (cols < 60 || rows < 20) {
            return tooSmall(snap, cols, rows);
        }
        if (snap.rulesOpen) {
            Snapshot under = snap.copy();
            under.rulesOpen = false;
            under.dim = true;
            return stampRules(lines(under, cols, rows), snap, cols, rows);
        }
        return switch (snap.phase) {
            case SETUP_COUNT -> setup(snap, cols, rows, false);
            case SETUP_NAMES -> setup(snap, cols, rows, true);
            case WIN -> winner(snap, cols, rows);
            default -> table(snap, cols, rows);
        };
    }

    private static List<String> tooSmall(Snapshot snap, int cols, int rows) {
        Glyphs g = Glyphs.of(snap.unicode);
        String message = "Make the window at least 60" + g.times + "20 (now "
                + cols + g.times + rows + ")";
        List<String> wrapped = wrap(message, cols);
        List<String> frame = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            frame.add(Paint.spaces(cols));
        }
        int start = Math.max(0, (rows - wrapped.size()) / 2);
        for (int i = 0; i < wrapped.size() && start + i < rows; i++) {
            String text = wrapped.get(i);
            int pad = Math.max(0, (cols - Paint.width(text)) / 2);
            Paint.Row row = row(cols, snap);
            row.add(Paint.spaces(pad) + text, "");
            frame.set(start + i, row.finish());
        }
        return frame;
    }

    private static List<String> setup(Snapshot snap, int cols, int rows, boolean names) {
        Glyphs g = Glyphs.of(snap.unicode);
        int inner = cols - 2;
        List<String> body = new ArrayList<>();
        if (!names) {
            int text = Math.max(1, inner - 2);
            char cell = snap.unicode ? '\u2588' : '#';
            List<String> block = new ArrayList<>();
            for (int r = 0; r < 5; r++) {
                block.add(bannerRow("GREED", r, cell));
            }
            block.add("");
            block.add("First to " + Scorer.format(snap.winning) + ". Hold 1s, 5s, and three of a kind.");
            block.add("");
            block.addAll(cheat(snap, g));
            block.add("");
            block.add("How many players?");
            for (String line : block) {
                body.add(centerIn(line, text));
            }
            return present(snap, g, cols, rows, body, inner, null, setupHint(snap, text), true, null);
        } else {
            body.add("Name each seat. Enter moves on. Empty uses Player N.");
            if (snap.solo) {
                body.add("One human sits with the computer. Rook takes the other seat.");
            }
            body.add("");
            int seat = 1;
            for (Player player : snap.players) {
                body.add(seat + "  " + player.label() + botTag(player));
                seat++;
            }
            String typed = snap.nameBuf.toString();
            body.add(snap.namingSeat + "  " + typed);
            if (snap.solo) {
                body.add(rookName(snap) + " [bot]");
            }
            if (Snapshot.isBotToken(typed.trim())) {
                body.add(g.arrow + " seats the computer");
            }
            if (snap.fieldError != null && !snap.fieldError.isEmpty()) {
                body.add(snap.fieldError);
            }
        }
        return present(snap, g, cols, rows, body, inner,
                "type a name, Enter seats them, q quits", null, false, null);
    }

    private static String rookName(Snapshot snap) {
        List<Player> virtual = new ArrayList<>(snap.players);
        String typed = snap.nameBuf.toString().trim();
        if (!typed.isEmpty() && !Snapshot.isBotToken(typed)) {
            virtual.add(new Player(typed, false));
        }
        return Bot.nameFor(virtual);
    }

    private static List<String> winner(Snapshot snap, int cols, int rows) {
        Glyphs g = Glyphs.of(snap.unicode);
        int inner = cols - 2;
        int text = Math.max(1, inner - 2);
        List<Player> players = seat(snap);
        Player winner = snap.winner == null ? players.get(0) : snap.winner;
        List<Player> order = new ArrayList<>(players);
        order.sort((left, right) -> Integer.compare(right.score, left.score));
        char cell = snap.unicode ? '\u2588' : '#';
        String heading = winner.label().toUpperCase(Locale.ROOT) + " WINS";
        List<String> body = new ArrayList<>();
        for (int r = 0; r < 5; r++) {
            body.add(centerIn(bannerRow(heading, r, cell), text));
        }
        body.add(centerIn("", text));
        String winnerLine = null;
        for (int i = 0; i < order.size(); i++) {
            Player player = order.get(i);
            boolean champion = player == winner;
            String row = standingRow(g, i + 1, player, champion);
            String centered = centerIn(row, text);
            if (champion) {
                winnerLine = centered;
            }
            body.add(centered);
        }
        List<Seg> hint = new ArrayList<>();
        addPair(hint, "n", "new game", true, true);
        addPair(hint, "q", "quit", true, false);
        return present(snap, g, cols, rows, body, inner, null, spread(snap, text, hint, List.of(), text - segWidth(hint)),
                true, winnerLine);
    }

    /** About 40 columns: star, rank, full name, score. */
    private static String standingRow(Glyphs g, int rank, Player player, boolean winner) {
        String score = Scorer.format(player.score);
        String bot = botTag(player);
        String mark = winner ? g.star + " " : "  ";
        String label = mark + rank + "  " + player.label() + bot;
        int width = 40;
        int room = width - Paint.width(score) - 1;
        if (Paint.width(label) > room) {
            label = ellipsize(label, Math.max(1, room), g.ellipsis);
        }
        int gap = width - Paint.width(label) - Paint.width(score);
        return label + Paint.spaces(Math.max(1, gap)) + score;
    }

    /**
     * Header, body, hint, bottom edge.
     * {@code hintBody} is already {@code inner - 2} wide. When it is null, {@code hintPlain} is used.
     */
    private static List<String> present(Snapshot snap, Glyphs g, int cols, int rows, List<String> body, int inner,
                                         String hintPlain, String hintBody, boolean center, String boldLine) {
        int text = Math.max(1, inner - 2);
        List<String> content = shrink(new ArrayList<>(body), rows - 3);
        int spare = rows - 3 - content.size();
        int before = center ? Math.max(0, spare / 2) : 0;
        int after = Math.max(0, spare - before);
        List<String> frame = new ArrayList<>();
        frame.add(header(snap, g, cols));
        for (int i = 0; i < before; i++) {
            frame.add(box(Paint.spaces(inner), snap, g));
        }
        for (String line : content) {
            String style = line.equals(snap.fieldError) ? Paint.RED : line.equals(boldLine) ? Paint.BOLD : "";
            frame.add(box(inset(plain(line, text, snap, style)), snap, g));
        }
        for (int i = 0; i < after; i++) {
            frame.add(box(Paint.spaces(inner), snap, g));
        }
        String hint = hintBody == null ? plain(hintPlain == null ? "" : hintPlain, text, snap, Paint.DIM) : hintBody;
        frame.add(box(inset(hint), snap, g));
        frame.add(bottom(snap, g, cols));
        return frame;
    }

    private static String setupHint(Snapshot snap, int width) {
        List<Seg> pairs = new ArrayList<>();
        addPair(pairs, "1-9", "players", true, true);
        addPair(pairs, "0", "ten", true, false);
        addPair(pairs, "q", "quit", true, false);
        return spread(snap, width, pairs, List.of(), Math.max(0, width - segWidth(pairs)));
    }

    private static String centerIn(String text, int width) {
        int size = Paint.width(text);
        if (size >= width) {
            return text;
        }
        int pad = (width - size) / 2;
        return Paint.spaces(pad) + text + Paint.spaces(width - pad - size);
    }

    private static String bannerRow(String word, int row, char cell) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (i > 0) {
                line.append(' ');
            }
            String[] glyph = letter(word.charAt(i));
            line.append(glyph[row].replace('#', cell));
        }
        return line.toString();
    }

    /** Five rows, four columns, {@code #} marks ink. */
    private static String[] letter(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'A' -> glyph(" ## ", "#  #", "####", "#  #", "#  #");
            case 'B' -> glyph("### ", "#  #", "### ", "#  #", "### ");
            case 'C' -> glyph(" ###", "#   ", "#   ", "#   ", " ###");
            case 'D' -> glyph("### ", "#  #", "#  #", "#  #", "### ");
            case 'E' -> glyph("####", "#   ", "### ", "#   ", "####");
            case 'F' -> glyph("####", "#   ", "### ", "#   ", "#   ");
            case 'G' -> glyph(" ###", "#   ", "# ##", "#  #", " ###");
            case 'H' -> glyph("#  #", "#  #", "####", "#  #", "#  #");
            case 'I' -> glyph("### ", " #  ", " #  ", " #  ", "### ");
            case 'J' -> glyph(" ###", "  # ", "  # ", "# # ", " #  ");
            case 'K' -> glyph("#  #", "# # ", "##  ", "# # ", "#  #");
            case 'L' -> glyph("#   ", "#   ", "#   ", "#   ", "####");
            case 'M' -> glyph("#  #", "## #", "# ##", "#  #", "#  #");
            case 'N' -> glyph("#  #", "## #", "# ##", "#  #", "#  #");
            case 'O' -> glyph(" ## ", "#  #", "#  #", "#  #", " ## ");
            case 'P' -> glyph("### ", "#  #", "### ", "#   ", "#   ");
            case 'Q' -> glyph(" ## ", "#  #", "#  #", "# # ", " # #");
            case 'R' -> glyph("### ", "#  #", "### ", "# # ", "#  #");
            case 'S' -> glyph(" ###", "#   ", " ## ", "   #", "### ");
            case 'T' -> glyph("####", " #  ", " #  ", " #  ", " #  ");
            case 'U' -> glyph("#  #", "#  #", "#  #", "#  #", " ## ");
            case 'V' -> glyph("#  #", "#  #", "#  #", "#  #", " ## ");
            case 'W' -> glyph("#  #", "#  #", "# ##", "## #", "#  #");
            case 'X' -> glyph("#  #", "#  #", " ## ", "#  #", "#  #");
            case 'Y' -> glyph("#  #", "#  #", " ## ", " #  ", " #  ");
            case 'Z' -> glyph("####", "  # ", " #  ", "#   ", "####");
            case '0' -> glyph(" ## ", "#  #", "#  #", "#  #", " ## ");
            case '1' -> glyph(" #  ", "##  ", " #  ", " #  ", "### ");
            case '2' -> glyph("### ", "   #", " ## ", "#   ", "####");
            case '3' -> glyph("### ", "   #", " ## ", "   #", "### ");
            case '4' -> glyph("#  #", "#  #", "####", "   #", "   #");
            case '5' -> glyph("####", "#   ", "### ", "   #", "### ");
            case '6' -> glyph(" ###", "#   ", "### ", "#  #", " ## ");
            case '7' -> glyph("####", "   #", "  # ", " #  ", " #  ");
            case '8' -> glyph(" ## ", "#  #", " ## ", "#  #", " ## ");
            case '9' -> glyph(" ## ", "#  #", " ###", "   #", "### ");
            case ' ' -> glyph("    ", "    ", "    ", "    ", "    ");
            default -> glyph("####", "#  #", "#  #", "#  #", "####");
        };
    }

    private static String[] glyph(String a, String b, String c, String d, String e) {
        return new String[] {fit4(a), fit4(b), fit4(c), fit4(d), fit4(e)};
    }

    private static String fit4(String text) {
        if (text.length() >= 4) {
            return text.substring(0, 4);
        }
        return text + "    ".substring(text.length());
    }

    private static List<String> shrink(List<String> content, int room) {
        List<String> lines = new ArrayList<>(content);
        while (lines.size() > room) {
            int blank = lines.indexOf("");
            if (blank < 0) {
                break;
            }
            lines.remove(blank);
        }
        while (lines.size() > room && lines.size() > 1) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static List<String> table(Snapshot snap, int cols, int rows) {
        Glyphs g = Glyphs.of(snap.unicode);
        boolean narrow = cols < 80;
        int inner = cols - 2;
        boolean dividers = true;
        boolean merge = false;
        boolean window = false;
        boolean showTitle = true;
        int logRows = 2;
        List<String> status = List.of();
        int faceRows = narrow ? 3 : 5;
        int tagRows = 2;
        int aboveDice = 1;
        int aboveSummary = 1;
        for (int pass = 0; pass < 8; pass++) {
            faceRows = narrow ? 3 : 5;
            tagRows = merge ? 1 : 2;
            int dividerRows = dividers ? 3 : 0;
            status = buildStatus(snap, g, inner, narrow, window, showTitle);
            int tableBody = rows - status.size() - logRows - 3 - dividerRows;
            int spare = tableBody - (faceRows + tagRows + 1);
            if (spare >= 2) {
                int extra = spare - 2;
                aboveDice = 1 + (extra + 1) / 2;
                aboveSummary = 1 + extra / 2;
                break;
            }
            if (dividers) {
                dividers = false;
                continue;
            }
            if (!merge) {
                merge = true;
                continue;
            }
            if (!window) {
                window = true;
                continue;
            }
            if (logRows > 1) {
                logRows = 1;
                continue;
            }
            if (spare >= 1) {
                aboveDice = 1;
                aboveSummary = 0;
                break;
            }
            throw new IllegalStateException("status " + status.size() + " leaves " + spare
                    + " spare rows at " + cols + "x" + rows);
        }
        List<String> dice = diceBlock(snap, g, inner, narrow, faceRows, tagRows, aboveDice, aboveSummary);
        List<String> logs = logLines(snap, g, inner, logRows);
        String hintLine = hint(snap, g, inner);

        List<String> frame = new ArrayList<>();
        frame.add(header(snap, g, cols));
        for (String line : status) {
            frame.add(box(line, snap, g));
        }
        if (dividers) {
            frame.add(rule(snap, g, cols));
        }
        for (String line : dice) {
            frame.add(box(line, snap, g));
        }
        if (dividers) {
            frame.add(rule(snap, g, cols));
        }
        for (String line : logs) {
            frame.add(box(line, snap, g));
        }
        if (dividers) {
            frame.add(rule(snap, g, cols));
        }
        frame.add(box(hintLine, snap, g));
        frame.add(bottom(snap, g, cols));
        if (frame.size() != rows) {
            throw new IllegalStateException("frame " + frame.size() + " rows, wanted " + rows
                    + " at " + cols + "x" + rows);
        }
        return frame;
    }

    private static List<String> buildStatus(Snapshot snap, Glyphs g, int inner, boolean narrow,
                                             boolean window, boolean showTitle) {
        List<Player> players = seat(snap);
        int current = Math.max(0, Math.min(snap.current, players.size() - 1));
        List<Integer> shown = visible(players.size(), current, window);
        int hidden = players.size() - shown.size();
        int scoreCol = 5;
        for (int index : shown) {
            if (players.get(index).onBoard) {
                scoreCol = Math.max(scoreCol, Paint.width(Scorer.format(snap.scoreOf(index))));
            }
        }
        if (narrow) {
            int content = Math.max(1, inner - 2);
            List<String> lines = new ArrayList<>();
            if (showTitle) {
                lines.add(inset(plain("SCOREBOARD", content, snap, Paint.DIM)));
            }
            for (int index : shown) {
                lines.add(inset(playerLine(snap, g, players.get(index), index, index == current,
                        content, true, scoreCol)));
            }
            if (hidden > 0) {
                lines.add(inset(plain("+" + hidden + " more", content, snap, Paint.DIM)));
            }
            for (String line : turnLines(snap, g, players.get(current), content)) {
                lines.add(inset(line));
            }
            return lines;
        }
        int leftW = (inner - 1) / 2;
        int rightW = inner - 1 - leftW;
        int leftC = Math.max(1, leftW - 2);
        int rightC = Math.max(1, rightW - 2);
        List<String> left = new ArrayList<>();
        if (showTitle) {
            left.add(plain("SCOREBOARD", leftC, snap, Paint.DIM));
        }
        for (int index : shown) {
            left.add(playerLine(snap, g, players.get(index), index, index == current, leftC, false, scoreCol));
        }
        if (hidden > 0) {
            left.add(plain("+" + hidden + " more", leftC, snap, Paint.DIM));
        }
        List<String> right = turnLines(snap, g, players.get(current), rightC);
        int band = Math.max(left.size(), right.size());
        String mid = paint(g.v, Paint.DIM, snap);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < band; i++) {
            String l = i < left.size() ? left.get(i) : Paint.spaces(leftC);
            String r = i < right.size() ? right.get(i) : Paint.spaces(rightC);
            lines.add(" " + l + " " + mid + " " + r + " ");
        }
        return lines;
    }

    private static List<Integer> visible(int count, int current, boolean window) {
        List<Integer> ids = new ArrayList<>();
        if (!window || count <= 5) {
            for (int i = 0; i < count; i++) {
                ids.add(i);
            }
            return ids;
        }
        int start = current - 2;
        if (start < 0) {
            start = 0;
        }
        if (start + 5 > count) {
            start = count - 5;
        }
        for (int i = start; i < start + 5; i++) {
            ids.add(i);
        }
        return ids;
    }

    private static String playerLine(Snapshot snap, Glyphs g, Player player, int index, boolean current,
                                      int width, boolean narrow, int scoreCol) {
        Paint.Row row = row(width, snap);
        boolean off = !player.onBoard;
        boolean dimRow = off && !current;
        String marker = (current ? g.marker : " ") + " ";
        String bot = botTag(player);
        String tail = off
                ? "  needs " + Scorer.format(snap.opening)
                : (narrow ? "" : " " + bar(snap, g, snap.scoreOf(index)));
        int tailW = Paint.width(tail);
        int fixed = Paint.width(marker) + Paint.width(bot) + scoreCol + tailW;
        int nameRoom = Math.max(1, width - fixed - 1);
        String name = ellipsize(player.label(), nameRoom, g.ellipsis);
        String nameStyle = dimRow ? Paint.DIM : (current ? Paint.YELLOW : "");
        row.add(marker, nameStyle);
        row.add(name, nameStyle);
        row.add(bot, dimRow ? Paint.DIM : Paint.MAGENTA);
        int pad = width - Paint.width(marker) - Paint.width(name) - Paint.width(bot) - scoreCol - tailW;
        if (pad > 0) {
            row.add(Paint.spaces(pad), "");
        }
        if (off) {
            row.add(g.dot.repeat(scoreCol), Paint.DIM);
            row.add(tail, Paint.DIM);
        } else {
            String scoreText = Scorer.format(snap.scoreOf(index));
            int lead = scoreCol - Paint.width(scoreText);
            if (lead > 0) {
                row.add(Paint.spaces(lead), "");
            }
            row.add(scoreText, "");
            if (!tail.isEmpty()) {
                row.add(tail, "");
            }
        }
        return row.finish();
    }

    /** Personality already marks a computer, so the tag stays only for an unmarked one. */
    private static String botTag(Player player) {
        if (!player.computer || player.personality != null) {
            return "";
        }
        return " [bot]";
    }

    private static String bar(Snapshot snap, Glyphs g, int score) {
        int filled = snap.winning <= 0 ? 0 : (int) Math.round(10.0 * score / snap.winning);
        filled = Math.max(0, Math.min(10, filled));
        return g.barOn.repeat(filled) + g.barOff.repeat(10 - filled);
    }

    private static List<String> turnLines(Snapshot snap, Glyphs g, Player player, int width) {
        List<String> lines = new ArrayList<>();
        String name = player.label().toUpperCase(Locale.ROOT);
        if (snap.phase == Snapshot.Phase.CONTINUE) {
            String banker = snap.banker == null ? "" : snap.banker;
            for (String part : wrap("CONTINUING " + banker.toUpperCase(Locale.ROOT) + "'S "
                    + snap.diceLeft + " DICE", width)) {
                lines.add(plain(part, width, snap, Paint.YELLOW));
            }
            lines.add(plain("Starts at " + Scorer.format(snap.banked) + " " + g.dot + " " + banker
                    + " keeps " + Scorer.format(snap.banked),
                    width, snap, ""));
            return lines;
        }
        if (snap.phase == Snapshot.Phase.BUST) {
            for (String part : wrap(name + "'S TURN", width)) {
                lines.add(plain(part, width, snap, Paint.YELLOW));
            }
            String lost = Scorer.format(snap.pointsLost);
            lines.add(valueLine(snap, width, "Hand lost", "", "", lost, Paint.width(lost), Paint.RED));
            return lines;
        }
        for (String part : wrap(name + "'S TURN", width)) {
            lines.add(plain(part, width, snap, Paint.YELLOW));
        }
        String risk = Scorer.format(shownRisk(snap));
        String dice = Integer.toString(snap.diceLeft);
        int field = Math.max(Paint.width(risk), Paint.width(dice));
        lines.add(valueLine(snap, width, "Hand at risk", "", "", risk, field, Paint.YELLOW));
        lines.add(valueLine(snap, width, "Dice to roll", "", "", dice, field, Paint.BOLD));
        if (!player.onBoard) {
            if (snap.hand <= 0) {
                lines.add(plain("Needs " + Scorer.format(snap.opening) + " to get on", width, snap, Paint.DIM));
            } else if (snap.hand < snap.opening) {
                int more = snap.opening - snap.hand;
                lines.add(plain(Scorer.format(more) + " more to get on", width, snap, Paint.DIM));
            }
        }
        return lines;
    }

    /** Carried total, plus a hold that already scores. An empty or illegal selection adds nothing. */
    private static int shownRisk(Snapshot snap) {
        int total = Math.max(0, snap.hand);
        if (snap.phase != Snapshot.Phase.HOLD || snap.faces == null || snap.selected == null) {
            return total;
        }
        int[] indexes = snap.selectedIndexes();
        if (indexes.length == 0) {
            return total;
        }
        for (int index : indexes) {
            if (index < 0 || index >= snap.faces.length) {
                return total;
            }
        }
        Scorer.Scoring scoring = Scorer.score(LineView.facesAt(snap.faces, indexes));
        if (!scoring.valid()) {
            return total;
        }
        return total + scoring.points();
    }

    /** Label on the left, value in a fixed field on the right. Optional dim note sits between them. */
    private static String valueLine(Snapshot snap, int width, String label, String middle, String middleStyle,
                                     String value, int field, String valueStyle) {
        Paint.Row row = row(width, snap);
        int labelW = Paint.width(label);
        int middleW = middle == null || middle.isEmpty() ? 0 : 1 + Paint.width(middle);
        int gap = width - labelW - middleW - field;
        if (gap < 1 && middleW > 0) {
            middle = "";
            middleW = 0;
            gap = width - labelW - field;
        }
        row.add(label, "");
        if (middleW > 0) {
            row.add(" ", "");
            row.add(middle, middleStyle);
        }
        if (gap > 0) {
            row.add(Paint.spaces(gap), "");
        }
        int lead = field - Paint.width(value);
        if (lead > 0) {
            row.add(Paint.spaces(lead), "");
        }
        row.add(value, valueStyle);
        return row.finish();
    }

    private static List<String> diceBlock(Snapshot snap, Glyphs g, int inner, boolean narrow,
                                           int faceRows, int tagRows, int aboveDice, int aboveSummary) {
        boolean strip = snap.phase != Snapshot.Phase.HOLD && snap.phase != Snapshot.Phase.BUST;
        List<String> faces;
        if (strip) {
            faces = stripFaces(snap, g, inner, faceRows);
        } else if (snap.faces.length == 0) {
            faces = new ArrayList<>();
            for (int i = 0; i < faceRows; i++) {
                faces.add(Paint.spaces(inner));
            }
        } else {
            faces = bigFaces(snap, g, inner, narrow, faceRows);
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < aboveDice; i++) {
            lines.add(Paint.spaces(inner));
        }
        lines.addAll(faces);
        lines.addAll(tagLines(snap, g, inner, narrow, tagRows, strip));
        for (int i = 0; i < aboveSummary; i++) {
            lines.add(Paint.spaces(inner));
        }
        lines.add(summary(snap, g, inner));
        return lines;
    }

    private static List<String> bigFaces(Snapshot snap, Glyphs g, int inner, boolean narrow, int faceRows) {
        int count = snap.faces.length;
        int dieW = narrow ? 5 : 9;
        Kind[] kinds = kinds(snap, g);
        String[][] art = new String[count][];
        String[] styles = new String[count];
        for (int i = 0; i < count; i++) {
            art[i] = dieArt(snap.faces[i], kinds[i], narrow, g);
            styles[i] = style(kinds[i]);
        }
        return insetEach(placeDice(art, styles, contentWidth(inner), dieW, faceRows, snap));
    }

    private static List<String> stripFaces(Snapshot snap, Glyphs g, int inner, int faceRows) {
        boolean narrow = faceRows < 5;
        if (snap.phase == Snapshot.Phase.CONTINUE || snap.phase == Snapshot.Phase.HOT) {
            int n = snap.phase == Snapshot.Phase.HOT ? 5 : Math.max(1, snap.diceLeft);
            String[][] art = new String[n][];
            String[] styles = new String[n];
            for (int i = 0; i < n; i++) {
                art[i] = dieArt(1, Kind.UNKNOWN, narrow, g);
                styles[i] = style(Kind.UNKNOWN);
            }
            int dieW = narrow ? 5 : 9;
            return insetEach(placeDice(art, styles, contentWidth(inner), dieW, faceRows, snap));
        }
        List<Integer> faces = new ArrayList<>();
        List<Kind> kinds = new ArrayList<>();
        for (int face : snap.kept) {
            faces.add(face);
            kinds.add(Kind.KEPT);
        }
        for (int i = 0; i < snap.diceLeft; i++) {
            faces.add(-1);
            kinds.add(Kind.UNKNOWN);
        }
        if (faces.isEmpty()) {
            faces.add(-1);
            kinds.add(Kind.UNKNOWN);
        }
        String[][] art = new String[faces.size()][];
        String[] styles = new String[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            int face = faces.get(i);
            art[i] = smallDie(face, kinds.get(i), g);
            styles[i] = style(kinds.get(i));
        }
        int dieH = 3;
        int blank = Math.max(0, (faceRows - dieH) / 2);
        int content = contentWidth(inner);
        List<String> placed = insetEach(placeDice(art, styles, content, 5, dieH, snap));
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < blank && lines.size() < faceRows; i++) {
            lines.add(Paint.spaces(inner));
        }
        lines.addAll(placed);
        while (lines.size() < faceRows) {
            lines.add(Paint.spaces(inner));
        }
        if (lines.size() > faceRows) {
            return new ArrayList<>(lines.subList(0, faceRows));
        }
        return lines;
    }

    private static int contentWidth(int inner) {
        return Math.max(1, inner - 2);
    }

    private static List<String> insetEach(List<String> lines) {
        List<String> padded = new ArrayList<>();
        for (String line : lines) {
            padded.add(inset(line));
        }
        return padded;
    }

    private static List<String> placeDice(String[][] art, String[] styles, int inner, int dieW,
                                           int rows, Snapshot snap) {
        int count = art.length;
        int gap = 2;
        int total = count * dieW + Math.max(0, count - 1) * gap;
        int pad = Math.max(0, (inner - total) / 2);
        List<String> lines = new ArrayList<>();
        int height = art.length == 0 ? rows : art[0].length;
        for (int r = 0; r < height && r < rows; r++) {
            Paint.Row row = row(inner, snap);
            row.add(Paint.spaces(pad), "");
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    row.add(Paint.spaces(gap), "");
                }
                row.add(art[i][r], styles[i]);
            }
            lines.add(row.finish());
        }
        while (lines.size() < rows) {
            lines.add(Paint.spaces(inner));
        }
        return lines;
    }

    private static List<String> tagLines(Snapshot snap, Glyphs g, int inner, boolean narrow,
                                          int tagRows, boolean strip) {
        List<String> lines = new ArrayList<>();
        if (snap.phase == Snapshot.Phase.BUST) {
            while (lines.size() < tagRows) {
                lines.add(Paint.spaces(inner));
            }
            return lines;
        }
        if (strip) {
            int content = contentWidth(inner);
            String label = stripLabel(snap, g);
            lines.add(inset(plain(label, content, snap, Paint.DIM)));
            while (lines.size() < tagRows) {
                lines.add(Paint.spaces(inner));
            }
            return lines;
        }
        if (snap.faces.length == 0) {
            while (lines.size() < tagRows) {
                lines.add(Paint.spaces(inner));
            }
            return lines;
        }
        Kind[] kinds = kinds(snap, g);
        String[] tags = new String[kinds.length];
        String[] keys = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            tags[i] = tagText(snap, g, i, kinds[i], narrow);
            boolean mark = kinds[i] == Kind.HELD;
            keys[i] = "[" + (i + 1) + "]" + (mark ? " " + g.check : "");
        }
        int content = contentWidth(inner);
        if (tagRows >= 2) {
            lines.add(inset(alignUnderDice(keys, snap, g, content, narrow, "")));
            lines.add(inset(alignUnderDice(tags, snap, g, content, narrow, "")));
        } else {
            lines.add(inset(alignUnderDice(tags, snap, g, content, narrow, "")));
        }
        while (lines.size() < tagRows) {
            lines.add(Paint.spaces(inner));
        }
        return lines;
    }

    private static String stripLabel(Snapshot snap, Glyphs g) {
        if (snap.phase == Snapshot.Phase.CONTINUE || snap.phase == Snapshot.Phase.HOT) {
            return "";
        }
        if (snap.phase == Snapshot.Phase.BANK) {
            return snap.banner == null ? "" : snap.banner;
        }
        if (snap.phase == Snapshot.Phase.BUST) {
            return snap.banner == null ? "" : snap.banner;
        }
        return "Kept this hand";
    }

    /** Column-align short tags. Pack left to right when a tag is wider than its slot. */
    private static String alignUnderDice(String[] labels, Snapshot snap, Glyphs g, int inner,
                                          boolean narrow, String style) {
        int dieW = narrow ? 5 : 9;
        int gap = 2;
        int count = labels.length;
        int total = count * dieW + Math.max(0, count - 1) * gap;
        int pad = Math.max(0, (inner - total) / 2);
        boolean overlap = false;
        int cursor = 0;
        for (int i = 0; i < count; i++) {
            int dieX = pad + i * (dieW + gap);
            int width = Paint.width(labels[i]);
            int start = dieX + Math.max(0, (dieW - width) / 2);
            if (start < cursor) {
                overlap = true;
                break;
            }
            cursor = start + width;
        }
        if (cursor > inner) {
            overlap = true;
        }
        Paint.Row row = row(inner, snap);
        if (!overlap) {
            int x = 0;
            for (int i = 0; i < count; i++) {
                int dieX = pad + i * (dieW + gap);
                int width = Paint.width(labels[i]);
                int start = dieX + Math.max(0, (dieW - width) / 2);
                if (start > x) {
                    row.add(Paint.spaces(start - x), "");
                }
                row.add(labels[i], styleForTag(labels[i], g, style));
                x = start + width;
            }
            return row.finish();
        }
        boolean any = false;
        for (String label : labels) {
            if (label.isEmpty()) {
                continue;
            }
            if (any) {
                row.add(" ", "");
            }
            row.add(label, styleForTag(label, g, style));
            any = true;
        }
        return row.finish();
    }

    private static String styleForTag(String label, Glyphs g, String fallback) {
        if (label.contains("HELD") || label.contains(g.check)) {
            return Paint.GREEN;
        }
        if ("SCORE".equals(label) || label.contains("SCORES")) {
            return Paint.CYAN;
        }
        if (label.contains(g.cross) || label.contains("no score")) {
            return Paint.RED;
        }
        return fallback;
    }

    private static String summary(Snapshot snap, Glyphs g, int inner) {
        int text = Math.max(1, inner - 2);
        if (snap.phase == Snapshot.Phase.CONTINUE) {
            String banker = snap.banker == null ? "" : snap.banker;
            String line = "Continue " + banker + "'s " + snap.diceLeft
                    + " dice from " + Scorer.format(snap.banked) + ", or start a new hand with 5";
            return inset(plain(line, text, snap, ""));
        }
        if (snap.phase == Snapshot.Phase.BUST || snap.phase == Snapshot.Phase.BANK
                || snap.phase == Snapshot.Phase.HOT || snap.phase == Snapshot.Phase.DOUBLE_REROLL) {
            String banner = snap.phase == Snapshot.Phase.HOT ? hotBanner(g) : snap.banner;
            if (banner == null) {
                banner = "";
            }
            String style = snap.phase == Snapshot.Phase.BUST ? Paint.RED
                    : snap.phase == Snapshot.Phase.HOT || snap.phase == Snapshot.Phase.DOUBLE_REROLL ? Paint.YELLOW : Paint.GREEN;
            String shown = Paint.width(banner) <= text ? banner : wrap(banner, text).get(0);
            return inset(plain(shown, text, snap, style));
        }
        String left;
        String leftStyle = "";
        if (snap.confirmFailed && snap.detail != null && !snap.detail.isEmpty()) {
            left = snap.detail;
            leftStyle = Paint.RED;
        } else {
            left = selectedText(snap);
        }
        String right = snap.phase == Snapshot.Phase.HOLD ? bestText(snap) : "";
        Paint.Row row = row(text, snap);
        if (!right.isEmpty() && Paint.width(left) + 2 + Paint.width(right) <= text) {
            row.add(left, leftStyle);
            row.add(Paint.spaces(text - Paint.width(left) - Paint.width(right)), "");
            row.add(right, Paint.DIM);
        } else {
            row.add(left, leftStyle);
        }
        return inset(row.finish());
    }

    private static String hotBanner(Glyphs g) {
        return g.star + " ALL FIVE SCORED " + g.dot + " roll all 5 again " + g.star;
    }

    private static String selectedText(Snapshot snap) {
        if (snap.faces == null || snap.faces.length == 0) {
            return "Selected: nothing yet";
        }
        int[] indexes = snap.selectedIndexes();
        if (indexes.length == 0) {
            return "Selected: nothing yet";
        }
        for (int index : indexes) {
            if (index < 0 || index >= snap.faces.length) {
                return "Selected: nothing yet";
            }
        }
        StringBuilder text = new StringBuilder("Selected: ");
        for (int i = 0; i < indexes.length; i++) {
            if (i > 0) {
                text.append(" + ");
            }
            text.append(snap.faces[indexes[i]]);
        }
        Scorer.Scoring scoring = Scorer.score(LineView.facesAt(snap.faces, indexes));
        if (scoring.valid()) {
            text.append(" = ").append(Scorer.format(scoring.points()));
        }
        return text.toString();
    }

    private static String bestText(Snapshot snap) {
        if (snap.faces == null || snap.faces.length == 0) {
            return "";
        }
        if (snap.faces.length == 5) {
            int[] rollCounts = Scorer.counts(snap.faces);
            if (Scorer.isStraight(rollCounts)) {
                return "Best hold: 1, 2, 3, 4, 5 = 1,500";
            }
            if (Scorer.isFullHouse(rollCounts)) {
                int three = 0;
                int pair = 0;
                for (int face = 1; face <= 6; face++) {
                    if (rollCounts[face] == 3) {
                        three = face;
                    } else if (rollCounts[face] == 2) {
                        pair = face;
                    }
                }
                return "Best hold: " + three + ", " + three + ", " + three + ", "
                        + pair + ", " + pair + " = 1,250";
            }
        }
        int[] indexes = Scorer.scoringIndexes(snap.faces);
        if (indexes.length == 0) {
            return "Best hold: none";
        }
        int[] counts = new int[7];
        for (int index : indexes) {
            counts[snap.faces[index]]++;
        }
        StringBuilder text = new StringBuilder("Best hold: ");
        boolean any = false;
        for (int face = 1; face <= 6; face++) {
            if (counts[face] >= 3) {
                for (int n = 0; n < counts[face]; n++) {
                    if (any) {
                        text.append(", ");
                    }
                    text.append(face);
                    any = true;
                }
            }
        }
        int ones = counts[1] >= 3 ? 0 : counts[1];
        int fives = counts[5] >= 3 ? 0 : counts[5];
        for (int n = 0; n < ones; n++) {
            if (any) {
                text.append(", ");
            }
            text.append(1);
            any = true;
        }
        for (int n = 0; n < fives; n++) {
            if (any) {
                text.append(", ");
            }
            text.append(5);
            any = true;
        }
        Scorer.Scoring scoring = Scorer.score(LineView.facesAt(snap.faces, indexes));
        text.append(" = ").append(Scorer.format(scoring.points()));
        return text.toString();
    }

    private static Kind[] kinds(Snapshot snap, Glyphs g) {
        int n = snap.faces.length;
        Kind[] kinds = new Kind[n];
        if (snap.phase == Snapshot.Phase.BUST) {
            for (int i = 0; i < n; i++) {
                kinds[i] = Kind.BUST;
            }
            return kinds;
        }
        boolean[] scoring = new boolean[n];
        for (int index : Scorer.scoringIndexes(snap.faces)) {
            scoring[index] = true;
        }
        int[] picked = snap.selected == null ? new int[0] : snap.selectedIndexes();
        boolean selectionScores = picked.length > 0
                && Scorer.score(LineView.facesAt(snap.faces, picked)).valid();
        for (int i = 0; i < n; i++) {
            boolean held = snap.selected != null && i < snap.selected.length && snap.selected[i];
            if (!held) {
                kinds[i] = scoring[i] ? Kind.SCORES : Kind.DEAD;
                continue;
            }
            if (selectionScores) {
                kinds[i] = Kind.HELD;
            } else if (snap.confirmFailed) {
                kinds[i] = Kind.BAD;
            } else {
                kinds[i] = Kind.PLAIN;
            }
        }
        return kinds;
    }

    private static String tagText(Snapshot snap, Glyphs g, int index, Kind kind, boolean narrow) {
        int face = snap.faces[index];
        return switch (kind) {
            case HELD -> {
                if (narrow) {
                    yield "HELD";
                }
                int[] picked = snap.selectedIndexes();
                if (picked.length == 5) {
                    int[] pickedCounts = Scorer.counts(LineView.facesAt(snap.faces, picked));
                    if (Scorer.isStraight(pickedCounts) || Scorer.isFullHouse(pickedCounts)) {
                        yield "HELD";
                    }
                }
                int count = 0;
                for (int i = 0; i < snap.faces.length; i++) {
                    if (snap.selected[i] && snap.faces[i] == face) {
                        count++;
                    }
                }
                if (count >= 3) {
                    yield "HELD";
                }
                yield face == 1 ? "HELD 100" : "HELD 50";
            }
            case SCORES -> narrow ? "SCORE" : "SCORES";
            case BAD -> narrow ? g.cross : g.cross + " no score";
            case BUST -> g.cross;
            default -> "";
        };
    }

    private static String style(Kind kind) {
        return switch (kind) {
            case HELD -> Paint.GREEN;
            case SCORES -> Paint.CYAN;
            case DEAD, KEPT, UNKNOWN -> Paint.DIM;
            case BUST, BAD -> Paint.RED;
            case PLAIN -> "";
        };
    }

    private static String[] dieArt(int face, Kind kind, boolean narrow, Glyphs g) {
        if (narrow || !g.unicode) {
            return digitDie(face, kind, !narrow, g);
        }
        return pipDie(face, kind, g);
    }

    private static String[] smallDie(int face, Kind kind, Glyphs g) {
        return digitDie(face < 0 ? 0 : face, kind == Kind.UNKNOWN ? Kind.UNKNOWN : kind, false, g);
    }

    private static String[] pipDie(int face, Kind kind, Glyphs g) {
        boolean heavy = kind == Kind.HELD;
        String h = heavy ? g.dh : g.h;
        String v = heavy ? g.dv : g.v;
        String top = (heavy ? g.dtl : g.tl) + h.repeat(7) + (heavy ? g.dtr : g.tr);
        String bot = (heavy ? g.dbl : g.bl) + h.repeat(7) + (heavy ? g.dbr : g.br);
        String[] grid = kind == Kind.UNKNOWN
                ? new String[] {"       ", "       ", "       "}
                : pips(face, g.pip);
        return new String[] {
                top,
                v + grid[0] + v,
                v + grid[1] + v,
                v + grid[2] + v,
                bot
        };
    }

    private static String[] pips(int face, String pip) {
        boolean tl = false, tr = false, ml = false, mc = false, mr = false, bl = false, br = false;
        switch (face) {
            case 1 -> mc = true;
            case 2 -> { tl = true; br = true; }
            case 3 -> { tl = true; mc = true; br = true; }
            case 4 -> { tl = true; tr = true; bl = true; br = true; }
            case 5 -> { tl = true; tr = true; mc = true; bl = true; br = true; }
            default -> { tl = true; tr = true; ml = true; mr = true; bl = true; br = true; }
        }
        return new String[] {
                cells(tl, false, tr, pip),
                cells(ml, mc, mr, pip),
                cells(bl, false, br, pip)
        };
    }

    private static String cells(boolean left, boolean mid, boolean right, String pip) {
        char mark = pip.charAt(0);
        char[] raw = "       ".toCharArray();
        if (left) {
            raw[1] = mark;
        }
        if (mid) {
            raw[3] = mark;
        }
        if (right) {
            raw[5] = mark;
        }
        return new String(raw);
    }

    private static String[] digitDie(int face, Kind kind, boolean wide, Glyphs g) {
        boolean heavy = kind == Kind.HELD;
        String h = heavy ? g.dh : g.h;
        String v = heavy ? g.dv : g.v;
        String mark = kind == Kind.UNKNOWN ? " " : Integer.toString(Math.max(1, face));
        if (wide) {
            String top = (heavy ? g.dtl : g.tl) + h.repeat(7) + (heavy ? g.dtr : g.tr);
            String bot = (heavy ? g.dbl : g.bl) + h.repeat(7) + (heavy ? g.dbr : g.br);
            String mid = v + center(mark, 7) + v;
            String blank = v + "       " + v;
            return new String[] {top, blank, mid, blank, bot};
        }
        String top = (heavy ? g.dtl : g.tl) + h.repeat(3) + (heavy ? g.dtr : g.tr);
        String bot = (heavy ? g.dbl : g.bl) + h.repeat(3) + (heavy ? g.dbr : g.br);
        String mid = v + center(mark, 3) + v;
        return new String[] {top, mid, bot};
    }

    private static String center(String text, int width) {
        int pad = Math.max(0, width - Paint.width(text));
        int left = pad / 2;
        return Paint.spaces(left) + text + Paint.spaces(pad - left);
    }

    private static List<String> logLines(Snapshot snap, Glyphs g, int inner, int rows) {
        int text = Math.max(1, inner - 2);
        String older = snap.logOlder == null ? "" : snap.logOlder;
        String newer = snap.logNewer == null ? "" : snap.logNewer;
        if (snap.confirmFailed && snap.detail != null && snap.detail.equals(newer)) {
            newer = "";
        }
        List<String> wrapped = new ArrayList<>();
        wrapped.addAll(wrap(older, text));
        wrapped.addAll(wrap(newer, text));
        while (wrapped.size() < rows) {
            wrapped.add(0, "");
        }
        if (wrapped.size() > rows) {
            wrapped = new ArrayList<>(wrapped.subList(wrapped.size() - rows, wrapped.size()));
        }
        List<String> lines = new ArrayList<>();
        for (String line : wrapped) {
            lines.add(inset(plain(line, text, snap, Paint.DIM)));
        }
        return lines;
    }

    private static String hint(Snapshot snap, Glyphs g, int inner) {
        int text = Math.max(1, inner - 2);
        if (snap.confirmQuit) {
            return inset(segments(snap, text,
                    seg("Quit this game? ", "", false),
                    seg("y", Paint.BOLD, false),
                    seg(" / ", "", false),
                    seg("n", Paint.BOLD, false)));
        }
        Player player = current(snap);
        boolean labels = true;
        boolean extra = true;
        boolean rightLabels = true;
        while (true) {
            List<Seg> left = hintLeft(snap, g, player, labels, extra);
            List<Seg> right = rulesQuit(rightLabels);
            int gap = text - segWidth(left) - segWidth(right);
            if (gap >= 2) {
                return inset(spread(snap, text, left, right, gap));
            }
            if (extra && hasExtra(snap)) {
                extra = false;
                continue;
            }
            if (labels) {
                labels = false;
                continue;
            }
            if (rightLabels) {
                rightLabels = false;
                continue;
            }
            return inset(spread(snap, text, left, right, Math.max(0, gap)));
        }
    }

    private static boolean hasExtra(Snapshot snap) {
        return snap.phase == Snapshot.Phase.BANK_OR_ROLL;
    }

    private static List<Seg> hintLeft(Snapshot snap, Glyphs g, Player player, boolean labels, boolean extra) {
        List<Seg> left = new ArrayList<>();
        if (player.computer && snap.phase != Snapshot.Phase.WIN) {
            addPair(left, player.label(), "is playing" + g.ellipsis, true, true);
            addPair(left, "space", labels ? "skip ahead" : "", labels, false);
            return left;
        }
        switch (snap.phase) {
            case HOLD -> {
                addPair(left, "1-5", "toggle", labels, true);
                addPair(left, "a", "hold best", labels, false);
                addPair(left, g.enter, "confirm", labels, false);
            }
            case BANK_OR_ROLL -> {
                addPair(left, "r", "roll " + snap.diceLeft + " dice", labels, true);
                addPair(left, "b", "bank " + Scorer.format(snap.hand), labels, false);
                if (extra) {
                    left.add(seg("  choose r or b", Paint.DIM, false));
                }
            }
            case ROLL_ONLY -> {
                addPair(left, "r/" + g.enter, "roll " + snap.diceLeft + " dice", labels, true);
                addPair(left, "b", "bank (need " + Scorer.format(snap.opening) + ", have "
                        + Scorer.format(snap.hand) + ")", labels, false);
            }
            case HOT -> addPair(left, "r/" + g.enter, "roll 5 dice", labels, true);
            case CONTINUE -> {
                addPair(left, "c", "continue (" + snap.diceLeft + ")", labels, true);
                addPair(left, "n", "new hand (5)", labels, false);
            }
            case BUST, BANK -> addPair(left, "any key", "continue", labels, true);
            case DOUBLE_REROLL -> addPair(left, "any key", "roll again", labels, true);
            default -> addPair(left, "q", "quit", labels, true);
        }
        return left;
    }

    private static void addPair(List<Seg> segs, String key, String label, boolean labels, boolean first) {
        if (!first) {
            segs.add(seg("  ", "", false));
        }
        segs.add(seg(key, Paint.BOLD, false));
        if (labels && label != null && !label.isEmpty()) {
            segs.add(seg(" " + label, Paint.DIM, false));
        }
    }

    private static List<Seg> rulesQuit(boolean labels) {
        List<Seg> segs = new ArrayList<>();
        segs.add(seg("?", Paint.BOLD, false));
        if (labels) {
            segs.add(seg(" rules", Paint.DIM, false));
        }
        segs.add(seg("  ", "", false));
        segs.add(seg("q", Paint.BOLD, false));
        if (labels) {
            segs.add(seg(" quit", Paint.DIM, false));
        }
        return segs;
    }

    private static String spread(Snapshot snap, int width, List<Seg> left, List<Seg> right, int gap) {
        Paint.Row row = row(width, snap);
        for (Seg piece : left) {
            row.add(piece.text, piece.sgr);
        }
        if (gap > 0) {
            row.add(Paint.spaces(gap), "");
        }
        for (Seg piece : right) {
            row.add(piece.text, piece.sgr);
        }
        return row.finish();
    }

    private static String segments(Snapshot snap, int width, Seg... segs) {
        List<Seg> kept = new ArrayList<>(List.of(segs));
        while (segWidth(kept) > width) {
            int optional = -1;
            for (int i = kept.size() - 1; i >= 0; i--) {
                if (kept.get(i).optional) {
                    optional = i;
                    break;
                }
            }
            if (optional < 0) {
                break;
            }
            kept.remove(optional);
        }
        Paint.Row row = row(width, snap);
        for (Seg seg : kept) {
            row.add(seg.text, seg.sgr);
        }
        return row.finish();
    }

    private static int segWidth(List<Seg> segs) {
        int width = 0;
        for (Seg seg : segs) {
            width += Paint.width(seg.text);
        }
        return width;
    }

    private static Seg seg(String text, String sgr, boolean optional) {
        return new Seg(text, sgr, optional);
    }

    private static List<String> stampRules(List<String> under, Snapshot snap, int cols, int rows) {
        Glyphs g = Glyphs.of(snap.unicode);
        int boxW = Math.min(cols - 2, 72);
        if (boxW < 20) {
            boxW = cols;
        }
        int textW = boxW - 4;
        List<String> body = new ArrayList<>();
        body.add("RULES");
        body.addAll(cheat(snap, g));
        body.add("");
        for (String line : LineView.rulesText(snap.winning, snap.opening)) {
            if (line.isEmpty() || line.equals("GREED")) {
                continue;
            }
            body.add(line);
        }
        List<String> wrapped = new ArrayList<>();
        for (String line : body) {
            if (line.isEmpty()) {
                wrapped.add("");
            } else {
                wrapped.addAll(wrap(line, textW));
            }
        }
        int maxBody = rows - 4;
        wrapped = shrink(wrapped, maxBody);
        int boxH = Math.min(rows - 2, wrapped.size() + 2);
        int top = Math.max(0, (rows - boxH) / 2);
        int left = Math.max(0, (cols - boxW) / 2);
        List<String> frame = new ArrayList<>(under);
        for (int r = 0; r < boxH; r++) {
            String piece;
            if (r == 0) {
                piece = g.tl + g.h.repeat(boxW - 2) + g.tr;
            } else if (r == boxH - 1) {
                String close = " esc/? close ";
                int dashes = boxW - 2 - Paint.width(close);
                int leftDashes = Math.max(0, dashes / 2);
                piece = g.bl + g.h.repeat(leftDashes) + close + g.h.repeat(dashes - leftDashes) + g.br;
            } else {
                int index = r - 1;
                String text = index < wrapped.size() ? wrapped.get(index) : "";
                piece = g.v + " " + text + Paint.spaces(textW - Paint.width(text)) + " " + g.v;
            }
            if (Paint.width(piece) != boxW) {
                piece = piece + Paint.spaces(Math.max(0, boxW - Paint.width(piece)));
                if (Paint.width(piece) > boxW) {
                    piece = clipPlain(piece, boxW);
                }
            }
            Paint.Row row = row(cols, snap);
            row.add(Paint.spaces(left), "");
            row.add(piece, r == 1 ? Paint.BOLD : "");
            frame.set(top + r, row.finish());
        }
        return frame;
    }

    private static List<String> cheat(Snapshot snap, Glyphs g) {
        String times = g.times;
        List<String> lines = new ArrayList<>();
        if (snap.unicode) {
            lines.add("\u2680 1 = 100    \u2684 5 = 50");
        } else {
            lines.add("1 = 100    5 = 50");
        }
        lines.add("three of a kind = face " + times + " 100 (three 1s = 1,000)");
        lines.add("four of a kind " + times + " 2    five of a kind " + times + " 4");
        lines.add("A set scores only when those dice come from one roll.");
        lines.add("first bank must be " + Scorer.format(snap.opening) + "+");
        return lines;
    }

    private static String header(Snapshot snap, Glyphs g, int cols) {
        String target = "to " + Scorer.format(snap.winning);
        int dashes = cols - 13 - Paint.width(target);
        if (dashes < 1) {
            dashes = 1;
        }
        Paint.Row row = row(cols, snap);
        row.add(g.tl, Paint.DIM);
        row.add(g.h, Paint.DIM);
        row.add(" ", "");
        row.add("GREED", Paint.BOLD);
        row.add(" ", "");
        row.add(g.h.repeat(dashes), Paint.DIM);
        row.add(" ", "");
        row.add(target, Paint.DIM);
        row.add(" ", "");
        row.add(g.h, Paint.DIM);
        row.add(g.tr, Paint.DIM);
        return row.finish();
    }

    private static String bottom(Snapshot snap, Glyphs g, int cols) {
        return paint(g.bl, Paint.DIM, snap)
                + paint(g.h.repeat(Math.max(0, cols - 2)), Paint.DIM, snap)
                + paint(g.br, Paint.DIM, snap);
    }

    private static String rule(Snapshot snap, Glyphs g, int cols) {
        return paint(g.teeL, Paint.DIM, snap)
                + paint(g.h.repeat(Math.max(0, cols - 2)), Paint.DIM, snap)
                + paint(g.teeR, Paint.DIM, snap);
    }

    private static String box(String inner, Snapshot snap, Glyphs g) {
        return paint(g.v, Paint.DIM, snap) + inner + paint(g.v, Paint.DIM, snap);
    }

    /** One space inside each side border. {@code body} is already {@code inner - 2} wide. */
    private static String inset(String body) {
        return " " + body + " ";
    }

    private static String plain(String text, int width, Snapshot snap, String sgr) {
        Paint.Row row = row(width, snap);
        row.add(text == null ? "" : text, sgr);
        return row.finish();
    }

    private static Paint.Row row(int width, Snapshot snap) {
        Glyphs g = Glyphs.of(snap.unicode);
        return new Paint.Row(width, snap.color, snap.dim, g.ellipsis);
    }

    private static String paint(String text, String sgr, Snapshot snap) {
        String style = Paint.pick(sgr, snap.color, snap.dim);
        if (style.isEmpty()) {
            return text;
        }
        return style + text + Paint.RESET;
    }

    private static String ellipsize(String text, int room, String dots) {
        if (Paint.width(text) <= room) {
            return text;
        }
        int dotW = Paint.width(dots);
        if (room <= dotW) {
            return clipPlain(dots, room);
        }
        int keep = room - dotW;
        int end = text.offsetByCodePoints(0, keep);
        return text.substring(0, end) + dots;
    }

    private static String clipPlain(String text, int room) {
        if (Paint.width(text) <= room) {
            return text;
        }
        int end = text.offsetByCodePoints(0, Math.max(0, room));
        return text.substring(0, end);
    }

    private static List<Player> seat(Snapshot snap) {
        if (snap.players == null || snap.players.isEmpty()) {
            return List.of(new Player("Player 1", false));
        }
        return snap.players;
    }

    private static Player current(Snapshot snap) {
        List<Player> players = seat(snap);
        int index = Math.max(0, Math.min(snap.current, players.size() - 1));
        return players.get(index);
    }

    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        if (width < 1) {
            width = 1;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            if (word.isEmpty()) {
                continue;
            }
            while (Paint.width(word) > width) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                int end = word.offsetByCodePoints(0, width);
                lines.add(word.substring(0, end));
                word = word.substring(end);
            }
            if (line.length() == 0) {
                line.append(word);
            } else if (Paint.width(line.toString()) + 1 + Paint.width(word) <= width) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private enum Kind {PLAIN, HELD, SCORES, DEAD, BAD, BUST, KEPT, UNKNOWN}

    private record Seg(String text, String sgr, boolean optional) {}

    /** Box-drawing and the backup glyphs. ASCII when the locale is not UTF-8. */
    static final class Glyphs {
        final boolean unicode;
        final String h;
        final String v;
        final String tl;
        final String tr;
        final String bl;
        final String br;
        final String teeL;
        final String teeR;
        final String dh;
        final String dv;
        final String dtl;
        final String dtr;
        final String dbl;
        final String dbr;
        final String pip;
        final String marker;
        final String barOn;
        final String barOff;
        final String check;
        final String cross;
        final String star;
        final String ellipsis;
        final String dot;
        final String times;
        final String arrow;
        final String enter;

        private Glyphs(boolean unicode, String h, String v, String tl, String tr, String bl, String br,
                        String teeL, String teeR, String dh, String dv, String dtl, String dtr,
                        String dbl, String dbr, String pip, String marker, String barOn, String barOff,
                        String check, String cross, String star, String ellipsis, String dot,
                        String times, String arrow, String enter) {
            this.unicode = unicode;
            this.h = h;
            this.v = v;
            this.tl = tl;
            this.tr = tr;
            this.bl = bl;
            this.br = br;
            this.teeL = teeL;
            this.teeR = teeR;
            this.dh = dh;
            this.dv = dv;
            this.dtl = dtl;
            this.dtr = dtr;
            this.dbl = dbl;
            this.dbr = dbr;
            this.pip = pip;
            this.marker = marker;
            this.barOn = barOn;
            this.barOff = barOff;
            this.check = check;
            this.cross = cross;
            this.star = star;
            this.ellipsis = ellipsis;
            this.dot = dot;
            this.times = times;
            this.arrow = arrow;
            this.enter = enter;
        }

        static Glyphs of(boolean unicode) {
            if (unicode) {
                return new Glyphs(true,
                        "─", "│", "╭", "╮", "╰", "╯", "├", "┤",
                        "═", "║", "╔", "╗", "╚", "╝",
                        "●", "▶", "■", "□", "✓", "✗", "★", "…", "·", "×", "→", "⏎");
            }
            return new Glyphs(false,
                    "-", "|", "+", "+", "+", "+", "+", "+",
                    "=", "|", "+", "+", "+", "+",
                    "*", ">", "#", "-", "*", "X", "*", "...", ".", "x", "->", "Enter");
        }
    }
}
