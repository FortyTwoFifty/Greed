package greed.rules;

/**
 * Command-line flags. {@code --no-color} and {@code NO_COLOR} also turn animation off.
 * {@code --board} and {@code --winning} take the next argument. {@code --dev} shows personalities.
 */
public final class Options {
    public final boolean plain;
    public final boolean color;
    public final boolean animate;
    public final boolean dev;
    public final int opening;
    public final int winning;

    private Options(boolean plain, boolean color, boolean animate, boolean dev, int opening, int winning) {
        this.plain = plain;
        this.color = color;
        this.animate = animate;
        this.dev = dev;
        this.opening = opening;
        this.winning = winning;
    }

    static Options parse(String[] args) {
        return parse(args, System.getenv("NO_COLOR") != null);
    }

    public static Options parse(String[] args, boolean noColorEnv) {
        boolean plain = false;
        boolean color = true;
        boolean animate = true;
        boolean dev = false;
        int opening = Game.OPENING_SCORE;
        int winning = Game.WINNING_SCORE;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--plain" -> plain = true;
                case "--no-color" -> color = false;
                case "--no-anim" -> animate = false;
                case "--dev" -> dev = true;
                case "--board" -> {
                    opening = readNumber(args, i + 1, "--board");
                    i++;
                }
                case "--winning" -> {
                    winning = readNumber(args, i + 1, "--winning");
                    i++;
                }
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (opening >= winning) {
            throw new IllegalArgumentException("--board must be less than --winning");
        }
        if (noColorEnv) {
            color = false;
        }
        if (!color) {
            animate = false;
        }
        return new Options(plain, color, animate, dev, opening, winning);
    }

    /** The next argv token, base 10. A missing token and a non-number are different errors. */
    private static int readNumber(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " needs a number");
        }
        int value;
        try {
            value = Integer.parseInt(args[index]);
        } catch (NumberFormatException rejected) {
            throw new IllegalArgumentException(flag + " must be a whole number");
        }
        if (value <= 0) {
            throw new IllegalArgumentException(flag + " must be greater than 0");
        }
        return value;
    }
}
