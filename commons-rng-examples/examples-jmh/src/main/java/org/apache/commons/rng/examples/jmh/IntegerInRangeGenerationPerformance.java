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

package org.apache.commons.rng.examples.jmh;

import org.apache.commons.rng.RestorableUniformRandomProvider;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;

import java.util.concurrent.TimeUnit;

/**
 * Executes benchmark to compare the speed of generation of integer numbers in a positive range
 * using the integer primitives as a source of randomness. The methods tested assume that the
 * range is known and constant.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = { "-server", "-Xms128M", "-Xmx128M" })
public class IntegerInRangeGenerationPerformance {
    /** The loops. */
    @Param({
        "100000",
        })
    private int loops;

    /**
     * The benchmark state (retrieve the various "RandomSource"s).
     */
    @State(Scope.Benchmark)
    public static class Sources {
        /**
         * RNG providers.
         *
         * <p>Use different speeds.</p>
         *
         * @see <a href="https://commons.apache.org/proper/commons-rng/userguide/rng.html">
         *      Commons RNG user guide</a>
         */
        @Param({"SPLIT_MIX_64",
                // Comment in for slower generators
                //"MWC_256", "KISS", "WELL_1024_A",
                //"WELL_44497_B"
                })
        private String randomSourceName;

        /** RNG. */
        private RestorableUniformRandomProvider generator;

        /**
         * @return the RNG.
         */
        public UniformRandomProvider getGenerator() {
            return generator;
        }

        /** Instantiates generator. */
        @Setup
        public void setup() {
            final RandomSource randomSource = RandomSource.valueOf(randomSourceName);
            generator = RandomSource.create(randomSource);
        }
    }

    /**
     * The upper range for the {@code int} generation.
     */
    @State(Scope.Benchmark)
    public static class IntRange {
        /**
         * The upper range for the {@code int} generation.
         *
         * <p>Note that the while loop uses a rejection algorithm. From the Javadoc for java.util.Random:</p>
         *
         * <pre>
         * "The probability of a value being rejected depends on n. The
         * worst case is n=2^30+1, for which the probability of a reject is 1/2,
         * and the expected number of iterations before the loop terminates is 2."
         * </pre>
         */
        @Param({
            "256", // Even: 1 << 8
            "257", // Prime number
            "1073741825", // Worst case: (1 << 30) + 1
            })
        private int upperBound;

        /**
         * Gets the upper bound.
         *
         * @return the upper bound
         */
        public int getUpperBound() {
            return upperBound;
        }
    }

    /**
     * Generates an {@code int} value between 0 (inclusive) and the
     * specified value (exclusive) using a bit source of randomness.
     *
     * @param rng Value to use as a source of randomness.
     * @param n Bound on the random number to be returned. Must be positive.
     * @return a random {@code int} value between 0 (inclusive) and {@code n}
     * (exclusive).
     * @throws IllegalArgumentException if {@code n} is negative.
     */
    private static int nextInt(UniformRandomProvider rng, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException();
        }
        final int nm1 = n - 1;
        if ((n & nm1) == 0) {
            // Range is a power of 2
            return rng.nextInt() & nm1;
        }

        // Rejection method
        int bits;
        int val;
        do {
            bits = rng.nextInt() >>> 1;
            val = bits % n;
        } while (bits - val + nm1 < 0);
        return val;
    }

    /**
     * Generates an {@code int} value between 0 (inclusive) and the specified value
     * (exclusive) using a bit source of randomness.
     *
     * <p>This method produces non-uniform samples.</p>
     *
     * @param rng Value to use as a source of randomness.
     * @param n Bound on the random number to be returned. Must be positive.
     * @return a random {@code int} value between 0 (inclusive) and {@code n}
     * (exclusive).
     */
    private static int nextIntModulusBiased(UniformRandomProvider rng, int n) {
        return (rng.nextInt() >>> 1) % n;
    }

    /**
     * Generates an {@code int} value between 0 (inclusive) and the specified value
     * (exclusive) using a bit source of randomness.
     *
     * <p>This assumes that the input {@code n} will be in the correct range and the
     * {@code fence} limit is set to
     * {@code 0x80000000L - 0x80000000L % n - 1}. This is the
     * level above which there are not {@code n - 1} more possible values before the
     * range exceeds {@code 2^31 - 1}. If {@code n} is a power of 2 then the fence
     * should be set to {@code Integer.MAX_VALUE}.</p>
     *
     * @param rng Value to use as a source of randomness.
     * @param n Bound on the random number to be returned. Must be positive.
     * @param fence the fence limit on the 31-bit positive integer.
     * @return a random {@code int} value between 0 (inclusive) and {@code n}
     * (exclusive).
     */
    private static int nextIntModulusUnbiased(UniformRandomProvider rng, int n, int fence) {
        int bits;
        do {
            bits = rng.nextInt() >>> 1;
        } while (bits > fence);
        return bits % n;
    }


    /**
     * Generates an {@code int} value between 0 (inclusive) and the
     * specified value (exclusive) using a bit source of randomness.
     *
     * <p>This method produces non-uniform samples.</p>
     *
     * @param rng Value to use as a source of randomness.
     * @param n Bound on the random number to be returned. Must be positive.
     * @return a random {@code int} value between 0 (inclusive) and {@code n}
     * (exclusive).
     */
    private static int nextIntMultiplyBiased(UniformRandomProvider rng, int n) {
        return (int)((n * (rng.nextInt() & 0xffffffffL)) >>> 32);
    }

    /**
     * Generates an {@code int} value between 0 (inclusive) and the
     * specified value (exclusive) using a bit source of randomness.
     *
     * <p>This assumes that the input {@code n} will be in the correct range and the
     * {@code fence} limit is set to {@code 0x100000000L % n}. This is the excess number of
     * samples that can be extracted from an unsigned 32-bit integer. It is used to
     * set a lower limit on the remainder of the multiply operation.</p>
     *
     * @param rng Value to use as a source of randomness.
     * @param n Bound on the random number to be returned. Must be positive.
     * @param fence the fence limit on the 32-bit positive integer remainder.
     * @return a random {@code int} value between 0 (inclusive) and {@code n}
     * (exclusive).
     */
    private static int nextIntMultiplyUnbiased(UniformRandomProvider rng, int n, long fence) {
        // Rejection method using multiply by a fraction:
        // n * [0, 2^32 - 1)
        //     -------------
        //         2^32
        // The result is mapped back to an integer and will be in the range [0, n)
        long result;
        do {
            // Compute 64-bit unsigned product of n * [0, 2^32 - 1)
            result = n * (rng.nextInt() & 0xffffffffL);

            // Test the sample uniformity.
            // The upper 32-bits contains the sample value in the range [0, n), i.e. result / 2^32.
            // The lower 32-bits contains the remainder (result % 2^32) and provides information
            // about the uniformity. Since the remainder is periodically spaced at intervals of n
            // the frequency observed for a sample value is either floor(2^32/n) or ceil(2^32/n).
            // To ensure all samples have a frequency of floor(2^32/n) reject any index with
            // a value < 2^32 % n, i.e. the level below which denotes that there are still
            // floor(2^32/n) more observations of this sample.
        } while ((result & 0xffffffffL) < fence);
        return (int)(result >>> 32);
    }

    // Benchmark methods

    /**
     * @param bh the data sink
     * @param source the source
     */
    @Benchmark
    public void nextIntBaseline(Blackhole bh, Sources source) {
        for (int i = loops; i-- != 0; ) {
            bh.consume(source.getGenerator().nextInt());
        }
    }

    /**
     * @param bh the data sink
     * @param source the source
     * @param range the range
     */
    @Benchmark
    public void nextInt(Blackhole bh, Sources source, IntRange range) {
        final int n = range.getUpperBound();
        for (int i = loops; i-- != 0; ) {
            bh.consume(nextInt(source.getGenerator(), n));
        }
    }

    /**
     * @param bh the data sink
     * @param source the source
     * @param range the range
     */
    @Benchmark
    public void nextIntModulusBiased(Blackhole bh, Sources source, IntRange range) {
        final int n = range.getUpperBound();
        for (int i = loops; i-- != 0; ) {
            bh.consume(nextIntModulusBiased(source.getGenerator(), n));
        }
    }

    /**
     * @param bh the data sink
     * @param source the source
     * @param range the range
     */
    @Benchmark
    public void nextIntModulusUnbiased(Blackhole bh, Sources source, IntRange range) {
        final int n = range.getUpperBound();
        // This method works where n is a power of 2 (the result is Integer.MAX_VALUE)
        final int fence = (int)(0x80000000L - 0x80000000L % n - 1);
        // These compute equivalent levels but do not work where n is a power of 2:
        // Integer.MAX_VALUE - Integer.MAX_VALUE % n - 1
        // (Integer.MAX_VALUE / n) * n - 1
        for (int i = loops; i-- != 0; ) {
            bh.consume(nextIntModulusUnbiased(source.getGenerator(), n, fence));
        }
    }

    /**
     * @param bh the data sink
     * @param source the source
     * @param range the range
     */
    @Benchmark
    public void nextIntMultiplyBiased(Blackhole bh, Sources source, IntRange range) {
        final int n = range.getUpperBound();
        for (int i = loops; i-- != 0; ) {
            bh.consume(nextIntMultiplyBiased(source.getGenerator(), n));
        }
    }

    /**
     * @param bh the data sink
     * @param source the source
     * @param range the range
     */
    @Benchmark
    public void nextIntMultiplyUnbiased(Blackhole bh, Sources source, IntRange range) {
        final int n = range.getUpperBound();
        final long fence = (1L << 32) % n;
        for (int i = loops; i-- != 0; ) {
            bh.consume(nextIntMultiplyUnbiased(source.getGenerator(), n, fence));
        }
    }
}
