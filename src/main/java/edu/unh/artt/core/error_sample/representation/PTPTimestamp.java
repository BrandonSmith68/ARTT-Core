package edu.unh.artt.core.error_sample.representation;

import java.math.BigInteger;
import java.util.Arrays;

public class PTPTimestamp {
    private final long seconds;
    private final long nanoseconds;
    private final long total_ns;

    public static final long NSEC_PER_SEC = 1_000_000_000;

    public PTPTimestamp(byte [] ptpTS) {
        nanoseconds = new BigInteger(Arrays.copyOfRange(ptpTS, 6, 10)).longValue();
        seconds = new BigInteger(Arrays.copyOfRange(ptpTS, 0, 6)).longValue();
        total_ns = seconds * NSEC_PER_SEC + nanoseconds;
    }

    public PTPTimestamp(long ptpTS) {
        seconds = ptpTS / NSEC_PER_SEC;
        nanoseconds = ptpTS % NSEC_PER_SEC;
        total_ns = ptpTS;
    }

    public long getTimestamp() {
        return total_ns;
    }

    public long getSecondsField() {
        return seconds;
    }

    public long getNanosecondsField() {
        return nanoseconds;
    }
}
