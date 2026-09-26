package greed;

/** Supplies the next roll. Production uses fair dice; tests use a script. */
public interface DieSource {
    /** @return {@code count} faces, each from 1 through 6 */
    int[] roll(int count);
}
