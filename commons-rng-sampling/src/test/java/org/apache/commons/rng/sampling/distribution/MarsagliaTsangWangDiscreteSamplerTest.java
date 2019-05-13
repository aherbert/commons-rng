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
 * Test for the {@link MarsagliaTsangWangDiscreteSampler}. The tests hit edge cases for
 * the sampler.
 */
public class MarsagliaTsangWangDiscreteSamplerTest {
    // Tests for the package-private constructor using int[] + offset

//    /**
//     * Test constructor throws with max index above integer max.
//     */
//    @Test(expected = IllegalArgumentException.class)
//    public void testConstructorThrowsWithMaxIndexAboveIntegerMax() {
//        final int[] prob = new int[1];
//        final int offset = Integer.MAX_VALUE;
//        createSampler(prob, offset);
//    }
//
//    /**
//     * Test constructor throws with negative offset.
//     */
//    @Test(expected = IllegalArgumentException.class)
//    public void testConstructorThrowsWithNegativeOffset() {
//        final int[] prob = new int[1];
//        final int offset = -1;
//        createSampler(prob, offset);
//    }
//
//    /**
//     * Test construction is allowed or when max index equals integer max.
//     */
//    @Test
//    public void testConstructorWhenMaxIndexEqualsIntegerMax() {
//        final int[] prob = new int[1];
//        prob[0] = 1 << 30; // So the total probability is 2^30
//        final int offset = Integer.MAX_VALUE - 1;
//        createSampler(prob, offset);
//    }
//
//    /**
//     * Creates the sampler.
//     *
//     * @param prob the probabilities
//     * @param offset the offset
//     * @return the sampler
//     */
//    private static MarsagliaTsangWangDiscreteSampler createSampler(final int[] probabilities, int offset) {
//        final UniformRandomProvider rng = new SplitMix64(0L);
//        return MarsagliaTsangWangDiscreteSampler.createDiscreteDistribution(rng, probabilities, offset);
//    }

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
    private static MarsagliaTsangWangDiscreteSampler createSampler(double[] probabilities) {
        final UniformRandomProvider rng = new SplitMix64(0L);
        return MarsagliaTsangWangDiscreteSampler.createDiscreteDistribution(rng, probabilities);
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
        final int[] prob = new int[6];
        prob[0] = 1;
        prob[1] = 1 + 1 << 6;
        prob[2] = 1 + 1 << 12;
        prob[3] = 1 + 1 << 18;
        prob[4] = 1 + 1 << 24;
        // Ensure probabilities sum to 2^30
        prob[5] = (1 << 30) - (prob[0] + prob[1] + prob[2] + prob[3] + prob[4]);

        // To hit all samples requires integers that are under the look-up table limits.
        // So compute the limits here.
        int n1 = 0;
        int n2 = 0;
        int n3 = 0;
        int n4 = 0;
        for (final int m : prob) {
            n1 += getBase64Digit(m, 1);
            n2 += getBase64Digit(m, 2);
            n3 += getBase64Digit(m, 3);
            n4 += getBase64Digit(m, 4);
        }

        final int t1 = n1 << 24;
        final int t2 = t1 + (n2 << 18);
        final int t3 = t2 + (n3 << 12);
        final int t4 = t3 + (n4 << 6);

        // Create values under the limits and bit shift by 2 to reverse what the sampler does.
        final int[] values = new int[] { 0, t1, t2, t3, t4, 0xffffffff };
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

        final double[] p1 = createProbabilities(offset1, prob);
        final double[] p2 = createProbabilities(offset2, prob);
        final double[] p3 = createProbabilities(offset3, prob);

        final MarsagliaTsangWangDiscreteSampler sampler1 = MarsagliaTsangWangDiscreteSampler.createDiscreteDistribution(rng1, p1);
        final MarsagliaTsangWangDiscreteSampler sampler2 = MarsagliaTsangWangDiscreteSampler.createDiscreteDistribution(rng2, p2);
        final MarsagliaTsangWangDiscreteSampler sampler3 = MarsagliaTsangWangDiscreteSampler.createDiscreteDistribution(rng3, p3);

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
     * Creates the probabilities using zero padding below the values.
     *
     * @param offset the offset
     * @param prob the probability values
     * @return the zero-padded probabilities
     */
    private static double[] createProbabilities(int offset, int[] prob) {
        double[] probabilities = new double[offset + prob.length];
        for (int i = 0; i < prob.length; i++) {
            probabilities[i + offset] = prob[i];
        }
        return probabilities;
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
        final MarsagliaTsangWangDiscreteSampler dummySampler = MarsagliaTsangWangDiscreteSampler.createDiscreteDistribution(dummyRng, probabilities);
        // This will throw if the table is incomplete as it hits the upper limit
        dummySampler.sample();

        // Do a test of the actual sampler
        final MarsagliaTsangWangDiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createDiscreteDistribution(rng, probabilities);

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
    @Test
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
    @Test
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
        final int d1 = getBase64Digit(m, 1);
        final int d2 = getBase64Digit(m, 2);
        final int d3 = getBase64Digit(m, 3);
        final int d4 = getBase64Digit(m, 4);
        final int d5 = getBase64Digit(m, 5);
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
     * Gets the k<sup>th</sup> base 64 digit of {@code m}.
     *
     * @param m the value m.
     * @param k the digit.
     * @return the base 64 digit
     */
    private static int getBase64Digit(int m, int k) {
        return (m >>> (30 - 6 * k)) & 63;
    }

    /**
     * Test the constructor with a bad mean.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreatePoissonDistributionThrowsWithMeanLargerThanUpperBound() {
        final UniformRandomProvider rng = new FixedRNG();
        final double mean = 1025;
        @SuppressWarnings("unused")
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createPoissonDistribution(rng, mean);
    }

    /**
     * Test the Poisson distribution with a bad mean.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreatePoissonDistributionThrowsWithZeroMean() {
        final UniformRandomProvider rng = new FixedRNG();
        final double mean = 0;
        @SuppressWarnings("unused")
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createPoissonDistribution(rng, mean);
    }

    /**
     * Test the Poisson distribution with the maximum mean.
     */
    @Test
    public void testCreatePoissonDistributionWithMaximumMean() {
        final UniformRandomProvider rng = new FixedRNG();
        final double mean = 1024;
        @SuppressWarnings("unused")
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createPoissonDistribution(rng, mean);
    }

    /**
     * Test the Poisson distribution with a small mean that hits the edge case where the
     * probability sum is not 2^30.
     */
    @Test
    public void testCreatePoissonDistributionWithSmallMean() {
        final UniformRandomProvider rng = new FixedRNG();
        final double mean = 0.25;
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createPoissonDistribution(rng, mean);
        // This will throw if the table does not sum to 2^30
        sampler.sample();
    }

    /**
     * Test the Poisson distribution with a medium mean that is at the switch point
     * for how the probability distribution is computed. This hits the edge case
     * where the loop from the mean decrements to reach zero.
     */
    @Test
    public void testCreatePoissonDistributionWithMediumMean() {
        final UniformRandomProvider rng = new FixedRNG();
        final double mean = 21.4;
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createPoissonDistribution(rng, mean);
        // This will throw if the table does not sum to 2^30
        sampler.sample();
    }

    /**
     * Test the Binomial distribution with a bad number of trials.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateBinomialDistributionThrowsWithTrialsBelow0() {
        final UniformRandomProvider rng = new FixedRNG();
        final int trials = -1;
        final double p = 0.5;
        @SuppressWarnings("unused")
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
    }

    /**
     * Test the Binomial distribution with an unsupported number of trials.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateBinomialDistributionThrowsWithTrialsAboveMax() {
        final UniformRandomProvider rng = new FixedRNG();
        final int trials = 1 << 16; // 2^16
        final double p = 0.5;
        @SuppressWarnings("unused")
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
    }

    /**
     * Test the Binomial distribution with probability {@code < 0}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateBinomialDistributionThrowsWithProbabilityBelow0() {
        final UniformRandomProvider rng = new FixedRNG();
        final int trials = 1;
        final double p = -0.5;
        @SuppressWarnings("unused")
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
    }

    /**
     * Test the Binomial distribution with probability {@code > 1}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateBinomialDistributionThrowsWithProbabilityAbove1() {
        final UniformRandomProvider rng = new FixedRNG();
        final int trials = 1;
        final double p = 1.5;
        @SuppressWarnings("unused")
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
    }

    /**
     * Test the Binomial distribution with distribution parameters that create a very small p(0)
     * with a high probability of success.
     */
    @Test
    public void testCreateBinomialDistributionWithSmallestP0ValueAndHighestProbabilityOfSuccess() {
        final UniformRandomProvider rng = new FixedRNG();
        // p(0) = Math.exp(trials * Math.log(1-p))
        // p(0) will be smaller as Math.log(1-p) is more negative, which occurs when p is
        // larger.
        // Since the sampler uses inversion the largest value for p is 0.5.
        // At the extreme for p = 0.5:
        // trials = Math.log(p(0)) / Math.log(1-p)
        // = Math.log(Double.MIN_VALUE) / Math.log(0.5)
        // = 1074
        final int trials = (int) Math.floor(Math.log(Double.MIN_VALUE) / Math.log(0.5));
        final double p = 0.5;
        // Validate set-up
        Assert.assertEquals("Invalid test set-up for p(0)", Double.MIN_VALUE, getBinomialP0(trials, p), 0);
        Assert.assertEquals("Invalid test set-up for p(0)", 0, getBinomialP0(trials + 1, p), 0);

        // This will throw if the table does not sum to 2^30
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
        sampler.sample();
    }

    /**
     * Test the Binomial distribution with distribution parameters that create a p(0)
     * that is zero (thus the distribution cannot be computed).
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateBinomialDistributionThrowsWhenP0IsZero() {
        final UniformRandomProvider rng = new FixedRNG();
        // As above but increase the trials so p(0) should be zero
        final int trials = 1 + (int) Math.floor(Math.log(Double.MIN_VALUE) / Math.log(0.5));
        final double p = 0.5;
        // Validate set-up
        Assert.assertEquals("Invalid test set-up for p(0)", 0, getBinomialP0(trials, p), 0);
        @SuppressWarnings("unused")
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
    }

    /**
     * Test the Binomial distribution with distribution parameters that create a very small p(0)
     * with a high number of trials.
     */
    @Test
    public void testCreateBinomialDistributionWithLargestTrialsAndSmallestProbabilityOfSuccess() {
        final UniformRandomProvider rng = new FixedRNG();
        // p(0) = Math.exp(trials * Math.log(1-p))
        // p(0) will be smaller as Math.log(1-p) is more negative, which occurs when p is
        // larger.
        // Since the sampler uses inversion the largest value for p is 0.5.
        // At the extreme for trials = 2^16-1:
        // p = 1 - Math.exp(Math.log(p(0)) / trials)
        // = 1 - Math.exp(Math.log(Double.MIN_VALUE) / trials)
        // = 0.011295152668039599
        final int trials = (1 << 16) - 1;
        double p = 1 - Math.exp(Math.log(Double.MIN_VALUE) / trials);

        // Validate set-up
        Assert.assertEquals("Invalid test set-up for p(0)", Double.MIN_VALUE, getBinomialP0(trials, p), 0);

        // Search for larger p until Math.nextAfter(p, 1) produces 0
        double upper = p * 2;
        Assert.assertEquals("Invalid test set-up for p(0)", 0, getBinomialP0(trials, upper), 0);

        double lower = p;
        while (Double.doubleToRawLongBits(lower) + 1 < Double.doubleToRawLongBits(upper)) {
            final double mid = (upper + lower) / 2;
            if (getBinomialP0(trials, mid) == 0) {
                upper = mid;
            } else {
                lower = mid;
            }
        }
        p = lower;

        // Re-validate
        Assert.assertEquals("Invalid test set-up for p(0)", Double.MIN_VALUE, getBinomialP0(trials, p), 0);
        Assert.assertEquals("Invalid test set-up for p(0)", 0, getBinomialP0(trials, Math.nextAfter(p, 1)), 0);

        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
        // This will throw if the table does not sum to 2^30
        sampler.sample();
    }

    /**
     * Gets the p(0) value for the Binomial distribution.
     *
     * @param trials the trials
     * @param probabilityOfSuccess the probability of success
     * @return the p(0) value
     */
    private static double getBinomialP0(int trials, double probabilityOfSuccess) {
        return Math.exp(trials * Math.log(1 - probabilityOfSuccess));
    }

    /**
     * Test the Binomial distribution with a probability of 0 where the sampler should equal 0.
     */
    @Test
    public void testCreateBinomialDistributionWithProbability0() {
        final UniformRandomProvider rng = new FixedRNG();
        final int trials = 1000000;
        final double p = 0;
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(0, sampler.sample());
        }
        // Hit the toString() method
        Assert.assertTrue(sampler.toString().contains("Binomial"));
    }

    /**
     * Test the Binomial distribution with a probability of 1 where the sampler should equal
     * the number of trials.
     */
    @Test
    public void testCreateBinomialDistributionWithProbability1() {
        final UniformRandomProvider rng = new FixedRNG();
        final int trials = 1000000;
        final double p = 1;
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(trials, sampler.sample());
        }
        // Hit the toString() method
        Assert.assertTrue(sampler.toString().contains("Binomial"));
    }

    /**
     * Test the sampler with a large number of trials. This tests the sampler can create the
     * Binomial distribution for a large size when a limiting distribution (e.g. the Normal distribution)
     * could be used instead.
     */
    @Test
    public void testCreateBinomialDistributionWithLargeNumberOfTrials() {
        final UniformRandomProvider rng = new FixedRNG();
        final int trials = 65000;
        final double p = 0.01;
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
        // This will throw if the table does not sum to 2^30
        sampler.sample();
    }

    /**
     * Test the sampler with a probability of 0.5. This should hit the edge case in the loop to
     * search for the last probability of the Binomial distribution.
     */
    @Test
    public void testCreateBinomialDistributionWithProbability0_5() {
        final UniformRandomProvider rng = new FixedRNG();
        final int trials = 10;
        final double p = 0.5;
        final DiscreteSampler sampler = MarsagliaTsangWangDiscreteSampler.createBinomialDistribution(rng, trials, p);
        // This will throw if the table does not sum to 2^30
        sampler.sample();
    }

    /**
     * Return a fixed sequence of {@code int} output.
     */
    private static class FixedSequenceIntProvider extends IntProvider {
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

    /**
     * A RNG returning a fixed {@code int} value with all the bits set.
     */
    private static class FixedRNG extends IntProvider {
        @Override
        public int next() {
            return 0xffffffff;
        }
    }

    ////////////////////////////////////////////////////
    // TODO: Delete the following from main branch code
    ////////////////////////////////////////////////////

    @Test
    public void testImplementations() {
        Long seed = 237648682L;
        double[] p = { 0.1, 0.2, 0.3, 0.4 };
        testImplementation(new MarsagliaTsangWangDiscreteSamplerOriginal(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSampler2D(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerByte(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerShort(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerByte2D(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerShort2D(new SplitMix64(seed), p), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerDynamic(new SplitMix64(seed), p, BaseOption.BASE_64), seed, p);
        testImplementation(new MarsagliaTsangWangDiscreteSamplerDynamicShort(new SplitMix64(seed), p, MarsagliaTsangWangDiscreteSamplerDynamicShort.BaseOption.BASE_64), seed, p);
    }

    private static void testImplementation(DiscreteSampler sampler, Long seed, double[] p) {
        final DiscreteSampler ref = 
                MarsagliaTsangWangDiscreteSampler.createDiscreteDistribution(new SplitMix64(seed), p);

        String msg = "Does not match reference: " + sampler.getClass().getSimpleName();
        for (int i = 0; i < 1000; i++) {
            Assert.assertEquals(msg, ref.sample(), sampler.sample());
        }

    }

    //@Test
    public void testMemoryFootPrint() {
        SplitMix64 rng = new SplitMix64(0L);
        for (int length : new int[] { 4, 8, 16, 32, 64, 128 }) {
            final double[] probabilities = new double[length];
            for (int i = 0; i < probabilities.length; i++) {
                probabilities[i] = (i + 1.0) / probabilities.length;
            }
            //new MarsagliaTsangWangDiscreteSamplerOriginal(rng, probabilities);
            new MarsagliaTsangWangDiscreteSamplerBase10(rng, probabilities);
        }
    }
}
