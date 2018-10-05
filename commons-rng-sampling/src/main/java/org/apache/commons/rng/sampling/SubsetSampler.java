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

import org.apache.commons.rng.UniformRandomProvider;

/**
 * Class for sampling a subset of a sequence of integers.
 *
 * <p>The sampled subset will contain a random set of integers from the sequence
 * but is not guaranteed to be in a random order.
 * 
 * <p>The sampler can be used to generate indices to select subsets where the
 * order of the subset is not important.
 */
public class SubsetSampler {
    /** Domain of the permutation. */
    private final int[] domain;
    /** Size of the permutation. */
    private final int size;
    /** The number of steps of a full shuffle to perform. */
    private final int steps;
    /**
     * The position to copy the domain from after a partial shuffle.
     *
     * <p>The copy is either in the range [0 : size] or
     * [domain.length - size : domain.length].
     */
    private final int from;
    /** RNG. */
    private final UniformRandomProvider rng;

    /**
     * Creates a subset sampler.
     *
     * The {@link #sample()} method will generate an integer array of
     * length {@code k} whose entries are selected randomly, without
     * repetition, from the integers 0, 1, ..., {@code n}-1 (inclusive).
     * 
     * <p>The returned array is a set of {@code n} taken
     * {@code k} but is not guaranteed to be in a random order.
     * 
     * <p>If {@code n <= 0} or {@code k <= 0} or {@code k >= n} then no
     * subset is required and an exception is raised.
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param n Domain of the permutation.
     * @param k Size of the permutation.
     * @throws IllegalArgumentException if {@code n <= 0} or {@code k <= 0}
     * or {@code k >= n}.
     */
    public SubsetSampler(UniformRandomProvider rng,
                              int n,
                              int k) {
        if (n <= 0) {
            throw new IllegalArgumentException(n + " <= " + 0);
        }
        if (k <= 0) {
            throw new IllegalArgumentException(k + " <= " + 0);
        }
        if (k >= n) {
            throw new IllegalArgumentException(k + " >= " + n);
        }

        domain = PermutationSampler.natural(n);
        size = k;
        // The sample can be optimised by only performing the first n steps
        // from a full Fisher-Yates shuffle. The upper n positions will then
        // contain a random sample from the domain. The lower half is then
        // by definition also a random sample (just not in a random order).
        // The sample is then picked using the upper or lower half depending
        // which makes the number of steps smaller.
        if (k < n / 2) {
            // Upper half
            steps = k;
            from = n - k;
        } else {
            // Lower half
            steps = n - k;
            from = 0;
        }
        this.rng = rng;
    }

    /**
     * Return a sample of {@code k} whose entries are selected randomly, without
     * repetition, from the integers 0, 1, ..., {@code n}-1 (inclusive).
     * 
     * <p>The returned array is not guaranteed to be in a random order.
     * 
     * @return a random subset.
     */
    public int[] sample() {
        // Shuffle from the end but only the first n positions.
        // The subset is then either those positions that have 
        // been sampled, or those that haven't been, depending
        // on the number of steps.
        for (int i = domain.length - 1,
                j = 0; i > 0 && j < steps; i--, j++) {
            final int target = rng.nextInt(i + 1);
            final int temp = domain[target];
            domain[target] = domain[i];
            domain[i] = temp;
        }
        final int[] result = new int[size];
        System.arraycopy(domain, from, result, 0, size);
        return result;
    }
}
