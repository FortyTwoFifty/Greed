package greed;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            for (String line : title()) {
                body.add(line);
            }
            body.add("");
            body.add("First to " + Scorer.format(snap.winning) + ". Hold 1s, 5s, and three of a kind.");
            body.add("");
            body.addAll(cheat(snap, g));
            body.add("");
            body.add("How many players?  [1-9,  0 = 10]");
        } else {
            body.add("Name each seat. Enter moves on. Empty uses Player N.");
            if (snap.solo) {
                body.add("One human sits with the computer. Rook takes the other seat.");
            }
            body.add("");
            int seat = 1;
            for (Player player : snap.players) {
                body.add(seat + "  " + player.name + (player.computer ? " [bot]" : ""));
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
        return chrome(snap, g, cols, rows, body, inner, names ? "type a name, Enter seats them, q quits" : "1-9 players, 0 = 10, q quits");
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
        List<Player> players = seat(snap);
        Player winner = snap.winner == null ? players.get(0) : snap.winner;
        List<Player> order = new ArrayList<>(players);
        order.sort((left, right) -> Integer.compare(right.score, left.score));
        List<String> body = new ArrayList<>();
        body.add("");
        body.add(winner.name.toUpperCase(Locale.ROOT) + " WINS");
        body.add("");
        for (int i = 0; i < order.size(); i++) {
            Player player = order.get(i);
            String rank = (i + 1) + "  ";
            String score = Scorer.format(player.score);
            String bot = player.computer ? " [bot]" : "";
            String name = player.name + bot;
            int room = inner - Paint.width(rank) - 2 - Paint.width(score);
            if (room >= Paint.width(name)) {
                int gap = inner - Paint.width(rank) - Paint.width(name) - Paint.width(score);
                body.add(rank + name + Paint.spaces(gap) + score);
            } else {
                for (String part : wrap(name, Math.max(1, inner - Paint.width(rank)))) {
                    body.add(rank + part);
                    rank = Paint.spaces(Paint.width(rank));
                }
                body.add(Paint.spaces(inner - Paint.width(score)) + score);
            }
        }
        return chrome(snap, g, cols, rows, body, inner, "n new game   q quit");
    }

    /** Header, body, hint, bottom edge. Body is padded or compacted so the frame is exactly {@code rows}. */
    private static List<String> chrome(Snapshot snap, Glyphs g, int cols, int rows,
                                        List<String> body, int inner, String hintPlain) {
        List<String> content = new ArrayList<>(body);
        int room = rows - 3;
        content = shrink(content, room);
        List<String> frame = new ArrayList<>();
        frame.add(header(snap, g, cols));
        for (String line : content) {
            frame.add(box(plain(line, inner, snap, line.equals(snap.fieldError) ? Paint.RED : ""), snap, g));
        }
        while (frame.size() < rows - 2) {
            frame.add(box(Paint.spaces(inner), snap, g));
        }
        frame.add(box(hintPlain(snap, hintPlain, inner), snap, g));
        frame.add(bottom(snap, g, cols));
        return frame;
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
        boolean compact = false;
        List<String> status = List.of();
        int faceRows = narrow ? 3 : 5;
        int tagRows = 2;
        for (int pass = 0; pass < 6; pass++) {
            faceRows = narrow ? 3 : 5;
            tagRows = merge ? 1 : 2;
            int diceRows = faceRows + tagRows + 1;
            int dividerRows = dividers ? 4 : 0;
            int budget = rows - 5 - dividerRows - diceRows;
            status = buildStatus(snap, g, inner, narrow, window, compact);
            if (budget >= status.size()) {
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
            if (!compact) {
                compact = true;
                continue;
            }
            break;
        }
        faceRows = narrow ? 3 : 5;
        tagRows = merge ? 1 : 2;
        int diceRows = faceRows + tagRows + 1;
        int dividerRows = dividers ? 4 : 0;
        int logRows = 2;
        int budget = rows - 5 - dividerRows - diceRows;
        if (status.size() > budget) {
            logRows = 1;
            budget++;
        }
        if (status.size() > budget) {
            throw new IllegalStateException("status " + status.size() + " exceeds budget " + budget
                    + " at " + cols + "x" + rows);
        }
        List<String> dice = diceBlock(snap, g, inner, narrow, faceRows, tagRows);
        List<String> logs = logLines(snap, g, inner, logRows);
        String hint = hint(snap, g, inner);

        List<String> frame = new ArrayList<>();
        frame.add(header(snap, g, cols));
        if (dividers) {
            frame.add(rule(snap, g, cols));
        }
        for (String line : status) {
            frame.add(box(line, snap, g));
        }
        int dividersLeft = dividers ? 3 : 0;
        int tail = dividersLeft + dice.size() + logRows + 2;
        int fill = rows - frame.size() - tail;
        for (int i = 0; i < fill; i++) {
            frame.add(box(Paint.spaces(inner), snap, g));
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
        frame.add(box(hint, snap, g));
        frame.add(bottom(snap, g, cols));
        if (frame.size() != rows) {
            throw new IllegalStateException("frame " + frame.size() + " rows, wanted " + rows);
        }
        return frame;
    }

    private static List<String> buildStatus(Snapshot snap, Glyphs g, int inner, boolean narrow,
                                             boolean window, boolean compact) {
        List<Player> players = seat(snap);
        int current = Math.max(0, Math.min(snap.current, players.size() - 1));
        List<Integer> shown = visible(players.size(), current, window);
        int hidden = players.size() - shown.size();
        if (narrow) {
            List<String> lines = new ArrayList<>();
            for (int index : shown) {
                lines.add(playerLine(snap, g, players.get(index), index, index == current, inner, true));
            }
            if (hidden > 0) {
                lines.add(plain("+" + hidden + " more", inner, snap, Paint.DIM));
            }
            lines.addAll(turnLines(snap, g, players.get(current), inner, compact));
            return lines;
        }
        int leftW = (inner - 1) / 2;
        int rightW = inner - 1 - leftW;
        List<String> left = new ArrayList<>();
        for (int index : shown) {
            left.add(playerLine(snap, g, players.get(index), index, index == current, leftW, false));
        }
        if (hidden > 0) {
            left.add(plain("+" + hidden + " more", leftW, snap, Paint.DIM));
        }
        List<String> right = turnLines(snap, g, players.get(current), rightW, compact);
        int rows = Math.max(left.size(), right.size());
        String mid = paint(g.v, Paint.DIM, snap);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            String l = i < left.size() ? left.get(i) : Paint.spaces(leftW);
            String r = i < right.size() ? right.get(i) : Paint.spaces(rightW);
            lines.add(l + mid + r);
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
                                      int width, boolean narrow) {
        Paint.Row row = row(width, snap);
        String marker = (current ? g.marker : " ") + " ";
        int score = snap.scoreOf(index);
        String scoreText = Scorer.format(score);
        String bot = player.computer ? " [bot]" : "";
        String tail;
        String tailStyle;
        if (!player.onBoard) {
            tail = g.dot.repeat(5) + "  needs " + Scorer.format(snap.opening);
            tailStyle = Paint.DIM;
        } else if (narrow) {
            tail = "";
            tailStyle = "";
        } else {
            tail = bar(snap, g, score);
            tailStyle = "";
        }
        int tailW = Paint.width(tail);
        int fixed = Paint.width(marker) + Paint.width(bot) + Paint.width(scoreText) + tailW
                + 1 + (tailW == 0 ? 0 : 1);
        int nameRoom = width - fixed;
        String name = ellipsize(player.name, Math.max(1, nameRoom), g.ellipsis);
        String nameStyle = current ? Paint.YELLOW : "";
        row.add(marker, nameStyle);
        row.add(name, nameStyle);
        row.add(bot, Paint.MAGENTA);
        int pad = width - Paint.width(marker) - Paint.width(name) - Paint.width(bot)
                - Paint.width(scoreText) - (tailW == 0 ? 0 : 1 + tailW);
        if (pad > 0) {
            row.add(Paint.spaces(pad), "");
        }
        row.add(scoreText, "");
        if (tailW > 0) {
            row.add(" ", "");
            row.add(tail, tailStyle);
        }
        return row.finish();
    }

    private static String bar(Snapshot snap, Glyphs g, int score) {
        int filled = snap.winning <= 0 ? 0 : (int) Math.round(10.0 * score / snap.winning);
        filled = Math.max(0, Math.min(10, filled));
        return g.barOn.repeat(filled) + g.barOff.repeat(10 - filled);
    }

    private static List<String> turnLines(Snapshot snap, Glyphs g, Player player, int width, boolean compact) {
        List<String> lines = new ArrayList<>();
        String name = player.name.toUpperCase(Locale.ROOT);
        if (snap.phase == Snapshot.Phase.CONTINUE) {
            String one = "CONTINUING " + name + "'S " + snap.diceLeft + " DICE";
            if (Paint.width(one) <= width) {
                lines.add(plain(one, width, snap, Paint.YELLOW));
            } else {
                lines.add(plain("CONTINUING", width, snap, Paint.YELLOW));
                for (String part : wrap(name + "'S " + snap.diceLeft + " DICE", width)) {
                    lines.add(plain(part, width, snap, Paint.YELLOW));
                }
            }
        } else {
            for (String part : wrap(name + "'S TURN", width)) {
                lines.add(plain(part, width, snap, Paint.YELLOW));
            }
        }
        String value = snap.phase == Snapshot.Phase.BUST ? "0" : Scorer.format(snap.hand);
        String tail = "";
        if (snap.phase == Snapshot.Phase.BUST && snap.pointsLost > 0) {
            tail = "  " + Scorer.format(snap.pointsLost) + " lost";
        } else if (compact) {
            tail = "  ·  " + snap.diceLeft + " dice";
        }
        lines.add(handLine(snap, width, value, tail));
        if (!compact) {
            lines.add(plain("Dice to roll  " + snap.diceLeft, width, snap, ""));
        }
        if (!player.onBoard && snap.phase != Snapshot.Phase.BUST) {
            int gap = Math.max(0, snap.opening - snap.hand);
            String need = gap == 0
                    ? "Need " + Scorer.format(snap.opening) + " to get on " + g.dot + " ready"
                    : "Need " + Scorer.format(snap.opening) + " to get on " + g.dot + " "
                    + Scorer.format(gap) + " to go";
            if (!compact) {
                for (String part : wrap(need, width)) {
                    lines.add(plain(part, width, snap, Paint.DIM));
                }
            }
        }
        if (snap.phase == Snapshot.Phase.CONTINUE && !compact) {
            String kept = "Continuing starts at 0. " + snap.banker + " keeps the "
                    + Scorer.format(snap.banked) + " banked.";
            for (String part : wrap(kept, width)) {
                lines.add(plain(part, width, snap, ""));
            }
        }
        return lines;
    }

    private static String handLine(Snapshot snap, int width, String value, String tail) {
        Paint.Row row = row(width, snap);
        row.add("Hand at risk  ", "");
        row.add(value, Paint.YELLOW);
        if (tail.endsWith(" lost")) {
            row.add(tail, Paint.DIM);
        } else if (!tail.isEmpty()) {
            row.add(tail, "");
        }
        return row.finish();
    }

    private static List<String> diceBlock(Snapshot snap, Glyphs g, int inner, boolean narrow,
                                           int faceRows, int tagRows) {
        List<String> lines = new ArrayList<>();
        boolean strip = snap.phase != Snapshot.Phase.HOLD && snap.phase != Snapshot.Phase.BUST;
        if (strip) {
            lines.addAll(stripFaces(snap, g, inner, faceRows));
        } else if (snap.faces.length == 0) {
            for (int i = 0; i < faceRows; i++) {
                lines.add(Paint.spaces(inner));
            }
        } else {
            lines.addAll(bigFaces(snap, g, inner, narrow, faceRows));
        }
        lines.addAll(tagLines(snap, g, inner, narrow, tagRows, strip));
        lines.add(summary(snap, g, inner));
        while (lines.size() < faceRows + tagRows + 1) {
            lines.add(Paint.spaces(inner));
        }
        if (lines.size() > faceRows + tagRows + 1) {
            lines = new ArrayList<>(lines.subList(0, faceRows + tagRows + 1));
        }
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
        return placeDice(art, styles, inner, dieW, faceRows, snap);
    }

    private static List<String> stripFaces(Snapshot snap, Glyphs g, int inner, int faceRows) {
        List<Integer> faces = new ArrayList<>();
        List<Kind> kinds = new ArrayList<>();
        if (snap.phase == Snapshot.Phase.CONTINUE || snap.phase == Snapshot.Phase.HOT) {
            int n = snap.phase == Snapshot.Phase.HOT ? 5 : Math.max(1, snap.diceLeft);
            for (int i = 0; i < n; i++) {
                faces.add(-1);
                kinds.add(Kind.UNKNOWN);
            }
        } else {
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
        List<String> placed = placeDice(art, styles, inner, 5, dieH, snap);
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
        if (strip) {
            String label = stripLabel(snap, g);
            lines.add(plain(label, inner, snap, Paint.DIM));
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
            tags[i] = tagText(snap, g, i, kinds[i]);
            boolean mark = kinds[i] == Kind.HELD;
            keys[i] = "[" + (i + 1) + "]" + (mark ? " " + g.check : "");
        }
        if (tagRows >= 2) {
            lines.add(alignUnderDice(keys, snap, g, inner, narrow, ""));
            lines.add(alignUnderDice(tags, snap, g, inner, narrow, ""));
        } else {
            String[] merged = new String[kinds.length];
            for (int i = 0; i < kinds.length; i++) {
                merged[i] = tags[i].isEmpty() ? keys[i] : keys[i] + " " + tags[i];
            }
            lines.add(alignUnderDice(merged, snap, g, inner, narrow, ""));
        }
        while (lines.size() < tagRows) {
            lines.add(Paint.spaces(inner));
        }
        return lines;
    }

    private static String stripLabel(Snapshot snap, Glyphs g) {
        if (snap.phase == Snapshot.Phase.CONTINUE) {
            return snap.banker + " left " + LineView.diceWord(snap.diceLeft);
        }
        if (snap.phase == Snapshot.Phase.HOT) {
            return g.star + " ALL FIVE SCORED " + g.star;
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
        if (label.contains("SCORES")) {
            return Paint.CYAN;
        }
        if (label.contains(g.cross) || label.contains("no score")) {
            return Paint.RED;
        }
        return fallback;
    }

    private static String summary(Snapshot snap, Glyphs g, int inner) {
        if (snap.phase == Snapshot.Phase.BUST || snap.phase == Snapshot.Phase.BANK
                || snap.phase == Snapshot.Phase.HOT) {
            String banner = snap.phase == Snapshot.Phase.HOT ? hotBanner(g) : snap.banner;
            if (banner == null) {
                banner = "";
            }
            String style = snap.phase == Snapshot.Phase.BUST ? Paint.RED : Paint.GREEN;
            if (Paint.width(banner) <= inner) {
                return plain(banner, inner, snap, style);
            }
            return plain(wrap(banner, inner).get(0), inner, snap, style);
        }
        String left;
        String leftStyle = "";
        if (snap.confirmFailed && snap.detail != null && !snap.detail.isEmpty()
                && Paint.width(snap.detail) <= inner) {
            left = snap.detail;
            leftStyle = Paint.RED;
        } else if (snap.confirmFailed) {
            left = "Selected: no score";
            leftStyle = Paint.RED;
        } else {
            left = selectedText(snap);
        }
        String right = snap.phase == Snapshot.Phase.HOLD ? bestText(snap) : "";
        Paint.Row row = row(inner, snap);
        if (!right.isEmpty() && Paint.width(left) + 2 + Paint.width(right) <= inner) {
            row.add(left, leftStyle);
            row.add(Paint.spaces(inner - Paint.width(left) - Paint.width(right)), "");
            row.add(right, Paint.DIM);
        } else {
            row.add(left, leftStyle);
        }
        return row.finish();
    }

    private static String hotBanner(Glyphs g) {
        return g.star + " ALL FIVE SCORED " + g.dot + " roll all 5 again to keep going " + g.star;
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
        int[] indexes = Scorer.scoringIndexes(snap.faces);
        if (indexes.length == 0) {
            return "Best hold: none";
        }
        Scorer.Scoring scoring = Scorer.score(LineView.facesAt(snap.faces, indexes));
        StringBuilder text = new StringBuilder("Best hold: ");
        for (int i = 0; i < indexes.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(snap.faces[indexes[i]]);
        }
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
        int[] counts = new int[7];
        for (int i = 0; i < n; i++) {
            if (snap.selected != null && i < snap.selected.length && snap.selected[i]) {
                counts[snap.faces[i]]++;
            }
        }
        for (int i = 0; i < n; i++) {
            boolean held = snap.selected != null && i < snap.selected.length && snap.selected[i];
            if (!held) {
                kinds[i] = scoring[i] ? Kind.SCORES : Kind.DEAD;
                continue;
            }
            int face = snap.faces[i];
            if (counts[face] >= 3 || face == 1 || face == 5) {
                kinds[i] = Kind.HELD;
            } else if (snap.confirmFailed) {
                kinds[i] = Kind.BAD;
            } else {
                kinds[i] = Kind.PLAIN;
            }
        }
        return kinds;
    }

    private static String tagText(Snapshot snap, Glyphs g, int index, Kind kind) {
        int face = snap.faces[index];
        return switch (kind) {
            case HELD -> {
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
            case SCORES -> "SCORES";
            case BAD -> g.cross + " no score";
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
        String[] grid = kind == Kind.BUST
                ? new String[] {center(g.cross, 7), "       ", "       "}
                : pips(face, g.pip);
        // Put the bust mark in the middle row.
        if (kind == Kind.BUST) {
            grid = new String[] {"       ", center(g.cross, 7), "       "};
        }
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
        String mark = kind == Kind.BUST ? g.cross : kind == Kind.UNKNOWN ? "?" : Integer.toString(Math.max(1, face));
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
        String older = snap.logOlder == null ? "" : snap.logOlder;
        String newer = snap.logNewer == null ? "" : snap.logNewer;
        if (snap.confirmFailed && snap.detail != null && !snap.detail.isEmpty()
                && !older.contains(snap.detail) && !newer.contains(snap.detail)) {
            older = newer;
            newer = snap.detail;
        }
        if (snap.phase == Snapshot.Phase.CONTINUE && (snap.banner == null || snap.banner.isEmpty())) {
            // The continuing sentence is on the turn panel when there is room.
        }
        List<String> wrapped = new ArrayList<>();
        wrapped.addAll(wrap(older, inner));
        wrapped.addAll(wrap(newer, inner));
        while (wrapped.size() < rows) {
            wrapped.add(0, "");
        }
        if (wrapped.size() > rows) {
            wrapped = new ArrayList<>(wrapped.subList(wrapped.size() - rows, wrapped.size()));
        }
        List<String> lines = new ArrayList<>();
        for (String line : wrapped) {
            lines.add(plain(line, inner, snap, Paint.DIM));
        }
        return lines;
    }

    private static String hint(Snapshot snap, Glyphs g, int inner) {
        if (snap.confirmQuit) {
            return segments(snap, inner,
                    seg("Quit this game? ", "", false),
                    seg("y", Paint.BOLD, false),
                    seg(" / ", "", false),
                    seg("n", Paint.BOLD, false));
        }
        Player player = current(snap);
        if (player.computer && snap.phase != Snapshot.Phase.WIN) {
            return segments(snap, inner,
                    seg(player.name + " is playing" + g.ellipsis + "  ", Paint.DIM, false),
                    seg("space", Paint.BOLD, false),
                    seg(" skip ahead  ", Paint.DIM, true),
                    seg("q", Paint.BOLD, false),
                    seg(" quit", Paint.DIM, false));
        }
        List<Seg> segs = new ArrayList<>();
        switch (snap.phase) {
            case HOLD -> {
                segs.add(seg("1-5", Paint.BOLD, false));
                segs.add(seg(" toggle  ", Paint.DIM, false));
                segs.add(seg("a", Paint.BOLD, false));
                segs.add(seg(" hold best  ", Paint.DIM, false));
                segs.add(seg(g.enter, Paint.BOLD, false));
                segs.add(seg(" confirm  ", Paint.DIM, false));
            }
            case BANK_OR_ROLL -> {
                segs.add(seg("r", Paint.BOLD, false));
                segs.add(seg(" roll " + snap.diceLeft + " dice  ", Paint.DIM, false));
                segs.add(seg("b", Paint.BOLD, false));
                segs.add(seg(" bank " + Scorer.format(snap.hand) + "  ", Paint.DIM, false));
                segs.add(seg("choose r or b", Paint.DIM, false));
            }
            case ROLL_ONLY -> {
                segs.add(seg("r/" + g.enter, Paint.BOLD, false));
                segs.add(seg(" roll " + snap.diceLeft + " dice  ", Paint.DIM, false));
                segs.add(seg("b bank (need " + Scorer.format(snap.opening) + ", have "
                        + Scorer.format(snap.hand) + ")", Paint.DIM, false));
            }
            case HOT -> {
                segs.add(seg("r/" + g.enter, Paint.BOLD, false));
                segs.add(seg(" roll 5 dice", Paint.DIM, false));
            }
            case CONTINUE -> {
                segs.add(seg("c", Paint.BOLD, false));
                segs.add(seg(" continue with " + snap.diceLeft + " dice  ", Paint.DIM, false));
                segs.add(seg("n", Paint.BOLD, false));
                segs.add(seg(" new hand with 5 dice  ", Paint.DIM, false));
                segs.add(seg("choose c or n", Paint.DIM, true));
            }
            case BUST, BANK -> {
                segs.add(seg("any key", Paint.BOLD, false));
                segs.add(seg(" continue", Paint.DIM, false));
            }
            default -> {
                segs.add(seg("q", Paint.BOLD, false));
                segs.add(seg(" quit", Paint.DIM, false));
            }
        }
        if (snap.phase != Snapshot.Phase.BANK_OR_ROLL) {
            segs.add(seg("  ", "", true));
            segs.add(seg("?", Paint.BOLD, true));
            segs.add(seg(" rules  ", Paint.DIM, true));
            segs.add(seg("q", Paint.BOLD, true));
            segs.add(seg(" quit", Paint.DIM, true));
        } else {
            segs.add(seg("  ", "", true));
            segs.add(seg("?", Paint.BOLD, true));
            segs.add(seg(" rules  ", Paint.DIM, true));
            segs.add(seg("q", Paint.BOLD, true));
            segs.add(seg(" quit", Paint.DIM, true));
        }
        return segments(snap, inner, segs.toArray(Seg[]::new));
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
        lines.add("first bank must be " + Scorer.format(snap.opening) + "+");
        return lines;
    }

    private static String[] title() {
        return new String[] {
                "#####  #####  #####  #####  ####",
                "#      #   #  #      #      #   #",
                "#  ##  #####  ####   ####   #   #",
                "#   #  #  #   #      #      #   #",
                "#####  #   #  #####  #####  ####"
        };
    }

    private static String header(Snapshot snap, Glyphs g, int cols) {
        String right = "to " + Scorer.format(snap.winning) + " ";
        Paint.Row row = row(cols, snap);
        row.add(g.tl, Paint.DIM);
        row.add(" GREED ", Paint.BOLD);
        int dashes = cols - 2 - Paint.width(" GREED ") - Paint.width(right);
        if (dashes < 1) {
            dashes = 1;
        }
        row.add(g.h.repeat(dashes), Paint.DIM);
        row.add(right, Paint.DIM);
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

    private static String plain(String text, int width, Snapshot snap, String sgr) {
        Paint.Row row = row(width, snap);
        row.add(text == null ? "" : text, sgr);
        return row.finish();
    }

    private static String hintPlain(Snapshot snap, String text, int width) {
        Paint.Row row = row(width, snap);
        row.add(text, Paint.DIM);
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
