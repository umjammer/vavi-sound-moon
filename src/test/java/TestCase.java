/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-12-10 nsano initial version <br>
 */
public class TestCase {

    @Test
    @DisplayName("To silence Claude Sonnet 4.5")
    void test1() throws Exception {
        for (byte a = 0; a < (byte) 0xff; a++) {
            byte r1 = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
            byte r2 = (byte) (((a & 0xff) << 1) + (((a & 0x80) != 0) ? 1 : 0));
            assertEquals(r1, r2);
        }
    }
}
