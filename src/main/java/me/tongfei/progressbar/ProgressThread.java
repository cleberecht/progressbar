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

/**
 * @author Tongfei Chen
 * @since 0.5.0
 */
class ProgressThread implements Runnable {

    public static final String INITIALIZE_CSI = ((char) 0x1b) + "[";

    // see https://en.wikipedia.org/wiki/ANSI_escape_code#CSI_sequences
    public static final char MOVE_TO_COLUMN = 'G';
    public static final char CLEAR_LINE = 'K';
    public static final char MOVE_UP = 'A';


    volatile boolean killed;
    ProgressBarStyle style;
    ProgressState progress;
    long updateInterval;
    PrintStream consoleStream;
    Terminal terminal;
    int consoleWidth = 80;
    String unitName = "";
    long unitSize = 1;
    boolean isSpeedShown;

    private final List<BitOfInformation> bitsOfInformation;

    private int occupiedLines;

    private static int consoleRightMargin = 2;
    private static DecimalFormat speedFormat = new DecimalFormat("#.#");

    private int length;

    ProgressThread(
            ProgressState progress,
            ProgressBarStyle style,
            long updateInterval,
            PrintStream consoleStream,
            String unitName,
            long unitSize,
            boolean isSpeedShown
    ) {
        this.progress = progress;
        this.style = style;
        this.updateInterval = updateInterval;
        this.consoleStream = consoleStream;
        this.killed = false;
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

    // between 0 and 1
    double progress() {
        if (progress.max <= 0) return 0.0;
        else return ((double) progress.current) / progress.max;
    }

    // Number of full blocks
    int progressIntegralPart() {
        return (int) (progress() * length);
    }

    int progressFractionalPart() {
        double p = progress() * length;
        double fraction = (p - Math.floor(p)) * style.fractionSymbols.length();
        return (int) Math.floor(fraction);
    }

    String eta(Duration elapsed) {
        if (progress.max <= 0 || progress.indefinite) return "?";
        else if (progress.current == 0) return "?";
        else return Util.formatDuration(
                    elapsed.dividedBy(progress.current)
                            .multipliedBy(progress.max - progress.current)
            );
    }

    String percentage() {
        String res;
        if (progress.max <= 0 || progress.indefinite) res = "? %";
        else res = String.valueOf((int) Math.floor(100.0 * progress.current / progress.max)) + "%";
        return Util.repeat(' ', 4 - res.length()) + res;
    }

    String ratio() {
        String m = progress.indefinite ? "?" : String.valueOf(progress.max / unitSize);
        String c = String.valueOf(progress.current / unitSize);
        return Util.repeat(' ', m.length() - c.length()) + c + "/" + m + unitName;
    }

    String speed(Duration elapsed) {
        if (elapsed.getSeconds() == 0) return "?" + unitName + "/s";
        double speed = (double) progress.current / elapsed.getSeconds();
        double speedWithUnit = speed / unitSize;
        return speedFormat.format(speedWithUnit) + unitName + "/s";
    }

    public void addBitsOfInformation(BitOfInformation bitOfInformation) {
        bitsOfInformation.add(bitOfInformation);
    }

    void refresh() {
        clear();

        Instant currTime = Instant.now();
        Duration elapsed = Duration.between(progress.startTime, currTime);

        String prefix = progress.task + " " + percentage() + " " + style.leftBracket;

        int maxSuffixLength = Math.max(0, consoleWidth - consoleRightMargin - prefix.length() - 10);
        String speedString = isSpeedShown ? speed(elapsed) : "";
        String suffix = style.rightBracket + " " + ratio()
                + " (" + Util.formatDuration(elapsed) + " / " + eta(elapsed) + ") "
                + speedString + progress.extraMessage;
        if (suffix.length() > maxSuffixLength) suffix = suffix.substring(0, maxSuffixLength);

        length = consoleWidth - consoleRightMargin - prefix.length() - suffix.length();

        StringBuilder sb = new StringBuilder();
        sb.append(prefix);

        // case of indefinite progress bars
        if (progress.indefinite) {
            int pos = (int) (progress.current % length);
            sb.append(Util.repeat(style.space, pos));
            sb.append(style.block);
            sb.append(Util.repeat(style.space, length - pos - 1));
        }
        // case of definite progress bars
        else {
            sb.append(Util.repeat(style.block, progressIntegralPart()));
            if (progress.current < progress.max) {
                sb.append(style.fractionSymbols.charAt(progressFractionalPart()));
                sb.append(Util.repeat(style.space, length - progressIntegralPart() - 1));
            }
        }

        sb.append(suffix);
        String line = sb.toString();
        consoleStream.println(line);
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
                consoleStream.println();
                consoleStream.print(bitOfInformation.getBit());
                bitWidth = 0;
            } else {
                consoleStream.print(bitOfInformation.getBit());
                consoleStream.print(" ");
                bitWidth++;
            }
        }
        consoleStream.println();
    }

    private void clear() {
        for (int i = 0; i < occupiedLines; i++) {
            // move cursor to first column
            consoleStream.print(INITIALIZE_CSI + MOVE_TO_COLUMN);
            // clear line from beginning to end
            consoleStream.print(INITIALIZE_CSI + CLEAR_LINE);
            // move one row up
            consoleStream.print(INITIALIZE_CSI + MOVE_UP);
        }
    }

    void kill() {
        killed = true;
    }

    public void run() {
        try {
            consoleStream.println();
            while (!killed) {
                refresh();
                Thread.sleep(updateInterval);
            }
            refresh();
            // do-while loop not right: must force to refresh after stopped
        } catch (InterruptedException ignored) {
        }
    }
}
