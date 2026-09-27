# Greed

A terminal dice game for up to 10 players — human vs. computer opponents. Built with Java and a full-screen TUI.

## Gameplay

The goal is to be the first to reach 10,000 points. Each turn you roll up to five dice and try to pick scoring dice to bank points.

### The dice

You start each hand with up to 5 dice. As you hold scoring dice and re-roll, fewer dice remain to roll each round until you choose to bank or run out of dice.

### Scoring dice

When you hold a set of dice, the scoring rules are:

- **1s** = 100 points each
- **5s** = 50 points each
- **Three of a kind** = face value x 100 (three 1s = 1,000)
- **Four of a kind** = twice the three-of-a-kind value
- **Five of a kind** = twice the four-of-a-kind value
- **Extra 1s and 5s** beyond a set still score individually

A selection is legal only when every die either scores or is a 1/5 leftover from a set. Any unscored face (like a stray 2, 3, 4, or 6) makes the selection invalid.

### Turn flow

1. Roll up to 5 dice.
2. Press 1-5 to toggle which dice you want to hold, or press **a** to auto-hold everything that scores.
3. Press Enter to confirm your hold. If nothing scores, you bust and lose the hand's accumulated points.
4. If you score at least one die, you get a choice: **bank** your points (adding them to your total) or **roll again** with the remaining dice, building on your current holding.
5. Your dice get smaller as you keep holding — you might roll just 2 dice at the end of a long hand.

### Rules

- **Opening score:** A new player must bank at least 750 points in their first hand before they're "on the board." Until then, banking is forced — you must keep rolling.
- **Hot dice:** If you score all five dice in a single roll, you get a fresh set of 5 dice to add to your hand. You cannot bank on that roll.
- **Continuing hands:** After a player banks, the leftover dice they chose not to score pass to the next player. They start a new hand at 0 with only those dice and can choose to continue with them or roll 5 fresh dice.
- **Double re-roll:** If only 2 dice remain and they form a non-scoring double (2-2, 3-3, 4-4, or 6-6), the player gets an extra re-roll of just those 2 dice.
- **Bust:** If you roll and can't score any die, you bust. You lose all points accumulated in that hand. If the hand had 0 points, you still lose 100 from your total.

The defaults are 10,000 to win and an opening bank of 750. `./run.sh --board` and `--winning` change the opening bank and the winning total. `--board` must be a positive whole number strictly below `--winning`.

### Players

Choose 1-10 seats at setup, then how many of those seats are computer players.
You name the rest. Type **bot** as a name to seat a computer in that seat.
Computers are Rook, Rook 2, and so on. One human sits with Rook.
A computer's Cautious, Steady, or Bold style is hidden unless you start with `--dev`.

### Controls (TUI)

| Key | Action |
|-----|--------|
| 1-5 | Toggle a die as held |
| a | Auto-hold all scoring dice |
| Enter | Confirm |
| r | Roll |
| b | Bank |
| c | Continue with opponent's leftover dice |
| n | Start a new hand with 5 dice |
| ? | Show rules |
| q | Quit |
| Space | Skip ahead during bot turns |

### Line transcript mode

Run with `--plain` to play in a line-by-line transcript mode instead of the full-screen TUI.

## Running

```bash
./run.sh              # TUI mode
./run.sh --plain      # Line transcript mode
./run.sh --no-color   # Disable color
./run.sh --dev                         # show each computer's Cautious, Steady, or Bold
./run.sh --board 300 --winning 2000    # opening bank 300, first to 2,000
./test.sh             # Compile and run the three test mains
```

## Configuration

| Environment | Effect |
|-------------|--------|
| `NO_COLOR` | Disables color and animation |

The command line overrides the scores for that game. The defaults stay `WINNING_SCORE` = 10,000 and `OPENING_SCORE` = 750. `--board` and `--winning` take the next argument (`300`, `2000`, not `10_000`). `--board=300` is not a supported form.

You can also set custom scores when constructing a Game object programmatically. The class constants define the defaults:

- `WINNING_SCORE` = 10,000
- `OPENING_SCORE` = 750

## Project structure

```
src/main/java/greed/
  Main.java                          # package greed
  rules/
    Game.java  Options.java  Scorer.java  DieSource.java  RandomDice.java
    Player.java  Bot.java  View.java  Quit.java
  view/
    LineView.java  TuiView.java  Frame.java  Paint.java  Snapshot.java  Terminal.java
src/test/java/greed/
  rules/GreedTest.java               # package greed.rules
  view/TuiTest.java                  # package greed.view
  view/HotDiceTest.java              # package greed.view
run.sh                               # play
test.sh                              # compile everything, run the 3 mains
```
