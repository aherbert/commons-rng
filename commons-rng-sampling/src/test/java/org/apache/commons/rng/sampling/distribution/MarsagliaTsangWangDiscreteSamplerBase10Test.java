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
package org.apache.commons.rng.sampling.distribution;

import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.core.source32.IntProvider;
import org.apache.commons.rng.core.source64.SplitMix64;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerDynamic.BaseOption;
import org.apache.commons.rng.simple.RandomSource;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test for the {@link MarsagliaTsangWangDiscreteSamplerBase10}. The tests hit edge cases for
 * the sampler.
 */
public class MarsagliaTsangWangDiscreteSamplerBase10Test {
    // Tests for the package-private constructor using int[] + offset

    /**
     * Test constructor throws with max index above integer max.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorThrowsWithMaxIndexAboveIntegerMax() {
        final int[] prob = new int[1];
        final int offset = Integer.MAX_VALUE;
        createSampler(prob, offset);
    }

    /**
     * Test constructor throws with negative offset.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorThrowsWithNegativeOffset() {
        final int[] prob = new int[1];
        final int offset = -1;
        createSampler(prob, offset);
    }

    /**
     * Test construction is allowed or when max index equals integer max.
     */
    @Test
    public void testConstructorWhenMaxIndexEqualsIntegerMax() {
        final int[] prob = new int[1];
        prob[0] = 1 << 30; // So the total probability is 2^30
        final int offset = Integer.MAX_VALUE - 1;
        createSampler(prob, offset);
    }

    /**
     * Creates the sampler.
     *
     * @param prob the probabilities
     * @param offset the offset
     * @return the sampler
     */
    private static MarsagliaTsangWangDiscreteSamplerBase10 createSampler(final int[] probabilities, int offset) {
        final UniformRandomProvider rng = new SplitMix64(0L);
        return new MarsagliaTsangWangDiscreteSamplerBase10(rng, probabilities, offset);
    }

    // Tests for the public constructor using double[]

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorThrowsWithNullProbabilites() {
        createSampler(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorThrowsWithZeroLengthProbabilites() {
        createSampler(new double[0]);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorThrowsWithNegativeProbabilites() {
        createSampler(new double[] { -1, 0.1, 0.2 });
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorThrowsWithNaNProbabilites() {
        createSampler(new double[] { 0.1, Double.NaN, 0.2 });
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorThrowsWithInfiniteProbabilites() {
        createSampler(new double[] { 0.1, Double.POSITIVE_INFINITY, 0.2 });
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorThrowsWithInfiniteSumProbabilites() {
        createSampler(new double[] { Double.MAX_VALUE, Double.MAX_VALUE });
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorThrowsWithZeroSumProbabilites() {
        createSampler(new double[4]);
    }

    /**
     * Creates the sampler.
     *
     * @param probabilities the probabilities
     * @return the sampler
     */
    private static MarsagliaTsangWangDiscreteSamplerBase10 createSampler(double[] probabilities) {
        final UniformRandomProvider rng = new SplitMix64(0L);
        return new MarsagliaTsangWangDiscreteSamplerBase10(rng, probabilities);
    }

    // Sampling tests

    /**
     * Test offset samples. This test hits all code paths in the sampler for 8, 16, and 32-bit
     * storage using different offsets to control the maximum sample value.
     */
    @Test
    public void testOffsetSamples() {
        // This is filled with probabilities to hit all edge cases in the fill procedure.
        // The probabilities must have a digit from each of the 5 possible.
        final int[] prob = new int[4];
        prob[0] = 1;
        prob[1] = 1 + 1 << 10;
        prob[2] = 1 + 1 << 20;
        // Ensure probabilities sum to 2^30
        prob[3] = (1 << 30) - (prob[0] + prob[1] + prob[2]);

        // To hit all samples requires integers that are under the look-up table limits.
        // So compute the limits here.
        int n1 = 0;
        int n2 = 0;
        for (final int m : prob) {
            n1 += getBase1024Digit(m, 1);
            n2 += getBase1024Digit(m, 2);
        }

        final int t1 = n1 << 24;
        final int t2 = t1 + (n2 << 18);

        // Create values under the limits and bit shift by 2 to reverse what the sampler does.
        final int[] values = new int[] { 0, t1, t2, 0xffffffff };
        for (int i = 0; i < values.length; i++) {
            values[i] <<= 2;
        }

        final UniformRandomProvider rng1 = new FixedSequenceIntProvider(values);
        final UniformRandomProvider rng2 = new FixedSequenceIntProvider(values);
        final UniformRandomProvider rng3 = new FixedSequenceIntProvider(values);

        // Create offsets to force storage as 8, 16, or 32-bit
        final int offset1 = 1;
        final int offset2 = 1 << 8;
        final int offset3 = 1 << 16;

        final MarsagliaTsangWangDiscreteSamplerBase10 sampler1 = new MarsagliaTsangWangDiscreteSamplerBase10(rng1, prob, offset1);
        final MarsagliaTsangWangDiscreteSamplerBase10 sampler2 = new MarsagliaTsangWangDiscreteSamplerBase10(rng2, prob, offset2);
        final MarsagliaTsangWangDiscreteSamplerBase10 sampler3 = new MarsagliaTsangWangDiscreteSamplerBase10(rng3, prob, offset3);

        for (int i = 0; i < values.length; i++) {
            // Remove offsets
            final int s1 = sampler1.sample() - offset1;
            final int s2 = sampler2.sample() - offset2;
            final int s3 = sampler3.sample() - offset3;
            Assert.assertEquals("Offset sample 1 and 2 do not match", s1, s2);
            Assert.assertEquals("Offset Sample 1 and 3 do not match", s1, s3);
        }
    }

    /**
     * Test samples from a distribution expressed using {@code double} probabilities.
     */
    @Test
    public void testRealProbabilityDistributionSamples() {
        // These do not have to sum to 1
        final double[] probabilities = new double[11];
        final UniformRandomProvider rng = RandomSource.create(RandomSource.SPLIT_MIX_64);
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] = rng.nextDouble();
        }

        // First test the table is completely filled to 2^30
        final UniformRandomProvider dummyRng = new FixedSequenceIntProvider(new int[] { 0xffffffff});
        final MarsagliaTsangWangDiscreteSamplerBase10 dummySampler = new MarsagliaTsangWangDiscreteSamplerBase10(dummyRng, probabilities);
        // This will throw if the table is incomplete as it hits the upper limit
        dummySampler.sample();

        // Do a test of the actual sampler
        final MarsagliaTsangWangDiscreteSamplerBase10 sampler = new MarsagliaTsangWangDiscreteSamplerBase10(rng, probabilities);

        final int numberOfSamples = 10000;
        final long[] samples = new long[probabilities.length];
        for (int i = 0; i < numberOfSamples; i++) {
            samples[sampler.sample()]++;
        }

        final ChiSquareTest chiSquareTest = new ChiSquareTest();
        // Pass if we cannot reject null hypothesis that the distributions are the same.
        Assert.assertFalse(chiSquareTest.chiSquareTest(probabilities, samples, 0.001));
    }

    /**
     * Test the storage requirements for a worst case set of 2^8 probabilities. This tests the
     * limits described in the class Javadoc is correct.
     */
    //@Test
    public void testStorageRequirements8() {
        // Max digits from 2^22:
        // (2^4 + 2^6 + 2^6 + 2^6)
        // Storage in bytes
        // = (15 + 3 * 63) * 2^8
        // = 52224 B
        // = 0.0522 MB
        checkStorageRequirements(8, 0.06);
    }

    /**
     * Test the storage requirements for a worst case set of 2^16 probabilities. This tests the
     * limits described in the class Javadoc is correct.
     */
    //@Test
    public void testStorageRequirements16() {
        // Max digits from 2^14:
        // (2^2 + 2^6 + 2^6)
        // Storage in bytes
        // = 2 * (3 + 2 * 63) * 2^16
        // = 16908288 B
        // = 16.91 MB
        checkStorageRequirements(16, 17.0);
    }

    /**
     * Test the storage requirements for a worst case set of 2^k probabilities. This
     * tests the limits described in the class Javadoc is correct.
     *
     * @param k Base is 2^k.
     * @param expectedLimitMB the expected limit in MB
     */
    private static void checkStorageRequirements(int k, double expectedLimitMB) {
        // Worst case scenario is a uniform distribution of 2^k samples each with the highest
        // mask set for base 64 digits.
        // The max number of samples: 2^k
        final int maxSamples = (1 << k);

        // The highest value for each sample:
        // 2^30 / 2^k = 2^(30-k)
        // The highest mask is all bits set
        final int m = (1 << (30 - k)) - 1;

        // Check the sum is less than 2^30
        final long sum = (long) maxSamples * m;
        final int total = 1 << 30;
        Assert.assertTrue("Worst case uniform distribution is above 2^30", sum < total);

        // Get the digits as per the sampler and compute storage
        final int d1 = getBase1024Digit(m, 1);
        final int d2 = getBase1024Digit(m, 2);
        final int d3 = getBase1024Digit(m, 3);
        final int d4 = getBase1024Digit(m, 4);
        final int d5 = getBase1024Digit(m, 5);
        // Compute storage in MB assuming 2 byte storage
        int bytes;
        if (k <= 8) {
            bytes = 1;
        } else if (k <= 16) {
            bytes = 2;
        } else {
            bytes = 4;
        }
        final double storageMB = bytes * 1e-6 * (d1 + d2 + d3 + d4 + d5) * maxSamples;
        Assert.assertTrue(
            "Worst case uniform distribution storage " + storageMB + "MB is above expected limit: " + expectedLimitMB,
            storageMB < expectedLimitMB);
    }

    /**
     * Gets the k<sup>th</sup> base 1024 digit of {@code m}.
     *
     * @param m the value m.
     * @param k the digit.
     * @return the base 1024 digit
     */
    private static int getBase1024Digit(int m, int k) {
        return (m >>> (30 - 10 * k)) & 1023;
    }

    /**
     * Return a fixed sequence of {@code int} output.
     */
    private class FixedSequenceIntProvider extends IntProvider {
        /** The count of values output. */
        private int count;
        /** The values. */
        private final int[] values;

        /**
         * Instantiates a new fixed sequence int provider.
         *
         * @param values Values.
         */
        FixedSequenceIntProvider(int[] values) {
            this.values = values;
        }

        @Override
        public int next() {
            // This should not be called enough to overflow count
            return values[count++ % values.length];
        }
    }

    @Test
    public void testImplementations() {
        Long seed = 237648682L;
        double[] p = { 0.1, 0.2, 0.3, 0.4 };
        testImplementation(new MarsagliaTsangWangDiscreteSamplerBase10_2D(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerBase10Byte(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerBase10Short(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerBase10Byte2D(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerBase10Short2D(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerDynamic(new SplitMix64(seed), p, BaseOption.BASE_1024), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerDynamicDelegated(new SplitMix64(seed), p, MarsagliaTsangWangDiscreteSamplerDynamicDelegated.BaseOption.BASE_1024), seed, p);
    }

    private void testImplementation(DiscreteSampler sampler, Long seed,
            double[] p) {
        final DiscreteSampler ref = 
                new MarsagliaTsangWangDiscreteSamplerBase10(new SplitMix64(seed), p);

        String msg = "Does not match reference: " + sampler.getClass().getSimpleName();
        for (int i = 0; i < 100; i++) {
            Assert.assertEquals(msg, ref.sample(), sampler.sample());
        }

    }
}
