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

import java.util.Arrays;

/**
 * Class for representing combinations of a sequence of integers.
 *
 * <p>A combination is a selection of items from a collection, such that (unlike
 * permutations) the order of selection does not matter. This sampler can be
 * used to generate a combination in an unspecified order and is faster than the
 * corresponding {@link PermutationSampler}. It can be configured to enforce
 * strict ordering using a sort of each sample with a degradation in
 * performance.
 *
 * <p>The sampler can be used to generate indices to select subsets where the
 * order of the subset is not important.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Combination">Combination
 *      definition</a>
 * @see PermutationSampler
 */
public class CombinationSampler {
    /** Domain of the combination. */
    private final int[] domain;
    /** Size of the combination. */
    private final int size;
    /** The number of steps of a full shuffle to perform. */
    private final int steps;
    /**
     * The position to copy the domain from after a partial shuffle.
     *
     * <p>The copy is either in the range [0 : size] or [domain.length - size :
     * domain.length].
     */
    private final int from;
    /** Flag to indicate the combination should be sorted. */
    private boolean sorted;
    /** RNG. */
    private final UniformRandomProvider rng;

    /**
     * Creates a combination sampler.
     *
     * The {@link #sample()} method will generate an integer array of length
     * {@code k} whose entries are selected randomly, without repetition, from the
     * integers 0, 1, ..., {@code n}-1 (inclusive).
     *
     * <p>The returned array is a set of {@code n} taken {@code k} but is not
     * guaranteed to be in a random order.
     *
     * <p>If {@code n <= 0} or {@code k <= 0} or {@code k >= n} then no combination
     * is required and an exception is raised.
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param n   Domain of the combination.
     * @param k   Size of the combination.
     * @throws IllegalArgumentException if {@code n <= 0} or {@code k <= 0} or
     *                                  {@code k >= n}.
     */
    public CombinationSampler(UniformRandomProvider rng, int n, int k) {
        this(rng, n, k, false);
    }

    /**
     * Creates a combination sampler.
     *
     * The {@link #sample()} method will generate an integer array of length
     * {@code k} whose entries are selected randomly, without repetition, from the
     * integers 0, 1, ..., {@code n}-1 (inclusive).
     *
     * <p>The returned array is a set of {@code n} taken {@code k} but is not
     * guaranteed to be in a random order. The order may optionally be enforced to
     * be sorted with an associated performance cost.
     *
     * <p>If {@code n <= 0} or {@code k <= 0} or {@code k >= n} then no combination
     * is required and an exception is raised.
     *
     * @param rng    Generator of uniformly distributed random numbers.
     * @param n      Domain of the combination.
     * @param k      Size of the combination.
     * @param sorted Set to true to sort the sample.
     * @throws IllegalArgumentException if {@code n <= 0} or {@code k <= 0} or
     *                                  {@code k >= n}.
     */
    public CombinationSampler(UniformRandomProvider rng, int n, int k, boolean sorted) {
        if (n < 0) {
            throw new IllegalArgumentException("n < 0 : n=" + n);
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k <= 0 : k=" + k);
        }
        if (k > n) {
            throw new IllegalArgumentException("k > n : k=" + k + ", n=" + n);
        }

        domain = PermutationSampler.natural(n);
        size = k;
        this.sorted = sorted;
        // The sample can be optimised by only performing the first k or (n - k) steps
        // from a full Fisher-Yates shuffle from the end of the domain to the start.
        // The upper positions will then contain a random sample from the domain. The
        // lower half is then by definition also a random sample (just not in a random order).
        // The sample is then picked using the upper or lower half depending which
        // makes the number of steps smaller.
        if (k <= n / 2) {
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
     * Return a combination of {@code k} whose entries are selected randomly,
     * without repetition, from the integers 0, 1, ..., {@code n}-1 (inclusive).
     *
     * <p>The order of the returned array is not guaranteed to be in a random order
     * as the order of a combination does not matter.
     *
     * @return a random combination.
     */
    public int[] sample() {
        // Shuffle from the end but limit to a number of steps.
        // The subset C(n, k) is then either those positions that have
        // been sampled, or those that haven't been, depending
        // on the number of steps.
        for (int i = domain.length - 1,
                j = 0; i > 0 && j < steps; i--, j++) {
            // Swap index with any position down to 0
            swap(domain, i, rng.nextInt(i + 1));
        }
        final int[] result = new int[size];
        System.arraycopy(domain, from, result, 0, size);
        if (sorted) {
            Arrays.sort(result);
        }
        return result;
    }

    /**
     * Swaps the two specified elements in the specified array.
     *
     * @param array the array
     * @param i     the first index
     * @param j     the second index
     */
    private static void swap(int[] array, int i, int j) {
        final int tmp = array[i];
        array[i] = array[j];
        array[j] = tmp;
    }

    /**
     * Gets the domain size (n) of the combination C(n, k).
     *
     * @return the number of elements (n) in the domain
     */
    public int getN() {
        return domain.length;
    }

    /**
     * Gets the sample size (k) of the combination C(n, k).
     *
     * @return the number of elements (k) in the combination
     */
    public int getK() {
        return size;
    }

    /**
     * Checks if the sample is sorted.
     *
     * <p>Note: The order of a combination does not matter. If this is {@code true}
     * the output will be sorted with an associated performance cost. If
     * {@code false} the sample order is unspecified.
     *
     * @return true if the sample is sorted
     */
    public boolean isSorted() {
        return sorted;
    }
}
