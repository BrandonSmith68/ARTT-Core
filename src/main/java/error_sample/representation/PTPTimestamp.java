package error_sample.representation;

import java.math.BigInteger;
import java.util.Arrays;

public class PTPTimestamp {
    private final byte [] ptp_timestamp;
    private final byte [] second_field;
    private final byte [] nano_field;
    private final BigInteger total_ns;

    public static final long NSEC_PER_SEC = 1_000_000_000;

    public PTPTimestamp(byte [] ptpTS) {
        ptp_timestamp = Arrays.copyOf(ptpTS, ptpTS.length);
        nano_field = Arrays.copyOfRange(ptpTS, 6, 10);
        second_field = Arrays.copyOfRange(ptpTS, 0, 6);
        total_ns = new BigInteger(second_field).multiply(BigInteger.valueOf(NSEC_PER_SEC)).add(new BigInteger(nano_field));
    }

    public BigInteger getTimestamp() {
        return total_ns;
    }

    public long getSecondsField() {
        return new BigInteger(second_field).longValue();
    }

    public long getNanosecondsField() {
        return new BigInteger(nano_field).longValue();
    }
}
