package greed.view;

/** ANSI SGR helpers. Color is only the 16-color palette; bold, dim, and reverse survive {@code --no-color}. */
final class Paint {
    static final String RESET = "\u001b[0m";
    static final String BOLD = "\u001b[1m";
    static final String DIM = "\u001b[2m";
    static final String REVERSE = "\u001b[7m";
    static final String YELLOW = "\u001b[1;33m";
    static final String GREEN = "\u001b[1;32m";
    static final String CYAN = "\u001b[36m";
    static final String RED = "\u001b[1;31m";
    static final String MAGENTA = "\u001b[35m";

    private Paint() {}

    static int width(String text) {
        return text.codePointCount(0, text.length());
    }

    /** Bold / dim / reverse kept; hue dropped. A dimmed overlay forces dim. */
    static String pick(String sgr, boolean color, boolean dimAll) {
        if (sgr == null || sgr.isEmpty()) {
            return dimAll ? DIM : "";
        }
        if (dimAll) {
            return DIM;
        }
        if (color) {
            return sgr;
        }
        boolean bold = sgr.contains("[1m") || sgr.contains("[1;");
        boolean dim = sgr.contains("[2m") || sgr.contains(";2");
        boolean rev = sgr.contains("[7m") || sgr.contains(";7");
        if (bold && rev) {
            return "\u001b[1;7m";
        }
        if (bold) {
            return BOLD;
        }
        if (dim) {
            return DIM;
        }
        if (rev) {
            return REVERSE;
        }
        return "";
    }

    static String spaces(int count) {
        if (count <= 0) {
            return "";
        }
        return " ".repeat(count);
    }

    /** One screen row. Visible columns never exceed {@code width}. */
    static final class Row {
        private final StringBuilder raw = new StringBuilder();
        private final int width;
        private final boolean color;
        private final boolean dimAll;
        private final String ellipsis;
        private int col;

        Row(int width, boolean color, boolean dimAll, String ellipsis) {
            this.width = width;
            this.color = color;
            this.dimAll = dimAll;
            this.ellipsis = ellipsis;
        }

        void add(String text, String sgr) {
            if (text == null || text.isEmpty() || col >= width) {
                return;
            }
            int room = width - col;
            if (width(text) > room) {
                text = cut(text, room);
            }
            if (text.isEmpty()) {
                return;
            }
            String style = pick(sgr, color, dimAll);
            if (!style.isEmpty()) {
                raw.append(style).append(text).append(RESET);
            } else {
                raw.append(text);
            }
            col += width(text);
        }

        String finish() {
            if (col < width) {
                raw.append(spaces(width - col));
            }
            return raw.toString();
        }

        private String cut(String text, int room) {
            int dot = width(ellipsis);
            if (room <= 0) {
                return "";
            }
            if (dot > 0 && room > dot && width(text) > room) {
                int keep = room - dot;
                int end = text.offsetByCodePoints(0, Math.min(keep, width(text)));
                return text.substring(0, end) + ellipsis;
            }
            int end = text.offsetByCodePoints(0, Math.min(room, width(text)));
            return text.substring(0, end);
        }
    }
}
