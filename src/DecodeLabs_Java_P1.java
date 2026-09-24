/*
 * ============================================================================
 *  DecodeLabs Industrial Training Kit  |  Batch 2026
 *  Java Programming - Project 1: Number Guessing Game
 *  "Engineering a Random Logic Engine"
 * ============================================================================
 *
 *  Goal:
 *    Generate a random number in a range (default 1-100) and let the user
 *    guess it, with instant HIGH / LOW feedback, until it is found.
 *
 *  Core requirements (all implemented):
 *    [x] Random number via java.util.Random        (no Math.random())
 *    [x] Zero-index shift: random.nextInt(range) + min
 *    [x] User input via java.util.Scanner
 *    [x] "Too High" / "Too Low" feedback loop
 *    [x] Live loop that runs until the win condition is met
 *
 *  Defensive engineering (all implemented):
 *    [x] Scanner Trap fixed: leftover newline is flushed with nextLine()
 *    [x] InputMismatchException handled - typing 'abc' never crashes the game
 *    [x] Range validation and closed-input (Ctrl+D / Ctrl+Z) handling
 *
 *  Optional enhancements (all implemented):
 *    [x] Attempt limiter (counter variable)
 *    [x] Score tracking + final score screen
 *    [x] Session persistence with a do-while "Play Again? [Y/N]" loop
 *    [x] Binary-search bot proving any 1-100 number is solvable in 7 guesses
 *
 *  OOP concepts demonstrated:
 *    Encapsulation  - GameRound hides the target; it is revealed only at the end
 *    Abstraction    - GuessStrategy interface hides how a guess is produced
 *    Polymorphism   - HumanGuesser and BinarySearchBot are interchangeable
 *    Enums          - Difficulty and GuessResult carry data and behavior
 *    Single Responsibility - every class has exactly one job
 *
 *  Run (Java 17+):
 *    javac -d out src/DecodeLabs_Java_P1.java && java -cp out DecodeLabs_Java_P1
 *    java src/DecodeLabs_Java_P1.java                 (single-file launch)
 *    java -cp out DecodeLabs_Java_P1 --selftest       (automated logic audit)
 * ============================================================================
 */

import java.util.HashSet;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

/** Entry point. Must stay the first class in this file. */
public class DecodeLabs_Java_P1 {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--selftest")) {
            System.exit(SelfTest.run() ? 0 : 1);
        }

        try (Scanner scanner = new Scanner(System.in)) {
            new Application(new ConsoleInput(scanner)).run();
        } catch (InputClosedException e) {
            System.out.println();
            System.out.println("Input stream closed. Goodbye!");
        }
    }
}

// ============================================================================
//  MODEL LAYER - data and rules of the game (no console I/O in here)
// ============================================================================

/** Difficulty levels. Each one carries its own range and attempt limit. */
enum Difficulty {
    EASY("Easy", 1, 50, 10),
    CLASSIC("Classic", 1, 100, 7),
    HARD("Hard", 1, 1000, 10);

    private final String label;
    private final int min;
    private final int max;
    private final int maxAttempts;

    Difficulty(String label, int min, int max, int maxAttempts) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.maxAttempts = maxAttempts;
    }

    String label() {
        return label;
    }

    int min() {
        return min;
    }

    int max() {
        return max;
    }

    int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Worst-case number of guesses a perfect binary search needs:
     * floor(log2(n)) + 1, computed with integer bit math (no floating point).
     */
    int optimalGuesses() {
        return 32 - Integer.numberOfLeadingZeros(max - min + 1);
    }

    @Override
    public String toString() {
        return String.format("%s (%d-%d, %d attempts)", label, min, max, maxAttempts);
    }
}

/** The three possible outcomes of comparing a guess with the target. */
enum GuessResult {
    TOO_LOW("Too low! Go higher."),
    TOO_HIGH("Too high! Go lower."),
    CORRECT("Correct!");

    private final String message;

    GuessResult(String message) {
        this.message = message;
    }

    String message() {
        return message;
    }
}

/** Wraps java.util.Random and applies the "zero-index shift". */
final class NumberGenerator {
    private final Random random;

    NumberGenerator() {
        this.random = new Random();
    }

    /** Seeded constructor - makes the generator reproducible for testing. */
    NumberGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Returns a uniformly random integer in [min, max] (both inclusive).
     * nextInt(n) yields 0..n-1 (computer logic), so we add min to shift it
     * into the human range (human logic).
     */
    int nextInRange(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min must not be greater than max");
        }
        return random.nextInt(max - min + 1) + min;
    }
}

/**
 * One round of the game. Owns the hidden state (the target) and enforces the
 * rules: attempt limit, range check, and "no peeking until the round ends".
 */
final class GameRound {
    private final Difficulty difficulty;
    private final int target; // hidden memory state - never exposed while playing
    private final Set<Integer> guessedNumbers = new HashSet<>();
    private int attemptsUsed;
    private int knownLow;
    private int knownHigh;
    private boolean won;

    GameRound(Difficulty difficulty, int target) {
        if (target < difficulty.min() || target > difficulty.max()) {
            throw new IllegalArgumentException("Target outside difficulty range: " + target);
        }
        this.difficulty = difficulty;
        this.target = target;
        this.knownLow = difficulty.min();
        this.knownHigh = difficulty.max();
    }

    /** Applies one guess, consumes one attempt and returns the feedback. */
    GuessResult submitGuess(int guess) {
        if (isFinished()) {
            throw new IllegalStateException("This round is already finished.");
        }
        if (guess < difficulty.min() || guess > difficulty.max()) {
            throw new IllegalArgumentException("Guess out of range: " + guess);
        }

        attemptsUsed++;
        guessedNumbers.add(guess);

        if (guess == target) {
            won = true;
            return GuessResult.CORRECT;
        }
        if (guess > target) {
            knownHigh = Math.min(knownHigh, guess - 1);
            return GuessResult.TOO_HIGH;
        }
        knownLow = Math.max(knownLow, guess + 1);
        return GuessResult.TOO_LOW;
    }

    boolean alreadyGuessed(int guess) {
        return guessedNumbers.contains(guess);
    }

    boolean isWon() {
        return won;
    }

    boolean isOutOfAttempts() {
        return attemptsUsed >= difficulty.maxAttempts();
    }

    /** A round ends on a win or when the attempts run out. */
    boolean isFinished() {
        return won || isOutOfAttempts();
    }

    int attemptsUsed() {
        return attemptsUsed;
    }

    int attemptsLeft() {
        return difficulty.maxAttempts() - attemptsUsed;
    }

    /** Lowest value the target can still have, based on the feedback so far. */
    int knownLow() {
        return knownLow;
    }

    /** Highest value the target can still have, based on the feedback so far. */
    int knownHigh() {
        return knownHigh;
    }

    Difficulty difficulty() {
        return difficulty;
    }

    /** Encapsulation in action: the answer stays hidden until the round ends. */
    int revealTarget() {
        if (!isFinished()) {
            throw new IllegalStateException("The target is hidden until the round ends.");
        }
        return target;
    }
}

/** Tracks results across the rounds of one session. */
final class ScoreBoard {
    static final int WIN_POINTS = 100;
    static final int BONUS_PER_UNUSED_ATTEMPT = 20;

    private int roundsPlayed;
    private int roundsWon;
    private int totalScore;
    private int currentStreak;
    private int bestStreak;
    private int fewestAttempts = Integer.MAX_VALUE;

    /** Records a finished round and returns the points it earned. */
    int recordRound(GameRound round) {
        if (!round.isFinished()) {
            throw new IllegalStateException("Cannot score an unfinished round.");
        }

        roundsPlayed++;
        if (!round.isWon()) {
            currentStreak = 0;
            return 0;
        }

        roundsWon++;
        currentStreak++;
        bestStreak = Math.max(bestStreak, currentStreak);
        fewestAttempts = Math.min(fewestAttempts, round.attemptsUsed());

        int points = WIN_POINTS + BONUS_PER_UNUSED_ATTEMPT * round.attemptsLeft();
        totalScore += points;
        return points;
    }

    int totalScore() {
        return totalScore;
    }

    int currentStreak() {
        return currentStreak;
    }

    void printSummary() {
        int winRate = roundsPlayed == 0 ? 0 : roundsWon * 100 / roundsPlayed;
        String best = fewestAttempts == Integer.MAX_VALUE ? "-" : String.valueOf(fewestAttempts);

        System.out.println();
        System.out.println("=============== FINAL SCORE ===============");
        System.out.printf("  Rounds played    : %d%n", roundsPlayed);
        System.out.printf("  Rounds won       : %d (%d%%)%n", roundsWon, winRate);
        System.out.printf("  Best (attempts)  : %s%n", best);
        System.out.printf("  Longest streak   : %d%n", bestStreak);
        System.out.printf("  TOTAL SCORE      : %d%n", totalScore);
        System.out.println("===========================================");
    }
}

// ============================================================================
//  STRATEGY LAYER - "who is guessing?" (abstraction + polymorphism)
// ============================================================================

/** Anything that can produce the next guess for a round. */
interface GuessStrategy {
    String name();

    int nextGuess(GameRound round);
}

/** A human at the keyboard. */
final class HumanGuesser implements GuessStrategy {
    private final ConsoleInput input;

    HumanGuesser(ConsoleInput input) {
        this.input = input;
    }

    @Override
    public String name() {
        return "You";
    }

    @Override
    public int nextGuess(GameRound round) {
        Difficulty difficulty = round.difficulty();
        while (true) {
            String prompt = String.format("Attempt %d/%d - guess (%d-%d): ",
                    round.attemptsUsed() + 1, difficulty.maxAttempts(),
                    round.knownLow(), round.knownHigh());

            int guess = input.readInt(prompt, difficulty.min(), difficulty.max());

            if (round.alreadyGuessed(guess)) {
                System.out.println("You already tried " + guess + " - no attempt used. Pick another.");
                continue;
            }
            return guess;
        }
    }
}

/**
 * A computer player that always guesses the middle of the remaining range.
 * Halving the search space every turn solves 1-100 in at most 7 guesses.
 */
final class BinarySearchBot implements GuessStrategy {
    private final boolean verbose;

    BinarySearchBot(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public String name() {
        return "Bot";
    }

    @Override
    public int nextGuess(GameRound round) {
        // low + (high - low) / 2 avoids integer overflow on huge ranges.
        int guess = round.knownLow() + (round.knownHigh() - round.knownLow()) / 2;
        if (verbose) {
            System.out.printf("Bot guesses %d (range %d-%d)%n",
                    guess, round.knownLow(), round.knownHigh());
        }
        return guess;
    }
}

// ============================================================================
//  I/O LAYER - all Scanner handling lives here
// ============================================================================

/** Thrown when the input stream ends (e.g. Ctrl+D) so the game can exit cleanly. */
final class InputClosedException extends RuntimeException {
    InputClosedException() {
        super("Input stream closed.");
    }
}

/** Crash-proof console input: validates, flushes the buffer, never throws on bad text. */
final class ConsoleInput {
    private final Scanner scanner;

    ConsoleInput(Scanner scanner) {
        this.scanner = scanner;
    }

    /** Keeps asking until the user types an integer within [min, max]. */
    int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = scanner.nextInt();
                discardRestOfLine(); // Scanner Trap fix: flush the leftover newline

                if (value < min || value > max) {
                    System.out.printf("Out of range. Enter a number between %d and %d.%n", min, max);
                    continue;
                }
                return value;
            } catch (InputMismatchException e) {
                discardRestOfLine(); // throw away the invalid token, e.g. "abc"
                System.out.printf("Invalid input. Please enter a whole number (%d-%d).%n", min, max);
            } catch (NoSuchElementException e) {
                throw new InputClosedException();
            }
        }
    }

    /** Keeps asking until the user answers yes or no. */
    boolean readYesNo(String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                throw new InputClosedException();
            }
            String answer = scanner.nextLine().trim().toLowerCase();

            if (answer.equals("y") || answer.equals("yes")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("no")) {
                return false;
            }
            System.out.println("Please type Y or N.");
        }
    }

    private void discardRestOfLine() {
        if (scanner.hasNextLine()) {
            scanner.nextLine();
        }
    }
}

// ============================================================================
//  GAME ENGINE - the live loop
// ============================================================================

/** Runs rounds and sessions for any GuessStrategy. */
final class NumberGame {
    private final Difficulty difficulty;
    private final NumberGenerator generator;
    private final GuessStrategy player;
    private final ConsoleInput input;
    private final ScoreBoard scoreBoard = new ScoreBoard();

    NumberGame(Difficulty difficulty, NumberGenerator generator,
               GuessStrategy player, ConsoleInput input) {
        this.difficulty = difficulty;
        this.generator = generator;
        this.player = player;
        this.input = input;
    }

    /** Session persistence: do-while guarantees at least one round is played. */
    void runSession() {
        boolean playAgain;
        do {
            GameRound round = playRound();
            int points = scoreBoard.recordRound(round);

            if (round.isWon()) {
                System.out.printf("Points earned: +%d | Total score: %d | Win streak: %d%n",
                        points, scoreBoard.totalScore(), scoreBoard.currentStreak());
            } else {
                System.out.printf("No points this round | Total score: %d%n", scoreBoard.totalScore());
            }
            playAgain = input.readYesNo(System.lineSeparator() + "Play again? [Y/N]: ");
        } while (playAgain);

        scoreBoard.printSummary();
    }

    /** One complete round: generate -> loop (input, compare, feedback) -> outcome. */
    GameRound playRound() {
        int target = generator.nextInRange(difficulty.min(), difficulty.max());
        GameRound round = new GameRound(difficulty, target);

        System.out.println();
        System.out.println("--------------------------------------------");
        System.out.printf("New round - %s%n", difficulty);
        System.out.printf("I'm thinking of a number between %d and %d.%n",
                difficulty.min(), difficulty.max());
        System.out.println("--------------------------------------------");

        while (!round.isFinished()) { // the live loop: runs until the win/lose state
            int guess = player.nextGuess(round);
            GuessResult result = round.submitGuess(guess);

            if (result != GuessResult.CORRECT) {
                System.out.println(result.message());
            }
        }

        announceOutcome(round);
        return round;
    }

    private void announceOutcome(GameRound round) {
        System.out.println();
        if (round.isWon()) {
            System.out.printf("Correct! %s found %d in %d attempt(s).%n",
                    player.name(), round.revealTarget(), round.attemptsUsed());

            int optimal = difficulty.optimalGuesses();
            if (round.attemptsUsed() <= optimal) {
                System.out.printf("Efficiency: binary-search level (optimal worst case is %d).%n", optimal);
            } else {
                System.out.printf("Pro tip: always guess the middle of the range - "
                        + "that solves any number in %d guesses or fewer.%n", optimal);
            }
        } else {
            System.out.printf("Out of attempts! The number was %d.%n", round.revealTarget());
        }
    }
}

// ============================================================================
//  APPLICATION - menus and navigation
// ============================================================================

/** Main menu and screens. */
final class Application {
    private final ConsoleInput input;

    Application(ConsoleInput input) {
        this.input = input;
    }

    void run() {
        printBanner();

        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("MAIN MENU");
            System.out.println("  1) Play the Number Game");
            System.out.println("  2) Watch the binary-search bot play");
            System.out.println("  3) How to play");
            System.out.println("  4) Exit");

            int choice = input.readInt("Choose an option (1-4): ", 1, 4);
            switch (choice) {
                case 1 -> startGame();
                case 2 -> watchBot();
                case 3 -> printHelp();
                default -> running = false;
            }
        }
        System.out.println("Thanks for playing. Keep building!");
    }

    private void startGame() {
        Difficulty[] levels = Difficulty.values();

        System.out.println();
        System.out.println("SELECT DIFFICULTY");
        for (int i = 0; i < levels.length; i++) {
            System.out.printf("  %d) %s%n", i + 1, levels[i]);
        }

        int pick = input.readInt("Select difficulty (1-" + levels.length + "): ", 1, levels.length);
        Difficulty chosen = levels[pick - 1];

        new NumberGame(chosen, new NumberGenerator(), new HumanGuesser(input), input).runSession();
    }

    private void watchBot() {
        System.out.println();
        System.out.println("Watch a perfect binary search solve the Classic (1-100) game.");
        new NumberGame(Difficulty.CLASSIC, new NumberGenerator(),
                new BinarySearchBot(true), input).playRound();
    }

    private void printBanner() {
        System.out.println("============================================");
        System.out.println("   DECODELABS  |  JAVA PROGRAMMING  |  P1");
        System.out.println("        THE NUMBER GUESSING GAME");
        System.out.println("============================================");
    }

    private void printHelp() {
        System.out.println();
        System.out.println("HOW TO PLAY");
        System.out.println("  - The computer picks a hidden random number.");
        System.out.println("  - Type your guess and press Enter.");
        System.out.println("  - You get 'Too high' or 'Too low' after each guess.");
        System.out.println("  - Find the number before you run out of attempts.");
        System.out.printf("  - Score: %d points per win + %d per unused attempt.%n",
                ScoreBoard.WIN_POINTS, ScoreBoard.BONUS_PER_UNUSED_ATTEMPT);
        System.out.println("  - Repeating a guess costs nothing.");
    }
}

// ============================================================================
//  SELF TEST - the "Logic Audit" (run with --selftest, used by GitHub Actions)
// ============================================================================

/** Dependency-free automated checks of the game's core logic. */
final class SelfTest {
    private static int failures;

    static boolean run() {
        failures = 0;
        System.out.println("DecodeLabs Java P1 - Logic Audit");
        System.out.println("--------------------------------");

        testGenerator();
        testFeedbackAndHiddenState();
        testAttemptLimit();
        testScoring();
        testBinarySearchBot();

        System.out.println("--------------------------------");
        System.out.println(failures == 0 ? "LOGIC AUDIT PASSED" : failures + " CHECK(S) FAILED");
        return failures == 0;
    }

    private static void check(String name, boolean condition) {
        System.out.println((condition ? "[PASS] " : "[FAIL] ") + name);
        if (!condition) {
            failures++;
        }
    }

    private static void testGenerator() {
        for (Difficulty d : Difficulty.values()) {
            NumberGenerator generator = new NumberGenerator(42L);
            boolean inRange = true;
            boolean sawMin = false;
            boolean sawMax = false;

            for (int i = 0; i < 100_000; i++) {
                int value = generator.nextInRange(d.min(), d.max());
                inRange &= value >= d.min() && value <= d.max();
                sawMin |= value == d.min();
                sawMax |= value == d.max();
            }
            check("Generator stays in " + d.min() + "-" + d.max() + " and reaches both bounds",
                    inRange && sawMin && sawMax);
        }
    }

    private static void testFeedbackAndHiddenState() {
        GameRound round = new GameRound(Difficulty.CLASSIC, 42);

        boolean hidden = false;
        try {
            round.revealTarget();
        } catch (IllegalStateException e) {
            hidden = true;
        }
        check("Target stays hidden while the round is active", hidden);
        check("Guess 10 -> TOO_LOW", round.submitGuess(10) == GuessResult.TOO_LOW);
        check("Guess 90 -> TOO_HIGH", round.submitGuess(90) == GuessResult.TOO_HIGH);
        check("Known range narrows to 11-89", round.knownLow() == 11 && round.knownHigh() == 89);
        check("Guess 42 -> CORRECT and round is won",
                round.submitGuess(42) == GuessResult.CORRECT && round.isWon());
        check("Target is revealed after the round ends", round.revealTarget() == 42);

        boolean rejected = false;
        try {
            new GameRound(Difficulty.CLASSIC, 50).submitGuess(101);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check("Out-of-range guess is rejected", rejected);
    }

    private static void testAttemptLimit() {
        GameRound round = new GameRound(Difficulty.CLASSIC, 100);
        for (int guess = 1; guess <= Difficulty.CLASSIC.maxAttempts(); guess++) {
            round.submitGuess(guess);
        }
        check("Round ends when attempts run out", round.isFinished() && !round.isWon());
        check("Attempts left is zero", round.attemptsLeft() == 0);
    }

    private static void testScoring() {
        ScoreBoard board = new ScoreBoard();

        GameRound perfect = new GameRound(Difficulty.CLASSIC, 42);
        perfect.submitGuess(42);
        int expected = ScoreBoard.WIN_POINTS
                + ScoreBoard.BONUS_PER_UNUSED_ATTEMPT * (Difficulty.CLASSIC.maxAttempts() - 1);
        check("First-try win scores " + expected, board.recordRound(perfect) == expected);

        GameRound lost = new GameRound(Difficulty.CLASSIC, 100);
        for (int guess = 1; guess <= Difficulty.CLASSIC.maxAttempts(); guess++) {
            lost.submitGuess(guess);
        }
        check("Lost round scores 0 and resets the streak",
                board.recordRound(lost) == 0 && board.currentStreak() == 0);
        check("Total score is kept across rounds", board.totalScore() == expected);
    }

    private static void testBinarySearchBot() {
        BinarySearchBot bot = new BinarySearchBot(false);

        for (Difficulty d : Difficulty.values()) {
            boolean allSolved = true;
            int worstCase = 0;

            for (int target = d.min(); target <= d.max(); target++) {
                GameRound round = new GameRound(d, target);
                while (!round.isFinished()) {
                    round.submitGuess(bot.nextGuess(round));
                }
                allSolved &= round.isWon();
                worstCase = Math.max(worstCase, round.attemptsUsed());
            }
            check(String.format("Bot solves every number in %d-%d (worst case %d guesses)",
                            d.min(), d.max(), worstCase),
                    allSolved && worstCase <= d.optimalGuesses() && worstCase <= d.maxAttempts());
        }
    }
}