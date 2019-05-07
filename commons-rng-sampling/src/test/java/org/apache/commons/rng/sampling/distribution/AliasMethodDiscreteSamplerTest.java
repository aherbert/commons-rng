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

import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Test for the {@link AliasMethodDiscreteSampler}.
 */
public class AliasMethodDiscreteSamplerTest {
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

    @Test
    public void testToString() {
        final AliasMethodDiscreteSampler sampler = createSampler(new double[] { 0.5, 0.5 });
        Assert.assertTrue(sampler.toString().toLowerCase().contains("alias method"));
    }

    /**
     * Creates the sampler.
     *
     * @param probabilities the probabilities
     * @return the alias method discrete sampler
     */
    private static AliasMethodDiscreteSampler createSampler(double[] probabilities) {
        final UniformRandomProvider rng = RandomSource.create(RandomSource.SPLIT_MIX_64);
        return new AliasMethodDiscreteSampler(rng, probabilities);
    }

    /**
     * Test the code example for how to compute the next power of 2. The example is part of the
     * Javadoc of the class describing how to pad to a power of 2.
     */
    @Test
    public void testNextPowerOf2() {
        // 2^0 is 1 so this is the next power-of-two size for zero
        Assert.assertEquals(1, nextPowerOf2(0));

        // Special case for first powers as the previous number is also a power of 2 so cannot be
        // tested in the loop below.
        Assert.assertEquals(1, nextPowerOf2(1));
        Assert.assertEquals(2, nextPowerOf2(2));

        // Test all remaining powers
        for (int pow = 2; pow < 30; pow++) {
            final int expected = 1 << pow;
            Assert.assertEquals(expected, nextPowerOf2(1 + expected / 2));
            Assert.assertEquals(expected, nextPowerOf2(expected - 1));
            Assert.assertEquals(expected, nextPowerOf2(expected));
        }

        // Random points
        Assert.assertEquals(256, nextPowerOf2(137));
        Assert.assertEquals(1024, nextPowerOf2(888));

        // Test upper bounds return the same value
        for (int expected : new int[] {Integer.MAX_VALUE, (1 << 30) + 1}) {
            Assert.assertEquals(expected, nextPowerOf2(expected));
        }
    }

    /**
     * Returns the closest power-of-two number greater than or equal to value.
     * This works for all positive integers up to 2^30. Above this the input size will be returned.
     *
     * @param size the size
     * @return 2^ceil(log<sub>2</sub>(size)), or size, whichever is larger due to integer overflow
     */
    private static int nextPowerOf2(int size) {
        final int pow2 = 32 - Integer.numberOfLeadingZeros(size - 1);
        // Use max to handle overflow of 2^31
        return Math.max(size, 1 << pow2);
    }

    /**
     * Test sampling from a binomial distribution.
     */
    @Test
    public void testBinomialSamples() {
        final int trials = 67;
        final double probabilityOfSuccess = 0.345;
        final BinomialDistribution dist = new BinomialDistribution(trials, probabilityOfSuccess);
        final double[] expected = new double[trials + 1];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = dist.probability(i);
        }
        checkSamples(expected);
    }

    /**
     * Test sampling from a Poisson distribution.
     */
    @Test
    public void testPoissonSamples() {
        final double mean = 3.14;
        final PoissonDistribution dist = new PoissonDistribution(null, mean,
            PoissonDistribution.DEFAULT_EPSILON, PoissonDistribution.DEFAULT_MAX_ITERATIONS);
        final int maxN = dist.inverseCumulativeProbability(1 - 1e-6);
        double[] expected = new double[maxN];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = dist.probability(i);
        }
        expected = Arrays.copyOf(expected, 2048);
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of probabilities (these sum to 1).
     */
    @Test
    public void testNonUniformSamplesWithProbabilities() {
        final double[] expected = { 0.1, 0.2, 0.3, 0.1, 0.3 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of observations (i.e. the sum is not 1 as per
     * probabilities).
     */
    @Test
    public void testNonUniformSamplesWithObservations() {
        final double[] expected = { 1, 2, 3, 1, 3 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of probabilities (these sum to 1).
     * Extra zero-values are added to make the table size a power of 2.
     */
    @Test
    public void testNonUniformSamplesWithProbabilitiesPaddedToPowerOf2() {
        final double[] expected = { 0.1, 0, 0.2, 0.3, 0.1, 0.3, 0, 0 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of observations (i.e. the sum is not 1 as per
     * probabilities). Extra zero-values are added to make the table size a power of 2.
     */
    @Test
    public void testNonUniformSamplesWithObservationsPaddedToPowerOf2() {
        final double[] expected = { 1, 2, 3, 0, 1, 3, 0, 0 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of probabilities (these sum to 1).
     * Extra zero-values are added.
     */
    @Test
    public void testNonUniformSamplesWithZeroProbabilities() {
        final double[] expected = { 0.1, 0, 0.2, 0.3, 0.1, 0.3, 0 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of observations (i.e. the sum is not 1 as per
     * probabilities). Extra zero-values are added.
     */
    @Test
    public void testNonUniformSamplesWithZeroObservations() {
        final double[] expected = { 1, 2, 3, 0, 1, 3, 0 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a uniform distribution. This is an edge case where there
     * are no probabilities less than the mean.
     */
    @Test
    public void testUniformSamplesWithNoObservationLessThanTheMean() {
        final double[] expected = { 2, 2, 2, 2, 2, 2 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution. This is an edge case where there
     * are no probabilities less than the mean.
     */
    @Test
    public void testLargeTableSize() {
        double[] expected = { 0.1, 0.2, 0.3, 0.1, 0.3 };
        // Pad to a large table size not supported for fast sampling (anything >= 2^11)
        expected = Arrays.copyOf(expected, (1 << 11));
        checkSamples(expected);
    }

    /**
     * Check the distribution of samples match the expected probabilities.
     *
     * @param expected the expected probabilities
     */
    private static void checkSamples(double[] probabilies) {
        final AliasMethodDiscreteSampler sampler = createSampler(probabilies);

        final int numberOfSamples = 10000;
        final long[] samples = new long[probabilies.length];
        for (int i = 0; i < numberOfSamples; i++) {
            samples[sampler.sample()]++;
        }

        // Handle a test with some zero-probability observations by mapping them out
        int mapSize = 0;
        for (int i = 0; i < probabilies.length; i++) {
            if (probabilies[i] != 0) {
                mapSize++;
            }
        }

        double[] expected = new double[mapSize];
        long[] observed = new long[mapSize];
        for (int i = 0; i < probabilies.length; i++) {
            if (probabilies[i] != 0) {
                --mapSize;
                expected[mapSize] = probabilies[i];
                observed[mapSize] = samples[i];
            } else {
                Assert.assertEquals("No samples expected from zero probability", 0, samples[i]);
            }
        }

        final ChiSquareTest chiSquareTest = new ChiSquareTest();
        // Pass if we cannot reject null hypothesis that the distributions are the same.
        Assert.assertFalse(chiSquareTest.chiSquareTest(expected, observed, 0.001));
    }
}
