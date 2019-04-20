/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.rng.core.util;

import org.apache.commons.math3.util.Precision;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;

/**
 * Tests for the {@link NumberFactory}.
 */
public class NumberFactoryTest {
    /** sizeof(int). */
    final int INT_SIZE = 4;
    /** sizeof(long). */
    final int LONG_SIZE = 8;

    /** Test values. */
    private static final long[] LONG_TEST_VALUES = new long[] { 0L, 1L, -1L, 19337L, 1234567891011213L,
            -11109876543211L, Long.valueOf(Integer.MAX_VALUE), Long.valueOf(Integer.MIN_VALUE), Long.MAX_VALUE,
            Long.MIN_VALUE, };
    /** Test values. */
    private static final int[] INT_TEST_VALUES = new int[] { 0, 1, -1, 19337, 1234567891, -1110987656,
            Integer.MAX_VALUE, Integer.MIN_VALUE, };

    @Test
    public void testMakeIntFromLong() {
        for (long v : LONG_TEST_VALUES) {
            final int vL = NumberFactory.extractLo(v);
            final int vH = NumberFactory.extractHi(v);

            final long actual = (((long) vH) << 32) | (vL & 0xffffffffL);
            Assert.assertEquals(v, actual);
        }
    }

    @Test
    public void testLong2Long() {
        for (long v : LONG_TEST_VALUES) {
            final int vL = NumberFactory.extractLo(v);
            final int vH = NumberFactory.extractHi(v);

            Assert.assertEquals(v, NumberFactory.makeLong(vH, vL));
        }
    }

    @Test
    public void testLongFromByteArray2Long() {
        for (long expected : LONG_TEST_VALUES) {
            final byte[] b = NumberFactory.makeByteArray(expected);
            Assert.assertEquals(expected, NumberFactory.makeLong(b));
        }
    }

    @Test
    public void testLongArrayFromByteArray2LongArray() {
        final byte[] b = NumberFactory.makeByteArray(LONG_TEST_VALUES);
        Assert.assertArrayEquals(LONG_TEST_VALUES, NumberFactory.makeLongArray(b));
    }

    @Test
    public void testIntFromByteArray2Int() {
        for (int expected : INT_TEST_VALUES) {
            final byte[] b = NumberFactory.makeByteArray(expected);
            Assert.assertEquals(expected, NumberFactory.makeInt(b));
        }
    }

    @Test
    public void testIntArrayFromByteArray2IntArray() {
        final byte[] b = NumberFactory.makeByteArray(INT_TEST_VALUES);
        Assert.assertArrayEquals(INT_TEST_VALUES, NumberFactory.makeIntArray(b));
    }

    @Test
    public void testMakeIntPrecondition1() {
        for (int i = 0; i <= 10; i++) {
            try {
                NumberFactory.makeInt(new byte[i]);
                if (i != INT_SIZE) {
                    Assert.fail("Exception expected");
                }
            } catch (IllegalArgumentException e) {
                // Expected.
            }
        }
    }

    @Test
    public void testMakeIntArrayPrecondition1() {
        for (int i = 0; i <= 20; i++) {
            try {
                NumberFactory.makeIntArray(new byte[i]);
                if (i != 0 && (i % INT_SIZE != 0)) {
                    Assert.fail("Exception expected");
                }
            } catch (IllegalArgumentException e) {
                // Expected.
            }
        }
    }

    @Test
    public void testMakeLongPrecondition1() {
        for (int i = 0; i <= 10; i++) {
            try {
                NumberFactory.makeLong(new byte[i]);
                if (i != LONG_SIZE) {
                    Assert.fail("Exception expected");
                }
            } catch (IllegalArgumentException e) {
                // Expected.
            }
        }
    }

    @Test
    public void testMakeLongArrayPrecondition1() {
        for (int i = 0; i <= 20; i++) {
            try {
                NumberFactory.makeLongArray(new byte[i]);
                if (i != 0 && (i % LONG_SIZE != 0)) {
                    Assert.fail("Exception expected");
                }
            } catch (IllegalArgumentException e) {
                // Expected.
            }
        }
    }

    /**
     * Test different methods for generation of a {@code float} from a {@code int}. The output
     * value should be in the range between 0 and 1.
     */
    @Test
    public void testFloatGenerationMethods() {
        final int allBits = 0xffffffff;

        // Not capable of generating 1. Set the delta with 1 or 2 ULP of 1.
        assertCloseToNotAbove1((allBits >>> 9) * 0x1.0p-23f, 2);
        assertCloseToNotAbove1((allBits >>> 8) * 0x1.0p-24f, 1);
        assertCloseToNotAbove1(Float.intBitsToFloat(0x7f << 23 | allBits >>> 9) - 1.0f, 2);

        final int noBits = 0;
        Assert.assertEquals(0.0f, (noBits >>> 9) * 0x1.0p-23f, 0);
        Assert.assertEquals(0.0f, (noBits >>> 8) * 0x1.0p-24f, 0);
        Assert.assertEquals(0.0f, Float.intBitsToFloat(0x7f << 23 | noBits >>> 9) - 1.0f, 0);
    }

    /**
     * Test different methods for generation of a {@code double} from a {@code long}. The output
     * value should be in the range between 0 and 1.
     */
    @Test
    public void testDoubleGenerationMethods() {
        final long allBits = 0xffffffffffffffffL;

        // Not capable of generating 1. Set the delta with 1 or 2 ULP of 1.
        assertCloseToNotAbove1((allBits >>> 12) * 0x1.0p-52d, 2);
        assertCloseToNotAbove1((allBits >>> 11) * 0x1.0p-53d, 1);
        assertCloseToNotAbove1(Double.longBitsToDouble(0x3ffL << 52 | allBits >>> 12) - 1.0, 2);

        final long noBits = 0;
        Assert.assertEquals(0.0, (noBits >>> 12) * 0x1.0p-52d, 0);
        Assert.assertEquals(0.0, (noBits >>> 11) * 0x1.0p-53d, 0);
        Assert.assertEquals(0.0, Double.longBitsToDouble(0x3ffL << 52 | noBits >>> 12) - 1.0, 0);
    }

    @Test
    public void testMakeDoubleFromLong() {
        final long allBits = 0xffffffffffffffffL;
        final long noBits = 0;
        // Within 1 ULP of 1.0
        assertCloseToNotAbove1(NumberFactory.makeDouble(allBits), 1);
        Assert.assertEquals(0.0, NumberFactory.makeDouble(noBits), 0);
    }

    @Test
    public void testMakeDoubleFromIntInt() {
        final int allBits = 0xffffffff;
        final int noBits = 0;
        // Within 1 ULP of 1.0
        assertCloseToNotAbove1(NumberFactory.makeDouble(allBits, allBits), 1);
        Assert.assertEquals(0.0, NumberFactory.makeDouble(noBits, noBits), 0);
    }

    @Test
    public void testMakeFloatFromInt() {
        final int allBits = 0xffffffff;
        final int noBits = 0;
        // Within 1 ULP of 1.0f
        assertCloseToNotAbove1(NumberFactory.makeFloat(allBits), 1);
        Assert.assertEquals(0.0f, NumberFactory.makeFloat(noBits), 0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMakeIntInRangeWithRangeZeroThrows() {
        NumberFactory.makeIntInRange(0, 0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMakeIntInRangeWithNegativeRangeThrows() {
        NumberFactory.makeIntInRange(0, -1);
    }

    @Test
    public void testMakeIntInRange() {
        final int allBits = 0xffffffff;
        final int noBits = 0;
        for (int i = 0; i < 31; i++) {
            final int n = 1 << i;
            Assert.assertEquals(0, NumberFactory.makeIntInRange(noBits, n));
            assertMakeIntInRange(allBits, n);
        }
        assertMakeIntInRange(allBits, Integer.MAX_VALUE);
        for (int i = 1; i <= 31; i++) {
            assertMakeIntInRange(allBits << i, Integer.MAX_VALUE);
        }

        // Check some random values
        final Random rng = new Random();
        for (int i = 0; i < 31; i++) {
            final int n = 1 << i;
            assertMakeIntInRange(rng.nextInt(), n);
        }
        for (int i = 0; i < 100; i++) {
            assertMakeIntInRange(rng.nextInt(), rng.nextInt(Integer.MAX_VALUE));
        }
    }

    @Test
    public void testBias() {
//        BigInteger max = BigInteger.ONE.shiftLeft(32);
//        BigDecimal max2 = new BigDecimal(max);
//        for (int i = 1; i < 32; i++) {
//            long n = (1L << i) + 1;
//            BigInteger value = BigInteger.valueOf(n);
//            BigInteger[] result = max.divideAndRemainder(value);
//            BigDecimal bd1 = new BigDecimal(value).divide(max2, 10, RoundingMode.HALF_UP);
//            BigDecimal bd2 = new BigDecimal(result[1]).divide(max2, 10, RoundingMode.HALF_UP);
//            System.out.printf("|%d|%s|%s|%s|%s|%s|%n", n, result[0], result[1],
//                    //new BigDecimal(result[1]).divide(max2, 10, RoundingMode.HALF_UP)
//                    bd1,
//                    bd2,
//                    //bd1.subtract(bd2).divide(bd1, 10, RoundingMode.HALF_UP)
//                    new BigDecimal(result[1]).divide(new BigDecimal(value), 10, RoundingMode.HALF_UP)
//                    );
//        }

        // If remainder is >= (range-extras) then reject it?
        // these are samples that are over represented.

        //int nn = 257;
        //final int fence = (int)((0x80000000L / nn) * nn);
        //System.out.printf("fence = %d%n", fence);
        //System.out.printf("fence = %d%n", (0x80000000 / nn) * -nn);
        
        int range = 16; // 2^4
        for (int n = 1; n <= range; n++) {
            // Output number of samples expected for each value in range [0,n)
            int numberOfSamples = range / n;
            // Output number of extra samples. These must be rejected.
            int extra = range % n;
            
            // frequency * numberOfSamples + frequency1 * (numberOfSamples + 1) = range

            // Frequency each number is seen (number of samples) times
            int frequency = n - extra;
            // Frequency each number is seen (number of samples + 1) times
            int frequency1 = extra;
            
            
            // Output rejection rate.
            double rejectionProbability = (double) extra / range;
            // Output bias (mean and variance of number of samples) if not rejected.
            double mean = (double) range / n;
            double var  = 0;
            if (extra != 0) {
                double dx = mean - numberOfSamples;
                double sum = frequency * dx * dx;
                dx = 1 - dx;
                sum += frequency1 * dx * dx;
                var = sum / n;
            }
            
            // Output limit for rejection if using modulus operator
            // Output limit for rejection on remainder if using multiply, divide and remainder.

            System.out.printf("[n=%d/%d] %d*%d/%d, %d*%d/%d (%.3f +/- %.3f) %f%n",
                    n, range,
                    frequency, numberOfSamples, range,
                    frequency1, numberOfSamples + 1, range,
                    mean, var, rejectionProbability);

            
            
            
            // q. How to explain the rejection level on the remainder. It works to use
            // the extra samples. this will never exceed n and so if n is bounded to be positive
            // rejection can be signed.
            // the rejection level is a fraction [extra] / 2^b. Any remainder within this fraction
            // is rejected.
            
            
            System.out.printf("[n=%d/%d] samples %d (%.3f), extra %d / %d, fence=%d  %d  %d  %d  %d, reject fence %d %d%n", 
                    n, range, 
                    numberOfSamples, (double)range / n, 
                    // extra method to use: range is a power of 2.
                    // can be supported using long but for long requires big integer.
                    extra,
                    n,
                    // val = bits % n
                    // bits - val + (n - 1) < 0
                    // bits < val - n + 1
                    range - (range % n),
                    n & -n,
                    n & (n-1),
                    // This is the fence method to use:
                    (range / n) * n,
                    ((range / n) * n) << 1,
                    // Better way to compute reject fence avoiding modulus?
                    // Or do it same way as the while loop:
                    // bits - val + (n - 1) < 0
                    // If the remainder + value > range then reject.
                    range - range % n,
                    (range-1) - ((range-1) / n) * n
                    );
            int[] h = new int[n];
            for (int i = 0; i < range; i++) {
                int sample = (n * i) / range;
                h[sample]++;
                System.out.printf("%d %d %% %d%n", i, sample, (n * i) & (range-1));
            }
            int min = h[0];
            int max = min;
            for (int i = 0; i < n; i++) {
                min = Math.min(min, h[i]);
                max = Math.max(max, h[i]);
                System.out.printf("%d %d%n", i, h[i]);
            }
            int[] h2 = new int[2];
            for (int i = 0; i < n; i++) {
                h2[h[i] - min]++;
            }
            System.out.printf("%d=%d %d=%d%n", min, h2[0], max, h2[1]);
        }
    }

    @Test
    public void outputBiasTable() {
        outputBiasTable(32, 31);
    }
    
    private void outputBiasTable(int power, int maxPower) {
//        BigInteger max = BigInteger.ONE.shiftLeft(32);
//        BigDecimal max2 = new BigDecimal(max);
//        for (int i = 1; i < 32; i++) {
//            long n = (1L << i) + 1;
//            BigInteger value = BigInteger.valueOf(n);
//            BigInteger[] result = max.divideAndRemainder(value);
//            BigDecimal bd1 = new BigDecimal(value).divide(max2, 10, RoundingMode.HALF_UP);
//            BigDecimal bd2 = new BigDecimal(result[1]).divide(max2, 10, RoundingMode.HALF_UP);
//            System.out.printf("|%d|%s|%s|%s|%s|%s|%n", n, result[0], result[1],
//                    //new BigDecimal(result[1]).divide(max2, 10, RoundingMode.HALF_UP)
//                    bd1,
//                    bd2,
//                    //bd1.subtract(bd2).divide(bd1, 10, RoundingMode.HALF_UP)
//                    new BigDecimal(result[1]).divide(new BigDecimal(value), 10, RoundingMode.HALF_UP)
//                    );
//        }

        // update this to BigInteger to support all possibilities
        
        
        long range = 1L << power; // 2^4
        // skip powers 1,2
        for (int p = 3; p <= maxPower; p++) {
            
            // Best case scenario
            // 2^power / 2^p = x samples = 2^(power-p) 
            long x = 1L << (power - p);
            
            // Worst case scenario output is half the numbers are over-sampled:
            // 2^power / n = x.5
            long upper = Math.round(range / (0.5 + x));
            
            // Output number of extra samples. These must be rejected.
            long extra = range % upper;
            
            // Search down until extra is close to half of n
            long lower = upper >>> 1;
            double ratio = (double) Math.max(upper - extra, extra) / upper;
            long n = upper;
            while (upper > lower && ratio > 0.52 && Math.abs(upper - 2 * extra) > 1) {
                long ex = range % (--upper);
                double newRatio = (double) Math.max(upper - ex, ex) / upper;
                if (newRatio < ratio) {
                    ratio = newRatio;
                    n = upper;
                    extra = ex;
                }
            }

            // Output number of samples expected for each value in range [0,n)
            long numberOfSamples = range / n;
            
            // frequency * numberOfSamples + frequency1 * (numberOfSamples + 1) = range

            // Frequency each number is seen (number of samples) times
            long frequency = n - extra;
            // Frequency each number is seen (number of samples + 1) times
            long frequency1 = extra;
            
            
            // Output rejection rate.
            double rejectionProbability = (double) extra / range;
            // Output bias (mean and variance of number of samples) if not rejected.
            double mean = (double) range / n;
            double var  = 0;
            if (extra != 0) {
                double dx = mean - numberOfSamples;
                double sum = frequency * dx * dx;
                dx = 1 - dx;
                sum += frequency1 * dx * dx;
                var = sum / n;
            }

            // Output rejection rate.
            double rejectionProbability2 = (double) (range % ((1L << (p-1)) + 1)) / range;

            // Output limit for rejection if using modulus operator
            // Output limit for rejection on remainder if using multiply, divide and remainder.

            System.out.printf("2^%d [n=%d] %d*%d, %d*%d (%.3g +/- %.3g) %.3g  %.3g%n",
                    p, n,
                    frequency, numberOfSamples,
                    frequency1, numberOfSamples + 1,
                    mean, var, 
                    rejectionProbability, rejectionProbability2);
        }
    }

    @Test
    public void testConverge() {
        long bits = 0xffffffffL;
        while (bits != 0) {
            testConverge(bits);
            bits >>>= 1;
        }
    }

    private void testConverge(long bits) {
        long n = 0;
        int n2 = 0;
        final long limit = 0;
        long result = bits;
        System.out.printf("first %d = %s : ", result, Long.toBinaryString(result));
        while (result > limit) {
            result = (result * bits) >>> 32;
            n++;
            n2++;
        }
        System.out.printf("n=%d (%s) [%s %d] %d = %s%n", n, n >= Integer.MAX_VALUE, 
                Integer.toUnsignedString(n2), n2 & 0x7fffffff,
                result, Long.toBinaryString(result));
    }

    @Test
    public void testMakeIntInRangeIsUniform() {
        final int bins = 37; // prime
        final int[] h = new int[bins];

        final int binWidth = Integer.MAX_VALUE / bins;
        final int n = binWidth * bins;

        // Weyl sequence using George Marsaglia’s increment from:
        // Marsaglia, G (July 2003). "Xorshift RNGs". Journal of Statistical Software. 8 (14).
        // https://en.wikipedia.org/wiki/Weyl_sequence
        final int increment = 362437;
        final int start = Integer.MIN_VALUE - increment;
        int bits = start;
        // Loop until the first wrap. The entire sequence will be uniform.
        // Note this is not the full period of the sequence.
        // Expect (1L << 32) / increment numbers = 11850
        while ((bits += increment) < start) {
            h[NumberFactory.makeIntInRange(bits, n) / binWidth]++;
        }

        // The bins should all be the same within a value of 1 (i.e. uniform)
        int min = h[0];
        int max = h[0];
        for (int value : h) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        Assert.assertTrue("Not uniform, max = " + max + ", min=" + min, max - min <= 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMakeLongInRangeWithRangeZeroThrows() {
        NumberFactory.makeLongInRange(0L, 0L);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMakeLongInRangeWithNegativeRangeThrows() {
        NumberFactory.makeLongInRange(0L, -1L);
    }

    @Test
    public void testMakeLongInRange() {
        final long allBits = 0xffffffffffffffffL;
        final long noBits = 0;
        for (int i = 0; i < 63; i++) {
            final long n = 1L << i;
            Assert.assertEquals(0, NumberFactory.makeLongInRange(noBits, n));
            assertMakeLongInRange(allBits, n);
        }
        assertMakeLongInRange(allBits, Long.MAX_VALUE);
        for (int i = 1; i <= 63; i++) {
            assertMakeLongInRange(allBits << 1, Long.MAX_VALUE);
        }
 
        // Check some random values
        final Random rng = new Random();
        for (int i = 0; i < 63; i++) {
            final long n = 1L << i;
            assertMakeLongInRange(rng.nextLong(), n);
        }
        for (int i = 0; i < 100; i++) {
            assertMakeLongInRange(rng.nextLong(), Long.MAX_VALUE);
        }
    }

    @Test
    public void testMakeLongInRangeIsUniform() {
        final long bins = 37; // prime
        final int[] h = new int[(int) bins];

        final long binWidth = Long.MAX_VALUE / bins;
        final long n = binWidth * bins;

        Assert.assertNotEquals("Require upper limit to have bits set in the lower 32-bits", 
                               0, NumberFactory.extractLo(n));

        // Weyl sequence using an increment to approximate the same number of samples
        // as the integer test for uniformity.
        final long increment = BigInteger.ONE.shiftLeft(64).divide(BigInteger.valueOf(11850)).longValue();
        final long start = Long.MIN_VALUE - increment;
        long bits = start;
        // Loop until the first wrap. The entire sequence will be uniform.
        // Note this is not the full period of the sequence.
        while ((bits += increment) < start) {
            h[(int) (NumberFactory.makeLongInRange(bits, n) / binWidth)]++;
        }

        // The bins should all be the same within a value of 1 (i.e. uniform)
        long min = h[0];
        long max = h[0];
        for (long value : h) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        Assert.assertTrue("Not uniform, max = " + max + ", min=" + min, max - min <= 1);
    }

    /**
     * Assert that the value is close to but <strong>not above</strong> 1. This is used to test
     * the output from methods that produce a {@code float} value that must be in the range
     * between 0 and 1.
     *
     * @param value the value
     * @param maxUlps {@code (maxUlps - 1)} is the number of floating point values between x and y.
     * @see Precision#equals(float, float, int)
     */
    private static void assertCloseToNotAbove1(float value, int maxUlps) {
        Assert.assertTrue("Not <= 1.0f", value <= 1.0f);
        Assert.assertTrue("Not equal to 1.0f within units of least precision: " + maxUlps,
                          Precision.equals(1.0f, value, maxUlps));
    }

    /**
     * Assert that the value is close to but <strong>not above</strong> 1. This is used to test
     * the output from methods that produce a {@code double} value that must be in the range
     * between 0 and 1.
     *
     * @param value the value
     * @param maxUlps {@code (maxUlps - 1)} is the number of floating point values between x and y.
     * @see Precision#equals(double, double, int)
     */
    private static void assertCloseToNotAbove1(double value, int maxUlps) {
        Assert.assertTrue("Not <= 1.0", value <= 1.0);
        Assert.assertTrue("Not equal to 1.0 within units of least precision: " + maxUlps,
                          Precision.equals(1.0, value, maxUlps));
    }

    /**
     * Assert that the {@link NumberFactory#makeIntInRange(int, int)} method matches the
     * arithmetic of {@link BigInteger}.
     *
     * <p>This test is included to match the corresponding {@link #assertMakeLongInRange(long, long)}.
     * It should demonstrate that the use of BigInteger is unnecessary and the unsigned integer 
     * arithmetic using {@code long} in the {@link NumberFactory} is correct.</p>
     *
     * @param v Value to use as a source of randomness.
     * @param n Bound on the random number to be returned. Must be positive.
     */
    private static void assertMakeIntInRange(int v, int n) {
        final long unsignedValue = v & 0xffffffffL;
        // Use long to ensure the int can fit unsigned
        final long expected = BigInteger.valueOf(n)
                                        .multiply(BigInteger.valueOf(unsignedValue))
                                        .shiftRight(32).longValue();
        final long actual = NumberFactory.makeIntInRange(v, n);
        if (expected != actual) {
            Assert.assertEquals("v=" + unsignedValue + ",n=" + n,
                                expected, actual);
        }
    }

    /**
     * Assert that the {@link NumberFactory#makeLongInRange(long, long)} method matches the
     * arithmetic of {@link BigInteger}.
     *
     * @param v Value to use as a source of randomness.
     * @param n Bound on the random number to be returned. Must be positive.
     */
    private static void assertMakeLongInRange(long v, long n) {
        // Compute using BigInteger.
        // Construct big-endian byte representation from the long.
        final byte[] bytes = new byte[8];
        for(int i = 0; i < 8; i++) {
           bytes[7 - i] = (byte)((v >>> (i * 8)) & 0xff);
        }
        final BigInteger unsignedValue = new BigInteger(1, bytes);
        final long expected = longValueExact(BigInteger.valueOf(n)
                                                       .multiply(unsignedValue)
                                                       .shiftRight(64));
        final long actual = NumberFactory.makeLongInRange(v, n);
        if (expected != actual) {
            Assert.assertEquals("v=" + unsignedValue + ",n=" + n,
                                expected, actual);
        }
    }

    /**
     * Get the exact long value of the BigInteger without loss of information.
     *
     * <p>This is like BigInteger::longValueExact added in Java 1.8.</p>
     *
     * @param value the value
     * @return the long
     */
    private static long longValueExact(BigInteger value) {
        if (value.bitLength() <= 63) {
            return value.longValue();
        }
        throw new ArithmeticException("BigInteger out of long range");
    }
}
