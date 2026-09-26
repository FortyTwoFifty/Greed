package greed;

import java.util.Random;

public final class RandomDice implements DieSource {
    private final Random random;

    public RandomDice(Random random) {
        this.random = random;
    }

    @Override
    public int[] roll(int count) {
        if (count < 1 || count > 5) {
            throw new IllegalArgumentException("roll 1 to 5 dice, not " + count);
        }
        int[] faces = new int[count];
        for (int i = 0; i < count; i++) {
            faces[i] = random.nextInt(6) + 1;
        }
        return faces;
    }
}
