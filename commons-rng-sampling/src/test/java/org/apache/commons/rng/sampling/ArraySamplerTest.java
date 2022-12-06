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
package org.apache.commons.rng.sampling;

import java.util.Arrays;
import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ArraySampler}.
 */
class ArraySamplerTest {
    @Test
    void testNullArguments() {
        final UniformRandomProvider rng = RandomAssert.seededRNG();
        // To generate a NPE for the RNG requires shuffle conditions to be satisfied (length > 1)
        final boolean[] a = {false, false};
        final byte[] b = {0, 0};
        final char[] c = {0, 0};
        final double[] d = {0, 0};
        final float[] f = {0, 0};
        final int[] i = {0, 0};
        final long[] l = {0, 0};
        final short[] s = {0, 0};
        final Object[] t = {new Object(), new Object()};
        // Shuffle full length
        ArraySampler.shuffle(rng, a);
        ArraySampler.shuffle(rng, b);
        ArraySampler.shuffle(rng, c);
        ArraySampler.shuffle(rng, d);
        ArraySampler.shuffle(rng, f);
        ArraySampler.shuffle(rng, i);
        ArraySampler.shuffle(rng, l);
        ArraySampler.shuffle(rng, s);
        ArraySampler.shuffle(rng, t);
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, a));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, b));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, c));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, d));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, f));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, i));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, l));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, s));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, t));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (boolean[]) null));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (byte[]) null));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (char[]) null));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (double[]) null));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (float[]) null));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (int[]) null));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (long[]) null));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (short[]) null));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (Object[]) null));
        // Shuffle with sub-range
        ArraySampler.shuffle(rng, a, 0, 2);
        ArraySampler.shuffle(rng, b, 0, 2);
        ArraySampler.shuffle(rng, c, 0, 2);
        ArraySampler.shuffle(rng, d, 0, 2);
        ArraySampler.shuffle(rng, f, 0, 2);
        ArraySampler.shuffle(rng, i, 0, 2);
        ArraySampler.shuffle(rng, l, 0, 2);
        ArraySampler.shuffle(rng, s, 0, 2);
        ArraySampler.shuffle(rng, t, 0, 2);
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, a, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, b, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, c, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, d, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, f, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, i, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, l, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, s, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(null, t, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (boolean[]) null, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (byte[]) null, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (char[]) null, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (double[]) null, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (float[]) null, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (int[]) null, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (long[]) null, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (short[]) null, 0, 2));
        Assertions.assertThrows(NullPointerException.class, () -> ArraySampler.shuffle(rng, (Object[]) null, 0, 2));
    }

    // Shuffle tests for randomness performed on int[].
    // All other implementations must match int[] shuffle.

    /**
     * Test an invalid sub-range.
     *
     * <p>Note if the range is invalid then any shuffle will eventually raise
     * an out-of-bounds exception when the invalid part of the range is encountered.
     * This may destructively modify the input before the exception. This test
     * verifies the RNG is never invoked and the input is unchanged.
     *
     * <p>This is only tested on int[].
     * We assume all other methods check the sub-range in the same way.
     */
    @ParameterizedTest
    @CsvSource({
        "-1, 10, 20", // from < 0
        "10, 0, 20", // from > to
        "10, -1, 20", // from > to; to < 0
        "10, 20, 15", // to > length
        "-20, -10, 10", // length >= to - from > 0; from < to < 0
        "-10, 10, 10", // from < 0; to - from > length
        // Overflow of differences
        // -2147483648 == Integer.MIN_VALUE
        "10, -2147483648, 20", // length > to - from > 0; to < from
    })
    void testInvalidSubRange(int from, int to, int length) {
        final int[] array = PermutationSampler.natural(length);
        final int[] original = array.clone();
        final UniformRandomProvider rng = new UniformRandomProvider() {
            @Override
            public long nextLong() {
                Assertions.fail("Preconditions should fail before RNG is used");
                return 0;
            }
        };
        Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> ArraySampler.shuffle(rng, array, from, to));
        Assertions.assertArrayEquals(original, array);
    }

    /**
     * Test that all (unique) entries exist in the shuffled array.
     */
    @ParameterizedTest
    @ValueSource(ints = {13, 42, 100})
    void testShuffleNoDuplicates(int length) {
        // Natural sequence
        final int[] array = new int[length];
        for (int i = 0; i < length; i++) {
            array[i] = i;
        }
        final UniformRandomProvider rng = RandomAssert.seededRNG();

        final int[] count = new int[length];
        for (int j = 1; j <= 10; j++) {
            ArraySampler.shuffle(rng, array);
            for (int i = 0; i < count.length; i++) {
                count[array[i]]++;
            }
            for (int i = 0; i < count.length; i++) {
                Assertions.assertEquals(j, count[i], "Shuffle duplicated data");
            }
        }
    }

    /**
     * Test that all (unique) entries exist in the shuffled sub-range of the array.
     */
    @ParameterizedTest
    @CsvSource({
        "0, 10, 10",
        "5, 10, 10",
        "0, 5, 10",
        "5, 10, 15",
    })
    void testShuffleSubRangeNoDuplicates(int from, int to, int length) {
        // Natural sequence in the sub-range
        final int[] array = natural(from, to, length);
        final UniformRandomProvider rng = RandomAssert.seededRNG();

        final int[] count = new int[to - from];
        for (int j = 1; j <= 10; j++) {
            ArraySampler.shuffle(rng, array, from, to);
            for (int i = 0; i < from; i++) {
                Assertions.assertEquals(i - from, array[i], "Shuffle changed data < from");
            }
            for (int i = from; i < to; i++) {
                count[array[i]]++;
            }
            for (int i = to; i < length; i++) {
                Assertions.assertEquals(i - from, array[i], "Shuffle changed data >= to");
            }
            for (int i = 0; i < count.length; i++) {
                Assertions.assertEquals(j, count[i], "Shuffle duplicated data");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {13, 16})
    void testShuffleIsRandom(int length) {
        // Natural sequence
        final int[] array = PermutationSampler.natural(length);
        final UniformRandomProvider rng = RandomAssert.seededRNG();
        final long[][] counts = new long[length][length];
        for (int j = 1; j <= 1000; j++) {
            ArraySampler.shuffle(rng, array);
            for (int i = 0; i < length; i++) {
                counts[i][array[i]]++;
            }
        }
        final double p = new ChiSquareTest().chiSquareTest(counts);
        Assertions.assertFalse(p < 1e-3, () -> "p-value too small: " + p);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 10, 10",
        "7, 18, 18",
        "0, 13, 20",
        "5, 17, 20",
    })
    void testShuffleSubRangeIsRandom(int from, int to, int length) {
        // Natural sequence in the sub-range
        final int[] array = natural(from, to, length);
        final UniformRandomProvider rng = RandomAssert.seededRNG();
        final int n = to - from;
        final long[][] counts = new long[n][n];
        for (int j = 1; j <= 1000; j++) {
            ArraySampler.shuffle(rng, array, from, to);
            for (int i = 0; i < n; i++) {
                counts[i][array[from + i]]++;
            }
        }
        final double p = new ChiSquareTest().chiSquareTest(counts);
        Assertions.assertFalse(p < 1e-3, () -> "p-value too small: " + p);
    }

    // Test other implementations. Include zero length arrays.

    @ParameterizedTest
    @ValueSource(ints = {0, 13, 16})
    void testShuffle(int length) {
        // Natural sequence
        final int[] i = PermutationSampler.natural(length);
        final byte[] b = bytes(i);
        final char[] c = chars(i);
        final double[] d = doubles(i);
        final float[] f = floats(i);
        final long[] l = longs(i);
        final short[] s = shorts(i);
        final Integer[] t = boxed(i);
        ArraySampler.shuffle(RandomAssert.seededRNG(), i);
        ArraySampler.shuffle(RandomAssert.seededRNG(), b);
        ArraySampler.shuffle(RandomAssert.seededRNG(), c);
        ArraySampler.shuffle(RandomAssert.seededRNG(), d);
        ArraySampler.shuffle(RandomAssert.seededRNG(), f);
        ArraySampler.shuffle(RandomAssert.seededRNG(), l);
        ArraySampler.shuffle(RandomAssert.seededRNG(), s);
        ArraySampler.shuffle(RandomAssert.seededRNG(), t);
        Assertions.assertArrayEquals(i, ints(b), "byte");
        Assertions.assertArrayEquals(i, ints(c), "char");
        Assertions.assertArrayEquals(i, ints(d), "double");
        Assertions.assertArrayEquals(i, ints(f), "float");
        Assertions.assertArrayEquals(i, ints(l), "longs");
        Assertions.assertArrayEquals(i, ints(s), "short");
        Assertions.assertArrayEquals(i, unboxed(t), "T");
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0",
        "0, 10, 10",
        "7, 18, 18",
        "0, 13, 20",
        "5, 17, 20",
        // Test is limited to max length 127 by signed byte
        "57, 121, 127",
    })
    void testShuffleSubRange(int from, int to, int length) {
        // Natural sequence.
        final int[] i = PermutationSampler.natural(length);
        final byte[] b = bytes(i);
        final char[] c = chars(i);
        final double[] d = doubles(i);
        final float[] f = floats(i);
        final long[] l = longs(i);
        final short[] s = shorts(i);
        final Integer[] t = boxed(i);
        ArraySampler.shuffle(RandomAssert.seededRNG(), i, from, to);
        ArraySampler.shuffle(RandomAssert.seededRNG(), b, from, to);
        ArraySampler.shuffle(RandomAssert.seededRNG(), c, from, to);
        ArraySampler.shuffle(RandomAssert.seededRNG(), d, from, to);
        ArraySampler.shuffle(RandomAssert.seededRNG(), f, from, to);
        ArraySampler.shuffle(RandomAssert.seededRNG(), l, from, to);
        ArraySampler.shuffle(RandomAssert.seededRNG(), s, from, to);
        ArraySampler.shuffle(RandomAssert.seededRNG(), t, from, to);
        Assertions.assertArrayEquals(i, ints(b), "byte");
        Assertions.assertArrayEquals(i, ints(c), "char");
        Assertions.assertArrayEquals(i, ints(d), "double");
        Assertions.assertArrayEquals(i, ints(f), "float");
        Assertions.assertArrayEquals(i, ints(l), "longs");
        Assertions.assertArrayEquals(i, ints(s), "short");
        Assertions.assertArrayEquals(i, unboxed(t), "T");
    }

    // Special case for boolean[].
    // Use a larger array and it is very unlikely a shuffle of bits will be the same.
    // This cannot be done with the other arrays as the limit is 127 for a "universal" number.

    @ParameterizedTest
    @ValueSource(ints = {0, 1234})
    void testShuffleBoolean(int length) {
        final byte[] a = randomBitsAsBytes(length);
        final boolean[] b = booleans(a);
        ArraySampler.shuffle(RandomAssert.seededRNG(), a);
        ArraySampler.shuffle(RandomAssert.seededRNG(), b);
        Assertions.assertArrayEquals(a, bytes(b));
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0",
        "0, 1000, 1000",
        "100, 1000, 1000",
        "0, 900, 1000",
        "100, 1100, 1200",
    })
    void testShuffleBooleanSubRange(int from, int to, int length) {
        final byte[] a = randomBitsAsBytes(length);
        final boolean[] b = booleans(a);
        ArraySampler.shuffle(RandomAssert.seededRNG(), a, from, to);
        ArraySampler.shuffle(RandomAssert.seededRNG(), b, from, to);
        Assertions.assertArrayEquals(a, bytes(b));
    }

    /**
     * Creates a natural sequence (0, 1, ..., n-1) in the sub-range {@code [from, to)}
     * where {@code n = to - from}. Values outside the sub-range are a continuation
     * of the sequence in either direction.
     *
     * @param from Lower-bound (inclusive) of the sub-range
     * @param to Upper-bound (exclusive) of the sub-range
     * @param length Upper-bound (exclusive) of the range
     * @return an array whose entries are the numbers 0, 1, ..., {@code n}-1.
     */
    private static int[] natural(int from, int to, int length) {
        final int[] array = new int[length];
        for (int i = 0; i < from; i++) {
            array[i] = i - from;
        }
        for (int i = from; i < to; i++) {
            array[i] = i - from;
        }
        for (int i = to; i < length; i++) {
            array[i] = i - from;
        }
        return array;
    }

    /**
     * Create random bits of the specified length stored as bytes using {0, 1}.
     *
     * @param length Length of the array.
     * @return the byte bits
     */
    private static byte[] randomBitsAsBytes(int length) {
        // Random bytes
        final byte[] a = new byte[length];
        RandomAssert.createRNG().nextBytes(a);
        // Convert to boolean bits: 0 or 1
        for (int i = 0; i < length; i++) {
            a[i] = (byte) (a[i] & 1);
        }
        return a;
    }

    // Conversion helpers
    // Special case for boolean <=> bytes as {0, 1}

    private static boolean[] booleans(byte[] in) {
        final boolean[] out = new boolean[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (in[i] & 1) == 1;
        }
        return out;
    }

    private static byte[] bytes(boolean[] in) {
        final byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] ? (byte) 1 : 0;
        }
        return out;
    }

    // Conversion helpers using standard primitive conversions.
    // This may involved narrowing conversions so "universal" numbers are
    // limited to lower 0 by char and upper 127 by byte.

    private static byte[] bytes(int[] in) {
        final byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) in[i];
        }
        return out;
    }

    private static char[] chars(int[] in) {
        final char[] out = new char[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (char) in[i];
        }
        return out;
    }

    private static double[] doubles(int[] in) {
        final double[] out = new double[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
        return out;
    }

    private static float[] floats(int[] in) {
        final float[] out = new float[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
        return out;
    }

    private static long[] longs(int[] in) {
        final long[] out = new long[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
        return out;
    }

    private static short[] shorts(int[] in) {
        final short[] out = new short[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (short) in[i];
        }
        return out;
    }

    private static int[] ints(byte[] in) {
        final int[] out = new int[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
        return out;
    }

    private static int[] ints(char[] in) {
        final int[] out = new int[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
        return out;
    }

    private static int[] ints(double[] in) {
        final int[] out = new int[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (int) in[i];
        }
        return out;
    }

    private static int[] ints(float[] in) {
        final int[] out = new int[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (int) in[i];
        }
        return out;
    }

    private static int[] ints(long[] in) {
        final int[] out = new int[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (int) in[i];
        }
        return out;
    }

    private static int[] ints(short[] in) {
        final int[] out = new int[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
        return out;
    }

    private static Integer[] boxed(int[] in) {
        return Arrays.stream(in).boxed().toArray(Integer[]::new);
    }

    private static int[] unboxed(Integer[] in) {
        return Arrays.stream(in).mapToInt(i -> i).toArray();
    }
}
