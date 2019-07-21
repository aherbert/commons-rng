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

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.RandomAssert;
import org.apache.commons.rng.simple.RandomSource;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test for the {@link DiscreteUniformSampler}. The tests hit edge cases for the sampler.
 */
public class DiscreteUniformSamplerTest {
    /**
     * Test the constructor with a bad range.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorThrowsWithLowerAboveUpper() {
        final int upper = 55;
        final int lower = upper + 1;
        final UniformRandomProvider rng = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        DiscreteUniformSampler.of(rng, lower, upper);
    }

    /**
     * Test the SharedStateSampler implementation.
     */
    @Test
    public void testSharedStateSamplerWithSmallRange() {
        testSharedStateSampler(5, 67);
    }

    /**
     * Test the SharedStateSampler implementation.
     */
    @Test
    public void testSharedStateSamplerWithLargeRange() {
        testSharedStateSampler(-99999999, Integer.MAX_VALUE);
    }

    /**
     * Test the SharedStateSampler implementation.
     *
     * @param lower Lower.
     * @param upper Upper.
     */
    private static void testSharedStateSampler(int lower, int upper) {
        final UniformRandomProvider rng1 = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        final UniformRandomProvider rng2 = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        // Use instance constructor not factory constructor to exercise 1.X public API
        final SharedStateDiscreteSampler sampler1 =
            new DiscreteUniformSampler(rng1, lower, upper);
        final SharedStateDiscreteSampler sampler2 = sampler1.withUniformRandomProvider(rng2);
        RandomAssert.assertProduceSameSequence(sampler1, sampler2);
    }

    /**
     * Test the toString method. This is added to ensure coverage as the factory constructor
     * used in other tests does not create an instance of the wrapper class.
     */
    @Test
    public void testToString() {
        final UniformRandomProvider rng = RandomSource.create(RandomSource.SPLIT_MIX_64, 0L);
        Assert.assertTrue(new DiscreteUniformSampler(rng, 1, 2).toString().toLowerCase().contains("uniform"));
    }
}
