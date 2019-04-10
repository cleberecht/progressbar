package me.tongfei.progressbar;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Tongfei Chen
 * @since 0.5.0
 */
public class ProgressThread implements Runnable {

    // see https://en.wikipedia.org/wiki/ANSI_escape_code#CSI_sequences
    private static final String INITIALIZE_CSI = ((char) 0x1b) + "[";
    private static final char MOVE_TO_COLUMN = 'G';
    private static final char CLEAR_LINE = 'K';
    private static final char MOVE_UP = 'A';

    private ProgressBarStyle style;
    private ProgressState progress;
    private long updateInterval;
    private PrintStream printStream;
    private Terminal terminal;
    private int consoleWidth = 80;
    private String unitName;
    private long unitSize;
    private boolean isSpeedShown;

    private final List<BitOfInformation> bitsOfInformation;

    private int occupiedLines;

    private static int consoleRightMargin = 2;
    private static DecimalFormat speedFormat = new DecimalFormat("#.#");

    private int length;
    private ScheduledExecutorService executorService;

    ProgressThread(ProgressState progress, ProgressBarStyle style, long updateInterval, PrintStream consoleStream, String unitName, long unitSize, boolean isSpeedShown) {
        this.progress = progress;
        this.style = style;
        this.updateInterval = updateInterval;
        this.printStream = consoleStream;
        this.unitName = unitName;
        this.unitSize = unitSize;
        this.isSpeedShown = isSpeedShown;
        this.bitsOfInformation = Collections.synchronizedList(new ArrayList<>());
        try {
            // Issue #42
            // Defaulting to a dumb terminal when a supported terminal can not be correctly created
            // see https://github.com/jline/jline3/issues/291
            this.terminal = TerminalBuilder.builder().dumb(true).build();
        } catch (IOException ignored) {

        }
        // Workaround for issue #23 under IntelliJ
        if (terminal.getWidth() >= 10) {
            consoleWidth = terminal.getWidth();
        }
        occupiedLines = 1 + bitsOfInformation.size();
    }

    public PrintStream getPrintStream() {
        return printStream;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    // between 0 and 1
    private double getProgress() {
        if (progress.max <= 0) {
            return 0.0;
        }
        return ((double) progress.getCurrent()) / progress.max;
    }

    // Number of full blocks
    private int getIntegralProgress() {
        return (int) (getProgress() * length);
    }

    private int getFractionalProgress() {
        double p = getProgress() * length;
        double fraction = (p - Math.floor(p)) * style.fractionSymbols.length();
        return (int) Math.floor(fraction);
    }

    private String estimateTimeRemaining(Duration elapsed) {
        if (progress.max <= 0 || progress.indefinite) {
            return "?";
        }
        if (progress.getCurrent() == 0) {
            return "?";
        }
        return Util.formatDuration(elapsed.dividedBy(progress.getCurrent()).multipliedBy(progress.max - progress.getCurrent()));
    }

    private String getPercentageProgress() {
        String result;
        if (progress.max <= 0 || progress.indefinite) {
            result = "? %";
        } else {
            result = (int) Math.floor(100.0 * progress.getCurrent() / progress.max) + "%";
        }
        return Util.repeat(' ', 4 - result.length()) + result;
    }

    private String getRatioProgress() {
        String m = progress.indefinite ? "?" : String.valueOf(progress.max / unitSize);
        String c = String.valueOf(progress.getCurrent() / unitSize);
        return Util.repeat(' ', m.length() - c.length()) + c + "/" + m + unitName;
    }

    private String getSpeed(Duration elapsed) {
        if (elapsed.getSeconds() == 0) {
            return "?" + unitName + "/s";
        }
        double speed = (double) progress.getCurrent() / elapsed.getSeconds();
        double speedWithUnit = speed / unitSize;
        return speedFormat.format(speedWithUnit) + unitName + "/s";
    }

    public void addBitOfInformation(BitOfInformation bitOfInformation) {
        bitsOfInformation.add(bitOfInformation);
    }

    void refresh() {
        clear();

        Instant currTime = Instant.now();
        Duration elapsed = Duration.between(progress.startTime, currTime);

        String prefix = progress.task + " " + getPercentageProgress() + " " + style.leftBracket;

        int maxSuffixLength = Math.max(0, consoleWidth - consoleRightMargin - prefix.length() - 10);
        String speedString = isSpeedShown ? getSpeed(elapsed) : "";
        String suffix = style.rightBracket + " " + getRatioProgress()
                + " (" + Util.formatDuration(elapsed) + " / " + estimateTimeRemaining(elapsed) + ") "
                + speedString + progress.extraMessage;
        if (suffix.length() > maxSuffixLength) suffix = suffix.substring(0, maxSuffixLength);

        length = consoleWidth - consoleRightMargin - prefix.length() - suffix.length();

        StringBuilder sb = new StringBuilder();
        sb.append(prefix);

        // case of indefinite progress bars
        if (progress.indefinite) {
            int pos = (int) (progress.getCurrent() % length);
            sb.append(Util.repeat(style.space, pos));
            sb.append(style.block);
            sb.append(Util.repeat(style.space, length - pos - 1));
        }
        // case of definite progress bars
        else {
            sb.append(Util.repeat(style.block, getIntegralProgress()));
            if (progress.getCurrent() < progress.max) {
                sb.append(style.fractionSymbols.charAt(getFractionalProgress()));
                sb.append(Util.repeat(style.space, length - getIntegralProgress() - 1));
            }
        }

        sb.append(suffix);
        String line = sb.toString();
        printStream.println(line);
        printBits();
    }

    private void printBits() {
        int bitWidth = 0;
        // +1 for progressbar, +1 for trailing println
        occupiedLines = 2;
        for (BitOfInformation bitOfInformation : bitsOfInformation) {
            bitWidth += bitOfInformation.getLength();
            if (bitWidth > consoleWidth) {
                occupiedLines++;
                printStream.println();
                printStream.print(bitOfInformation.getBit());
                bitWidth = 0;
            } else {
                printStream.print(bitOfInformation.getBit());
                printStream.print(" ");
                bitWidth++;
            }
        }
        printStream.println();
    }

    private void clear() {
        for (int i = 0; i < occupiedLines; i++) {
            // move cursor to first column
            printStream.print(INITIALIZE_CSI + MOVE_TO_COLUMN);
            // clear line from beginning to end
            printStream.print(INITIALIZE_CSI + CLEAR_LINE);
            // move one row up
            printStream.print(INITIALIZE_CSI + MOVE_UP);
        }
    }

    public void shutdownObservation() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(updateInterval, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void shutdownTerminal() {
        // clean exit: finish last line and flush print stream
        printStream.print(INITIALIZE_CSI + MOVE_UP);
        printStream.println();
        printStream.flush();
        try {
            terminal.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println();
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::refresh, 0, updateInterval, TimeUnit.MILLISECONDS);
    }
}
