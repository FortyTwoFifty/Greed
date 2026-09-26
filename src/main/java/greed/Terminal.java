package greed;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Raw mode, the alternate screen, and a guaranteed return to the cooked terminal.
 * Reads and writes {@code /dev/tty}. {@code min 0 time 5} wakes about every 500 ms
 * so a resize is noticed while the game is waiting for a key. ISIG stays on, so
 * Ctrl-C is still SIGINT.
 */
final class Terminal {
    private static final String ALT_ON = "\u001b[?1049h";
    private static final String ALT_OFF = "\u001b[?1049l";
    private static final String CURSOR_HIDE = "\u001b[?25l";
    private static final String CURSOR_SHOW = "\u001b[?25h";

    private final Charset charset;
    private String saved;
    private InputStream in;
    private OutputStream out;
    private volatile boolean raw;
    private volatile boolean saidGoodbye;
    private volatile boolean crashed;
    private int rows;
    private int cols;

    Terminal() {
        Charset detected = StandardCharsets.UTF_8;
        if (System.console() != null) {
            detected = System.console().charset();
        }
        this.charset = detected;
    }

    boolean unicode() {
        String name = charset.name().toUpperCase(java.util.Locale.ROOT);
        if (name.contains("UTF")) {
            return true;
        }
        String locale = firstSet("LC_ALL", "LC_CTYPE", "LANG");
        return locale.toLowerCase(java.util.Locale.ROOT).contains("utf");
    }

    void enter() {
        saved = run(true, "-g").trim();
        if (saved.isEmpty() || saved.indexOf(' ') >= 0 && saved.startsWith("stty")) {
            throw new IllegalStateException("could not read terminal settings");
        }
        // stty -g is a single token. Reject anything that looks like an error sentence.
        if (saved.indexOf('\n') >= 0) {
            saved = saved.replace("\n", "").trim();
        }
        run(false, "-icanon", "-echo", "min", "0", "time", "5");
        try {
            in = new FileInputStream("/dev/tty");
            out = new FileOutputStream("/dev/tty");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        write(ALT_ON + CURSOR_HIDE);
        raw = true;
        readSize();
    }

    void installHooks() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            restore();
            if (!saidGoodbye && !crashed) {
                System.out.println();
                System.out.println("Goodbye.");
                saidGoodbye = true;
            }
        }, "greed-restore"));
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            crashed = true;
            restore();
            error.printStackTrace(System.out);
        });
    }

    /** Idempotent. Leaves the main screen, shows the cursor, and restores echo. */
    synchronized void restore() {
        if (saved == null && !raw) {
            return;
        }
        try {
            if (out != null) {
                write(CURSOR_SHOW + ALT_OFF);
            }
        } catch (RuntimeException ignored) {
            // The hook still has to put echo back.
        }
        try {
            if (saved != null) {
                run(false, saved);
            }
        } catch (RuntimeException ignored) {
            // Already leaving. A second restore retries.
        }
        raw = false;
    }

    void goodbye() {
        restore();
        if (!saidGoodbye) {
            saidGoodbye = true;
            System.out.println();
            System.out.println("Goodbye.");
        }
    }

    void write(String text) {
        if (out == null) {
            return;
        }
        try {
            out.write(text.getBytes(charset));
            out.flush();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * One Unicode scalar, {@code -1} on timeout or EOF, {@code -2} when an arrow
     * or other escape sequence was consumed.
     */
    int readCodePoint() {
        int first = readByte();
        if (first < 0) {
            return -1;
        }
        if (first == 27) {
            return readEscape();
        }
        if (first < 128) {
            return first;
        }
        int need = first >= 0xF0 ? 3 : first >= 0xE0 ? 2 : first >= 0xC0 ? 1 : 0;
        if (need == 0) {
            return first;
        }
        byte[] bytes = new byte[1 + need];
        bytes[0] = (byte) first;
        for (int i = 0; i < need; i++) {
            int next = readByte();
            if (next < 0) {
                return -2;
            }
            bytes[i + 1] = (byte) next;
        }
        String text = new String(bytes, charset);
        if (text.isEmpty()) {
            return -2;
        }
        return text.codePointAt(0);
    }

    /** Tenths of a second. {@code 0} polls, {@code 5} is the idle resize wait. */
    void setWaitTenths(int tenths) {
        run(false, "-icanon", "-echo", "min", "0", "time", Integer.toString(Math.max(0, tenths)));
    }

    int rows() {
        return rows;
    }

    int cols() {
        return cols;
    }

    /** @return true when the reported size changed */
    boolean pollSize() {
        int oldRows = rows;
        int oldCols = cols;
        readSize();
        return rows != oldRows || cols != oldCols;
    }

    private int readEscape() {
        int next = readByte();
        if (next < 0) {
            return 27;
        }
        if (next != '[') {
            return -2;
        }
        int code;
        do {
            code = readByte();
        } while (code >= 0 && (code < 0x40 || code > 0x7E));
        return -2;
    }

    private int readByte() {
        if (in == null) {
            return -1;
        }
        try {
            byte[] buf = new byte[1];
            int n = in.read(buf);
            if (n <= 0) {
                return -1;
            }
            return buf[0] & 0xFF;
        } catch (IOException e) {
            return -1;
        }
    }

    private void readSize() {
        try {
            String text = run(true, "size").trim();
            String[] parts = text.split("\\s+");
            if (parts.length >= 2) {
                rows = Integer.parseInt(parts[0]);
                cols = Integer.parseInt(parts[1]);
            }
        } catch (RuntimeException ignored) {
            // Keep the last good size. The frame treats 0 as unknown.
        }
    }

    private static String firstSet(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String run(boolean capture, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "stty";
        System.arraycopy(args, 0, cmd, 1, args.length);
        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/tty")));
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            byte[] output = capture ? process.getInputStream().readAllBytes() : new byte[0];
            if (!capture) {
                process.getInputStream().readAllBytes();
            }
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException("stty " + String.join(" ", args));
            }
            return new String(output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("stty interrupted");
        }
    }
}
