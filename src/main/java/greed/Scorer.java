package greed;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scores a set of dice the player has chosen to hold.
 * A 1 is 100 and a 5 is 50. Three of a kind is face × 100, except three 1s,
 * which are 1,000. Four of a kind is twice that, and five of a kind is twice
 * the four-of-a-kind value. Leftover 1s and 5s still score. Any other leftover
 * die means the selection is illegal.
 */
public final class Scorer {
    public record Scoring(boolean valid, int points, String detail) {
        static Scoring ok(int points, String detail) {
            return new Scoring(true, points, detail);
        }

        static Scoring invalid(String detail) {
            return new Scoring(false, 0, detail);
        }
    }

    private Scorer() {}

    public static int[] counts(int[] faces) {
        int[] counts = new int[7];
        for (int face : faces) {
            if (face < 1 || face > 6) {
                throw new IllegalArgumentException("die face out of range: " + face);
            }
            counts[face]++;
        }
        return counts;
    }

    /** Indexes of dice that can be held: 1s, 5s, and every face with three or more. */
    public static int[] scoringIndexes(int[] roll) {
        int[] counts = counts(roll);
        int matches = 0;
        for (int face : roll) {
            if (isScoringFace(face, counts)) {
                matches++;
            }
        }
        int[] indexes = new int[matches];
        int write = 0;
        for (int i = 0; i < roll.length; i++) {
            if (isScoringFace(roll[i], counts)) {
                indexes[write++] = i;
            }
        }
        return indexes;
    }

    /** True when the roll contains a 1, a 5, or any three of a kind. */
    public static boolean canScore(int[] faces) {
        int[] counts = counts(faces);
        if (counts[1] > 0 || counts[5] > 0) {
            return true;
        }
        for (int face = 2; face <= 6; face++) {
            if (counts[face] >= 3) {
                return true;
            }
        }
        return false;
    }

    public static Scoring score(int[] faces) {
        if (faces == null || faces.length == 0) {
            return Scoring.invalid("Hold at least one scoring die.");
        }
        if (faces.length > 5) {
            return Scoring.invalid("A hand uses at most 5 dice.");
        }

        int[] counts = counts(faces);
        int points = 0;
        List<String> parts = new ArrayList<>();
        List<Integer> unscored = new ArrayList<>();

        for (int face = 1; face <= 6; face++) {
            int count = counts[face];
            if (count == 0) {
                continue;
            }
            int set = count >= 5 ? 5 : count >= 4 ? 4 : count >= 3 ? 3 : 0;
            int leftover = count - set;
            if (set > 0) {
                int value = setValue(face, set);
                points += value;
                parts.add(word(set) + " " + face + "s for " + format(value));
            }
            if (leftover > 0 && (face == 1 || face == 5)) {
                int each = face == 1 ? 100 : 50;
                int value = leftover * each;
                points += value;
                String label = leftover == 1 ? "a " + face : word(leftover) + " " + face + "s";
                parts.add(label + " for " + format(value));
            } else if (leftover > 0) {
                for (int i = 0; i < leftover; i++) {
                    unscored.add(face);
                }
            }
        }

        if (!unscored.isEmpty()) {
            return Scoring.invalid(unscoredMessage(unscored));
        }
        if (points <= 0 || parts.isEmpty()) {
            return Scoring.invalid("That selection does not score.");
        }
        if (parts.size() == 1) {
            return Scoring.ok(points, parts.get(0));
        }
        return Scoring.ok(points, String.join(" + ", parts) + " = " + format(points));
    }

    /** Three of a kind, then double for each extra matching die beyond three. */
    static int setValue(int face, int setSize) {
        int three = face == 1 ? 1000 : face * 100;
        return switch (setSize) {
            case 3 -> three;
            case 4 -> three * 2;
            case 5 -> three * 4;
            default -> throw new IllegalArgumentException("set size " + setSize);
        };
    }

    public static String format(int points) {
        return String.format(Locale.US, "%,d", points);
    }

    private static String unscoredMessage(List<Integer> unscored) {
        List<Integer> unique = new ArrayList<>();
        for (int face : unscored) {
            if (!unique.contains(face)) {
                unique.add(face);
            }
        }
        StringBuilder listed = new StringBuilder();
        for (int i = 0; i < unique.size(); i++) {
            if (i > 0 && i == unique.size() - 1) {
                listed.append(" and ");
            } else if (i > 0) {
                listed.append(", ");
            }
            listed.append(unique.get(i));
        }
        if (unique.size() == 1) {
            return listed + " does not score on its own. Hold 1s, 5s, or at least three of a kind.";
        }
        return listed + " do not score on their own. Hold 1s, 5s, or at least three of a kind.";
    }

    private static boolean isScoringFace(int face, int[] counts) {
        return face == 1 || face == 5 || counts[face] >= 3;
    }

    private static String word(int count) {
        return switch (count) {
            case 1 -> "one";
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            case 5 -> "five";
            default -> Integer.toString(count);
        };
    }
}
