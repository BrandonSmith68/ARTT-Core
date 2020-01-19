package edu.unh.artt.core.error_sample.representation;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Represents a PTP timestamp with nanosecond precision. Sub-nanosecond precision is not supported by PTP timestamps,
 * but there are some helper methods here for sub-nanosecond timestamps.
 */
public class PTPTimestamp {
    private final long seconds;
    private final long nanoseconds;
    private final long total_ns;

    public static final long NSEC_PER_SEC = 1_000_000_000;

    /* Scaled nanoseconds: incorporates 2 bytes of sub-nanosecond precision into the transmitted value*/
    public static final double SCALED_NS_CONVERSION = 2 << 15;

    /**
     * @param subns Fractional nanoseconds
     * @return Scaled value shifted by two bytes
     */
    public static long toScaledNs(double subns) {
        return Math.round(subns * SCALED_NS_CONVERSION);
    }

    /**
     * @param scaledNs Scaled nanoseconds
     * @return Fractional nanoseconds. First two east significant bytes are sub-nanosecond
     */
    public static double fromScaledNs(long scaledNs) {
        return scaledNs / SCALED_NS_CONVERSION;
    }

    /**
     * @param ptpTS Parses an IEEE-1588 PTP timestamp (48-bit nanosecond timestamp)
     */
    public PTPTimestamp(byte [] ptpTS) {
        nanoseconds = new BigInteger(Arrays.copyOfRange(ptpTS, 6, 10)).longValue();
        seconds = new BigInteger(Arrays.copyOfRange(ptpTS, 0, 6)).longValue();
        total_ns = seconds * NSEC_PER_SEC + nanoseconds;
    }

    /**
     * @param ptpTS Timestamp with nanosecond precision
     */
    public PTPTimestamp(long ptpTS) {
        seconds = ptpTS / NSEC_PER_SEC;
        nanoseconds = ptpTS % NSEC_PER_SEC;
        total_ns = ptpTS;
    }

    /**
     * @return Timestamp in nanoseconds
     */
    public long getTimestamp() {
        return total_ns;
    }

    /**
     * @return Seconds portion of the PTP timestamp
     */
    public long getSecondsField() {
        return seconds;
    }

    /**
     * @return Nanoseconds portion of the PTP timestamp
     */
    public long getNanosecondsField() {
        return nanoseconds;
    }
}
