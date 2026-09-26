package greed.rules;

/** Command-line flags. {@code --no-color} and {@code NO_COLOR} also turn animation off. */
public final class Options {
    public final boolean plain;
    public final boolean color;
    public final boolean animate;

    private Options(boolean plain, boolean color, boolean animate) {
        this.plain = plain;
        this.color = color;
        this.animate = animate;
    }

    static Options parse(String[] args) {
        return parse(args, System.getenv("NO_COLOR") != null);
    }

    public static Options parse(String[] args, boolean noColorEnv) {
        boolean plain = false;
        boolean color = true;
        boolean animate = true;
        for (String arg : args) {
            switch (arg) {
                case "--plain" -> plain = true;
                case "--no-color" -> color = false;
                case "--no-anim" -> animate = false;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (noColorEnv) {
            color = false;
        }
        if (!color) {
            animate = false;
        }
        return new Options(plain, color, animate);
    }
}
