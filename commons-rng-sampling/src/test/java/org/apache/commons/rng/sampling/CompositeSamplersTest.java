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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.CompositeSamplers.Builder;
import org.apache.commons.rng.sampling.CompositeSamplers.DiscreteProbabilitySampler;
import org.apache.commons.rng.sampling.CompositeSamplers.DiscreteProbabilitySamplerFactory;
import org.apache.commons.rng.sampling.distribution.AliasMethodDiscreteSampler;
import org.apache.commons.rng.sampling.distribution.ContinuousSampler;
import org.apache.commons.rng.sampling.distribution.DiscreteSampler;
import org.apache.commons.rng.sampling.distribution.GuideTableDiscreteSampler;
import org.apache.commons.rng.sampling.distribution.SharedStateContinuousSampler;
import org.apache.commons.rng.sampling.distribution.SharedStateDiscreteSampler;
import org.apache.commons.rng.simple.RandomSource;

/**
 * Test class for {@link CompositeSamplers}.
 */
public class CompositeSamplersTest {
    /**
     * Test the default implementations of the discrete probability sampler factory.
     */
    @Test
    public void testDiscreteProbabilitySampler() {
        final UniformRandomProvider rng = RandomSource.create(RandomSource.MWC_256, 78979L);
        final double[] probabilities = {0.1, 0.2, 0.3, 0.4};
        final double mean = 0.2 + 2 * 0.3 + 3 * 0.4;
        final int n = 1000000;
        for (final DiscreteProbabilitySampler item : DiscreteProbabilitySampler.values()) {
            final DiscreteSampler sampler = item.create(rng, probabilities.clone());
            long sum = 0;
            for (int i = 0; i < n; i++) {
                sum += sampler.sample();
            }
            Assert.assertEquals(item.name(), mean, (double) sum / n, 1e-3);
        }
    }

    /**
     * Test an empty builder cannot build a sampler.
     */
    @Test(expected = IllegalStateException.class)
    public void testEmptyBuilderThrows() {
        final UniformRandomProvider rng = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        final Builder<SharedStateObjectSampler<Integer>> builder = CompositeSamplers
                .newSharedStateObjectSamplerBuilder();
        Assert.assertEquals(0, builder.size());
        builder.build(rng);
    }

    /**
     * Test adding null sampler to a builder.
     */
    @Test(expected = NullPointerException.class)
    public void testNullSharedStateObjectSamplerThrows() {
        final Builder<SharedStateObjectSampler<Integer>> builder = CompositeSamplers
                .newSharedStateObjectSamplerBuilder();
        builder.add(null, 1.0);
    }

    /**
     * Test invalid weights (zero, negative, NaN, infinte).
     */
    @Test
    public void testInvalidWeights() {
        final UniformRandomProvider rng = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        final Builder<SharedStateObjectSampler<Integer>> builder = CompositeSamplers
                .newSharedStateObjectSamplerBuilder();
        final RangeSampler sampler = new RangeSampler(45, 63, rng);
        // Zero weight is ignored
        Assert.assertEquals(0, builder.size());
        builder.add(sampler, 0.0);
        Assert.assertEquals(0, builder.size());

        final double[] bad = {-1, Double.NaN, Double.POSITIVE_INFINITY};
        for (final double weight : bad) {
            try {
                builder.add(sampler, weight);
                Assert.fail("Did not detect invalid weight: " + weight);
            } catch (IllegalArgumentException ex) {
                // Expected
            }
        }
    }

    /**
     * Test a single sampler added to the builder is returned without a composite.
     */
    @Test
    public void testSingleSharedStateObjectSampler() {
        final UniformRandomProvider rng = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        final Builder<SharedStateObjectSampler<Integer>> builder = CompositeSamplers
                .newSharedStateObjectSamplerBuilder();
        final RangeSampler sampler = new RangeSampler(45, 63, rng);
        builder.add(sampler, 1.0);
        Assert.assertEquals(1, builder.size());
        final SharedStateObjectSampler<Integer> composite = builder.build(rng);
        Assert.assertSame(sampler, composite);
    }

    /**
     * Test sampling is uniform across several ObjectSampler samplers.
     */
    @Test
    public void testObjectSamplerSamples() {
        final Builder<ObjectSampler<Integer>> builder = CompositeSamplers.newObjectSamplerBuilder();
        final UniformRandomProvider rng = RandomSource.create(RandomSource.PCG_XSH_RR_32_OS, 345);
        final int n = 15;
        final int min = -134;
        final int max = 2097;
        addObjectSamplers(builder, n, min, max, rng);
        assertObjectSamplerSamples(builder.build(rng), min, max);
    }

    /**
     * Test sampling is uniform across several SharedStateObjectSampler samplers.
     */
    @Test
    public void testSharedStateObjectSamplerSamples() {
        final Builder<SharedStateObjectSampler<Integer>> builder = CompositeSamplers
                .newSharedStateObjectSamplerBuilder();
        final UniformRandomProvider rng = RandomSource.create(RandomSource.PCG_XSH_RS_32_OS, 299);
        final int n = 11;
        final int min = 42;
        final int max = 678;
        addObjectSamplers(builder, n, min, max, rng);
        assertObjectSamplerSamples(builder.build(rng), min, max);
    }

    /**
     * Test sampling is uniform across several SharedStateObjectSampler samplers
     * using a custom factory that implements SharedStateDiscreteSampler.
     */
    @Test
    public void testSharedStateObjectSamplerSamplesWithCustomSharedStateDiscreteSamplerFactory() {
        final Builder<SharedStateObjectSampler<Integer>> builder = CompositeSamplers
                .newSharedStateObjectSamplerBuilder();
        final AtomicInteger factoryCount = new AtomicInteger();
        builder.setFactory(new DiscreteProbabilitySamplerFactory() {
            @Override
            public DiscreteSampler create(UniformRandomProvider rng, double[] probabilities) {
                factoryCount.incrementAndGet();
                // Use an expanded table with a non-default alpha
                return AliasMethodDiscreteSampler.of(rng, probabilities, 2);
            }
        });
        final UniformRandomProvider rng = RandomSource.create(RandomSource.XO_SHI_RO_128_PP, 0xa6b7c9);
        final int n = 7;
        final int min = -610;
        final int max = 745;
        addObjectSamplers(builder, n, min, max, rng);

        // Exercise the shared state interface
        final UniformRandomProvider rng1 = RandomSource.create(RandomSource.XO_SHI_RO_256_PLUS, 0x1f2e3d);
        assertObjectSamplerSamples(builder.build(rng).withUniformRandomProvider(rng1), min, max);

        Assert.assertEquals("Factory should not be used to create the shared state sampler", 1, factoryCount.get());
    }

    /**
     * Test sampling is uniform across several SharedStateObjectSampler samplers
     * using a custom factory that implements DiscreteSampler (so must be wrapped).
     */
    @Test
    public void testSharedStateObjectSamplerSamplesWithCustomDiscreteSamplerFactory() {
        final Builder<SharedStateObjectSampler<Integer>> builder = CompositeSamplers
                .newSharedStateObjectSamplerBuilder();
        final AtomicInteger factoryCount = new AtomicInteger();
        builder.setFactory(new DiscreteProbabilitySamplerFactory() {
            @Override
            public DiscreteSampler create(UniformRandomProvider rng, double[] probabilities) {
                factoryCount.incrementAndGet();
                // Wrap so it is not a SharedStateSamplerInstance.
                final DiscreteSampler sampler = GuideTableDiscreteSampler.of(rng, probabilities, 2);
                // Destroy the probabilities to check that custom factories are not trusted.
                Arrays.fill(probabilities, Double.NaN);
                return new DiscreteSampler() {
                    @Override
                    public int sample() {
                        return sampler.sample();
                    }
                };
            }
        });
        final UniformRandomProvider rng = RandomSource.create(RandomSource.XO_SHI_RO_128_PP, 0x263478628L);
        final int n = 14;
        final int min = 56;
        final int max = 2033;
        addObjectSamplers(builder, n, min, max, rng);

        // Exercise the shared state interface.
        // This tests the custom factory is used twice.
        final UniformRandomProvider rng1 = RandomSource.create(RandomSource.XO_SHI_RO_256_PLUS, 0x8c7b6a);
        assertObjectSamplerSamples(builder.build(rng).withUniformRandomProvider(rng1), min, max);

        Assert.assertEquals("Factory should be used to create the shared state sampler", 2, factoryCount.get());
    }

    /**
     * Test sampling is uniform across several ObjectSampler samplers with a uniform
     * weighting. This tests an edge case where there is no requirement for a
     * sampler from a discrete probability distribution as the distribution is
     * uniform.
     */
    @Test
    public void testObjectSamplerSamplesWithUniformWeights() {
        final Builder<ObjectSampler<Integer>> builder = CompositeSamplers.newObjectSamplerBuilder();
        final UniformRandomProvider rng = RandomSource.create(RandomSource.JSF_64, 678345);
        final int max = 60;
        final int interval = 10;
        for (int min = 0; min < max; min += interval) {
            builder.add(new RangeSampler(min, min + interval, rng), 1.0);
        }
        assertObjectSamplerSamples(builder.build(rng), 0, max);
    }

    /**
     * Test sampling is uniform across several ObjectSampler samplers with very
     * large weights. This tests an edge case where the weights with sum to
     * infinity.
     */
    @Test
    public void testObjectSamplerSamplesWithVeryLargeWeights() {
        final Builder<ObjectSampler<Integer>> builder = CompositeSamplers.newObjectSamplerBuilder();
        final UniformRandomProvider rng = RandomSource.create(RandomSource.SFC_64, 267934293);
        // Ratio 4:4:2:1
        // The weights will sum to infinity as they are more than 2^1024.
        final double w4 = 0x1.0p1023;
        final double w2 = 0x1.0p1022;
        final double w1 = 0x1.0p1021;
        Assert.assertEquals(Double.POSITIVE_INFINITY, w4 + w4 + w2 + w1, 0.0);
        builder.add(new RangeSampler(0, 40, rng), w4);
        builder.add(new RangeSampler(40, 80, rng), w4);
        builder.add(new RangeSampler(80, 100, rng), w2);
        builder.add(new RangeSampler(100, 110, rng), w1);
        assertObjectSamplerSamples(builder.build(rng), 0, 110);
    }

    /**
     * Test sampling is uniform across several ObjectSampler samplers with very
     * small weights. This tests an edge case where the weights divided by their sum
     * are valid (due to accurate floating-point division) but cannot be multiplied
     * by the reciprocal of the sum.
     */
    @Test
    public void testObjectSamplerSamplesWithSubNormalWeights() {
        final Builder<ObjectSampler<Integer>> builder = CompositeSamplers.newObjectSamplerBuilder();
        final UniformRandomProvider rng = RandomSource.create(RandomSource.MSWS, 6786);
        // Ratio 4:4:2:1
        // The weights are very small sub-normal numbers
        final double w4 = Double.MIN_VALUE * 4;
        final double w2 = Double.MIN_VALUE * 2;
        final double w1 = Double.MIN_VALUE;
        final double sum = w4 + w4 + w2 + w1;
        // Cannot do a divide by multiplying by the reciprocal
        Assert.assertEquals(Double.POSITIVE_INFINITY, 1.0 / sum, 0.0);
        // A divide works so the sampler should work
        Assert.assertEquals(4.0 / 11, w4 / sum, 0.0);
        Assert.assertEquals(2.0 / 11, w2 / sum, 0.0);
        Assert.assertEquals(1.0 / 11, w1 / sum, 0.0);
        builder.add(new RangeSampler(0, 40, rng), w4);
        builder.add(new RangeSampler(40, 80, rng), w4);
        builder.add(new RangeSampler(80, 100, rng), w2);
        builder.add(new RangeSampler(100, 110, rng), w1);
        assertObjectSamplerSamples(builder.build(rng), 0, 110);
    }

    /**
     * Add samplers to the builder that sample from contiguous ranges between the
     * minimum and maximum. Note: {@code max - min >= n}
     *
     * @param builder the builder
     * @param n the number of samplers (must be {@code >= 2})
     * @param min the minimum (inclusive)
     * @param max the maximum (exclusive)
     * @param rng the source of randomness
     */
    private static void addObjectSamplers(Builder<? super SharedStateObjectSampler<Integer>> builder, int n, int min,
            int max, UniformRandomProvider rng) {
        // Create the ranges using n-1 random ticks in the range (min, max),
        // adding the limits and then sorting in ascending order.
        // The samplers are then constructed:
        //
        // min-------A-----B----max
        // Sampler 1 = [min, A)
        // Sampler 2 = [A, B)
        // Sampler 3 = [B, max)

        // Use a combination sampler to ensure the ticks are unique in the range.
        // This will throw if the range is negative.
        final int range = max - min - 1;
        int[] ticks = new CombinationSampler(rng, range, n - 1).sample();
        // Shift the ticks into the range
        for (int i = 0; i < ticks.length; i++) {
            ticks[i] += min + 1;
        }
        // Add the min and max
        ticks = Arrays.copyOf(ticks, n + 1);
        ticks[n - 1] = min;
        ticks[n] = max;
        Arrays.sort(ticks);

        // Sample within the ranges between the ticks
        final int before = builder.size();
        for (int i = 1; i < ticks.length; i++) {
            final RangeSampler sampler = new RangeSampler(ticks[i - 1], ticks[i], rng);
            // Weight using the range
            builder.add(sampler, sampler.range);
        }

        Assert.assertEquals("Failed to add the correct number of samplers", n, builder.size() - before);
    }

    /**
     * Assert sampling is uniform between the minimum and maximum.
     *
     * @param sampler the sampler
     * @param min the minimum (inclusive)
     * @param max the maximum (exclusive)
     */
    private static void assertObjectSamplerSamples(ObjectSampler<Integer> sampler, int min, int max) {
        final int n = 100000;
        final long[] observed = new long[max - min];
        for (int i = 0; i < n; i++) {
            observed[sampler.sample() - min]++;
        }

        final double[] expected = new double[observed.length];
        Arrays.fill(expected, (double) n / expected.length);
        final double p = new ChiSquareTest().chiSquareTest(expected, observed);
        Assert.assertFalse("p-value too small: " + p, p < 0.001);
    }

    /**
     * Test sampling is uniform across several DiscreteSampler samplers.
     */
    @Test
    public void testDiscreteSamplerSamples() {
        final Builder<DiscreteSampler> builder = CompositeSamplers.newDiscreteSamplerBuilder();
        final UniformRandomProvider rng = RandomSource.create(RandomSource.PCG_XSH_RR_32_OS, 345);
        final int n = 15;
        final int min = -134;
        final int max = 2097;
        addDiscreteSamplers(builder, n, min, max, rng);
        assertDiscreteSamplerSamples(builder.build(rng), min, max);
    }

    /**
     * Test sampling is uniform across several SharedStateDiscreteSampler samplers.
     */
    @Test
    public void testSharedStateDiscreteSamplerSamples() {
        final Builder<SharedStateDiscreteSampler> builder = CompositeSamplers.newSharedStateDiscreteSamplerBuilder();
        final UniformRandomProvider rng = RandomSource.create(RandomSource.PCG_XSH_RS_32_OS, 299);
        final int n = 11;
        final int min = 42;
        final int max = 678;
        addDiscreteSamplers(builder, n, min, max, rng);
        assertDiscreteSamplerSamples(builder.build(rng), min, max);
    }

    /**
     * Add samplers to the builder that sample from contiguous ranges between the
     * minimum and maximum. Note: {@code max - min >= n}
     *
     * @param builder the builder
     * @param n the number of samplers (must be {@code >= 2})
     * @param min the minimum (inclusive)
     * @param max the maximum (exclusive)
     * @param rng the source of randomness
     */
    private static void addDiscreteSamplers(Builder<? super SharedStateDiscreteSampler> builder, int n, int min,
            int max, UniformRandomProvider rng) {
        // Create the ranges using n-1 random ticks in the range (min, max),
        // adding the limits and then sorting in ascending order.
        // The samplers are then constructed:
        //
        // min-------A-----B----max
        // Sampler 1 = [min, A)
        // Sampler 2 = [A, B)
        // Sampler 3 = [B, max)

        // Use a combination sampler to ensure the ticks are unique in the range.
        // This will throw if the range is negative.
        final int range = max - min - 1;
        int[] ticks = new CombinationSampler(rng, range, n - 1).sample();
        // Shift the ticks into the range
        for (int i = 0; i < ticks.length; i++) {
            ticks[i] += min + 1;
        }
        // Add the min and max
        ticks = Arrays.copyOf(ticks, n + 1);
        ticks[n - 1] = min;
        ticks[n] = max;
        Arrays.sort(ticks);

        // Sample within the ranges between the ticks
        final int before = builder.size();
        for (int i = 1; i < ticks.length; i++) {
            final IntRangeSampler sampler = new IntRangeSampler(ticks[i - 1], ticks[i], rng);
            // Weight using the range
            builder.add(sampler, sampler.range);
        }

        Assert.assertEquals("Failed to add the correct number of samplers", n, builder.size() - before);
    }

    /**
     * Assert sampling is uniform between the minimum and maximum.
     *
     * @param sampler the sampler
     * @param min the minimum (inclusive)
     * @param max the maximum (exclusive)
     */
    private static void assertDiscreteSamplerSamples(DiscreteSampler sampler, int min, int max) {
        final int n = 100000;
        final long[] observed = new long[max - min];
        for (int i = 0; i < n; i++) {
            observed[sampler.sample() - min]++;
        }

        final double[] expected = new double[observed.length];
        Arrays.fill(expected, (double) n / expected.length);
        final double p = new ChiSquareTest().chiSquareTest(expected, observed);
        Assert.assertFalse("p-value too small: " + p, p < 0.001);
    }

    /**
     * Test sampling is uniform across several ContinuousSampler samplers.
     */
    @Test
    public void testContinuousSamplerSamples() {
        final Builder<ContinuousSampler> builder = CompositeSamplers.newContinuousSamplerBuilder();
        final UniformRandomProvider rng = RandomSource.create(RandomSource.XO_SHI_RO_256_PP, 9283756);
        final int n = 15;
        final double min = 67.2;
        final double max = 2033.8;
        addContinuousSamplers(builder, n, min, max, rng);
        assertContinuousSamplerSamples(builder.build(rng), min, max);
    }

    /**
     * Test sampling is uniform across several SharedStateContinuousSampler samplers.
     */
    @Test
    public void testSharedStateContinuousSamplerSamples() {
        final Builder<SharedStateContinuousSampler> builder = CompositeSamplers
                .newSharedStateContinuousSamplerBuilder();
        final UniformRandomProvider rng = RandomSource.create(RandomSource.PCG_RXS_M_XS_64_OS, 0x567567345L);
        final int n = 11;
        final double min = -15.7;
        final double max = 123.4;
        addContinuousSamplers(builder, n, min, max, rng);
        assertContinuousSamplerSamples(builder.build(rng), min, max);
    }

    /**
     * Add samplers to the builder that sample from contiguous ranges between the
     * minimum and maximum. Note: {@code max - min >= n}
     *
     * @param builder the builder
     * @param n the number of samplers (must be {@code >= 2})
     * @param min the minimum (inclusive)
     * @param max the maximum (exclusive)
     * @param rng the source of randomness
     */
    private static void addContinuousSamplers(Builder<? super SharedStateContinuousSampler> builder, int n, double min,
            double max, UniformRandomProvider rng) {
        // Create the ranges using n-1 random ticks in the range (min, max),
        // adding the limits and then sorting in ascending order.
        // The samplers are then constructed:
        //
        // min-------A-----B----max
        // Sampler 1 = [min, A)
        // Sampler 2 = [A, B)
        // Sampler 3 = [B, max)

        // For double values it is extremely unlikely the same value will be generated.
        // An assertion is performed to ensure we create the correct number of samplers.
        DoubleRangeSampler sampler = new DoubleRangeSampler(min, max, rng);
        final double[] ticks = new double[n + 1];
        ticks[0] = min;
        ticks[1] = max;
        // Shift the ticks into the range
        for (int i = 2; i < ticks.length; i++) {
            ticks[i] = sampler.sample();
        }
        Arrays.sort(ticks);

        // Sample within the ranges between the ticks
        final int before = builder.size();
        for (int i = 1; i < ticks.length; i++) {
            sampler = new DoubleRangeSampler(ticks[i - 1], ticks[i], rng);
            // Weight using the range
            builder.add(sampler, sampler.range());
        }

        Assert.assertEquals("Failed to add the correct number of samplers", n, builder.size() - before);
    }

    /**
     * Assert sampling is uniform between the minimum and maximum.
     *
     * @param sampler the sampler
     * @param min the minimum (inclusive)
     * @param max the maximum (exclusive)
     */
    private static void assertContinuousSamplerSamples(ContinuousSampler sampler, double min, double max) {
        final int n = 100000;
        final int bins = 200;
        final long[] observed = new long[bins];
        final double scale = bins / (max - min);
        for (int i = 0; i < n; i++) {
            // scale the sampler into a bin within the range:
            // bin = bins * (x - min) / (max - min)
            observed[(int) (scale * (sampler.sample() - min))]++;
        }

        final double[] expected = new double[observed.length];
        Arrays.fill(expected, (double) n / expected.length);
        final double p = new ChiSquareTest().chiSquareTest(expected, observed);
        Assert.assertFalse("p-value too small: " + p, p < 0.001);
    }

    /**
     * Test the SharedStateSampler implementation for the composite
     * SharedStateObjectSampler.
     */
    @Test
    public void testSharedStateObjectSampler() {
        testSharedStateObjectSampler(false);
    }

    /**
     * Test the SharedStateSampler implementation for the composite
     * SharedStateObjectSampler with a factory that does not support a shared state sampler.
     */
    @Test
    public void testSharedStateObjectSamplerWithCustomFactory() {
        testSharedStateObjectSampler(true);
    }

    /**
     * Test the SharedStateSampler implementation for the composite
     * SharedStateObjectSampler.
     *
     * @param customFactory Set to true to use a custom discrete sampler factory that does not
     * support a shared stated sampler.
     */
    private static void testSharedStateObjectSampler(boolean customFactory) {
        final UniformRandomProvider rng1 = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        final UniformRandomProvider rng2 = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);

        final Builder<SharedStateObjectSampler<Integer>> builder = CompositeSamplers
                .newSharedStateObjectSamplerBuilder();

        if (customFactory) {
            addFactoryWithNoSharedStateSupport(builder);
        }

        // Sample within the ranges between the ticks
        final int[] ticks = {6, 13, 42, 99};
        for (int i = 1; i < ticks.length; i++) {
            final RangeSampler sampler = new RangeSampler(ticks[i - 1], ticks[i], rng1);
            // Weight using the range
            builder.add(sampler, sampler.range);
        }

        final SharedStateObjectSampler<Integer> sampler1 = builder.build(rng1);
        final SharedStateObjectSampler<Integer> sampler2 = sampler1.withUniformRandomProvider(rng2);
        RandomAssert.assertProduceSameSequence(new RandomAssert.Sampler<Integer>() {
            @Override
            public Integer sample() {
                return sampler1.sample();
            }
        }, new RandomAssert.Sampler<Integer>() {
            @Override
            public Integer sample() {
                return sampler2.sample();
            }
        });
    }

    /**
     * Test the SharedStateSampler implementation for the composite
     * SharedStateDiscreteSampler.
     */
    @Test
    public void testSharedStateDiscreteSampler() {
        testSharedStateDiscreteSampler(false);
    }

    /**
     * Test the SharedStateSampler implementation for the composite
     * SharedStateDiscreteSampler with a factory that does not support a shared state sampler.
     */
    @Test
    public void testSharedStateDiscreteSamplerWithCustomFactory() {
        testSharedStateDiscreteSampler(true);
    }

    /**
     * Test the SharedStateSampler implementation for the composite
     * SharedStateDiscreteSampler.
     *
     * @param customFactory Set to true to use a custom discrete sampler factory that does not
     * support a shared stated sampler.
     */
    private static void testSharedStateDiscreteSampler(boolean customFactory) {
        final UniformRandomProvider rng1 = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        final UniformRandomProvider rng2 = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);

        final Builder<SharedStateDiscreteSampler> builder = CompositeSamplers.newSharedStateDiscreteSamplerBuilder();

        if (customFactory) {
            addFactoryWithNoSharedStateSupport(builder);
        }

        // Sample within the ranges between the ticks
        final int[] ticks = {-3, 5, 14, 22};
        for (int i = 1; i < ticks.length; i++) {
            final IntRangeSampler sampler = new IntRangeSampler(ticks[i - 1], ticks[i], rng1);
            // Weight using the range
            builder.add(sampler, sampler.range);
        }

        final SharedStateDiscreteSampler sampler1 = builder.build(rng1);
        final SharedStateDiscreteSampler sampler2 = sampler1.withUniformRandomProvider(rng2);
        RandomAssert.assertProduceSameSequence(new RandomAssert.Sampler<Integer>() {
            @Override
            public Integer sample() {
                return sampler1.sample();
            }
        }, new RandomAssert.Sampler<Integer>() {
            @Override
            public Integer sample() {
                return sampler2.sample();
            }
        });
    }

    /**
     * Test the SharedStateSampler implementation for the composite
     * SharedStateContinuousSampler.
     */
    @Test
    public void testSharedStateContinuousSampler() {
        testSharedStateContinuousSampler(false);
    }

    /**
     * Test the SharedStateSampler implementation for the composite
     * SharedStateContinuousSampler with a factory that does not support a shared state sampler.
     */
    @Test
    public void testSharedStateContinuousSamplerWithCustomFactory() {
        testSharedStateContinuousSampler(true);
    }

    /**
     * Test the SharedStateSampler implementation for the composite
     * SharedStateContinuousSampler.
     *
     * @param customFactory Set to true to use a custom discrete sampler factory that does not
     * support a shared stated sampler.
     */
    private static void testSharedStateContinuousSampler(boolean customFactory) {
        final UniformRandomProvider rng1 = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        final UniformRandomProvider rng2 = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);

        final Builder<SharedStateContinuousSampler> builder = CompositeSamplers
                .newSharedStateContinuousSamplerBuilder();

        if (customFactory) {
            addFactoryWithNoSharedStateSupport(builder);
        }

        // Sample within the ranges between the ticks
        final double[] ticks = {7.89, 13.99, 21.7, 35.6, 45.5};
        for (int i = 1; i < ticks.length; i++) {
            final DoubleRangeSampler sampler = new DoubleRangeSampler(ticks[i - 1], ticks[i], rng1);
            // Weight using the range
            builder.add(sampler, sampler.range());
        }

        final SharedStateContinuousSampler sampler1 = builder.build(rng1);
        final SharedStateContinuousSampler sampler2 = sampler1.withUniformRandomProvider(rng2);
        RandomAssert.assertProduceSameSequence(new RandomAssert.Sampler<Double>() {
            @Override
            public Double sample() {
                return sampler1.sample();
            }
        }, new RandomAssert.Sampler<Double>() {
            @Override
            public Double sample() {
                return sampler2.sample();
            }
        });
    }

    /**
     * Adds a DiscreteSamplerFactory to the builder that creates samplers that do not share state.
     *
     * @param builder the builder
     */
    private static void addFactoryWithNoSharedStateSupport(Builder<?> builder) {
        builder.setFactory(new DiscreteProbabilitySamplerFactory() {
            @Override
            public DiscreteSampler create(UniformRandomProvider rng, double[] probabilities) {
                // Wrap so it is not a SharedStateSamplerInstance.
                final DiscreteSampler sampler = GuideTableDiscreteSampler.of(rng, probabilities, 2);
                // Destroy the probabilities to check that custom factories are not trusted.
                Arrays.fill(probabilities, Double.NaN);
                return new DiscreteSampler() {
                    @Override
                    public int sample() {
                        return sampler.sample();
                    }
                };
            }
        });
    }

    /**
     * Sample an object {@code Integer} from a range.
     */
    private static class RangeSampler implements SharedStateObjectSampler<Integer> {
        private final int min;
        private final int range;
        private final UniformRandomProvider rng;

        /**
         * @param min the minimum (inclusive)
         * @param max the maximum (exclusive)
         * @param rng the source of randomness
         */
        RangeSampler(int min, int max, UniformRandomProvider rng) {
            this.min = min;
            this.range = max - min;
            this.rng = rng;
        }

        @Override
        public Integer sample() {
            return min + rng.nextInt(range);
        }

        @Override
        public SharedStateObjectSampler<Integer> withUniformRandomProvider(UniformRandomProvider generator) {
            return new RangeSampler(min, min + range, generator);
        }
    }

    /**
     * Sample a primitive {@code integer} from a range.
     */
    private static class IntRangeSampler implements SharedStateDiscreteSampler {
        private final int min;
        private final int range;
        private final UniformRandomProvider rng;

        /**
         * @param min the minimum (inclusive)
         * @param max the maximum (exclusive)
         * @param rng the source of randomness
         */
        IntRangeSampler(int min, int max, UniformRandomProvider rng) {
            this.min = min;
            this.range = max - min;
            this.rng = rng;
        }

        @Override
        public int sample() {
            return min + rng.nextInt(range);
        }

        @Override
        public SharedStateDiscreteSampler withUniformRandomProvider(UniformRandomProvider generator) {
            return new IntRangeSampler(min, min + range, generator);
        }
    }

    /**
     * Sample a primitive {@code double} from a range between a and b.
     */
    private static class DoubleRangeSampler implements SharedStateContinuousSampler {
        private final double a;
        private final double b;
        private final UniformRandomProvider rng;

        /**
         * @param a bound a
         * @param b bound b
         * @param rng the source of randomness
         */
        DoubleRangeSampler(double a, double b, UniformRandomProvider rng) {
            this.a = a;
            this.b = b;
            this.rng = rng;
        }

        /**
         * Get the range from a to b.
         *
         * @return the range
         */
        double range() {
            return Math.abs(b - a);
        }

        @Override
        public double sample() {
            // a + u * (b - a) == u * b + (1 - u) * a
            final double u = rng.nextDouble();
            return u * b + (1 - u) * a;
        }

        @Override
        public SharedStateContinuousSampler withUniformRandomProvider(UniformRandomProvider generator) {
            return new DoubleRangeSampler(a, b, generator);
        }
    }
}
