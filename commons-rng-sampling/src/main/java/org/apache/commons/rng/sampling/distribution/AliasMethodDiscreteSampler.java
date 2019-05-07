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

import java.util.Arrays;

/**
 * Distribution sampler that uses the <a
 * href="https://en.wikipedia.org/wiki/Alias_method">Alias method</a>. It can be used to
 * sample from {@code n} values each with an associated probability. If all unique items
 * are assigned the same probability it is much more efficient to use the
 * {@link DiscreteUniformSampler}.
 *
 * <p>This implementation is based on the detailed explanation of the alias method by
 * Keith Schartz and implements Vose's algorithm.</p>
 *
 * <ul>
 *  <li>
 *   <blockquote>
 *    Vose, M.D.,
 *    <i>A linear algorithm for generating random numbers with a given distribution,</i>
 *     IEEE Transactions on Software Engineering, 17, 972-975, 1991.
 *   </blockquote>
 *  </li>
 * </ul>
 *
 * <p>The algorithm will sample values in {@code O(1)} time after a pre-processing step of
 * {@code O(n)} time.</p>
 *
 * <p>In the generic case sampling uses {@link UniformRandomProvider#nextInt(int)} and
 * the upper 53-bits from {@link UniformRandomProvider#nextLong()}.</p>
 *
 * <p>Zero padding the input probabilities can be used to make more sampling more efficient.
 * Any zero entry will always be aliased removing the requirement to compute a {@code long}.
 * Increased sampling speed comes at the cost of increased storage space. The algorithm requires
 * approximately 12 bytes of storage per tabulated probability, that is {@code n * 12} for size
 * {@code n}.</p>
 *
 * <p>An optimisation is performed for small tables size that are a power of 2. In this case the
 * sampling uses 1 or 2 calls from {@link UniformRandomProvider#nextInt()} to generate up to
 * 64-bits for creation of an 11-bit index and 53-bits for the {@code long}. This optimisation
 * requires a generator with a high cycle length for the lower order bits.</p>
 *
 * <p>Larger table sizes that are a power of 2 will benefit from fast algorithms for
 * {@link UniformRandomProvider#nextInt(int)} that exploit the power of 2. A table can be padded
 * to a power of 2 using:</p>
 *
 * <pre><code>
 * double[] probabilities = ...;
 * int pow2 = 32 - Integer.numberOfLeadingZeros(probabilities.length - 1);
 * // Use max to handle overflow of 2^31
 * probabilities = Arrays.copyOf(probabilities, Math.max(probabilities.length, 1 << pow2)));
 * </code></pre>
 *
 * @since 1.3
 * @see <a href="https://en.wikipedia.org/wiki/Alias_method">Alias Method</a>
 * @see <a href="http://www.keithschwarz.com/darts-dice-coins/">Darts, Dice, and Coins:
 * Sampling from a Discrete Distribution by Keith Schwartz</a>
 * @see <a href="https://ieeexplore.ieee.org/document/92917">Vose (1991) IEEE Transactions
 * on Software Engineering 17, 972-975.</a>
 */
public class AliasMethodDiscreteSampler implements DiscreteSampler {
    /** The value 1.0 represented as a 53-bit long. This is the first 53 bits set to 1. */
    private static final long ONE_AS_LONG = (1L << 53) - 1;
    /** The value zero for a {@code double}. */
    private static final double ZERO = 0.0;
    /**
     * The multiplier to convert a {@code double} to the least significant 53-bits of a {@code long}.
     *
     * <p>This is equivalent to 2<sup>53</sup>.</p>
     */
    private static final long LONG_53 = 1L << 53;

    /** Underlying source of randomness. */
    private final UniformRandomProvider rng;

    /**
     * The probability table.
     *
     * <p>This has entries up to the last non-zero element since there is no need to store
     * probabilities of zero. This is an optimisation for zero-padded input. Any zero value will
     * always be aliased so any look-up index outside this table uses the alias.</p>
     *
     * <p>Note that a uniform double in the range [0,1) can be generated using 53-bits from a long
     * (see o.a.c.rng.core.utils.NumberFactory.makeDouble(long)). The probabilities are stored as
     * 53-bit longs to use integer arithmetic.</p>
     */
    private final long[] probability;
    /** The alias table. */
    private final int[] alias;
    /** The delegate that will sample from the computed tables. */
    private final DiscreteSampler delegate;

    /**
     * Sample from the computed tables. This implements the algorithm as per Vose (1991):
     *
     * <pre>
     * v = uniform()  in [0, 1)
     * j = uniform(n) in [0, n)
     * if v <= prob[j] then
     *   return j
     * else
     *   return alias[j]
     * </pre>
     */
    private class BasicAliasDiscreteSampler implements DiscreteSampler {
        @Override
        public int sample() {
            final int j = rng.nextInt(alias.length);

            // Optimisation for zero-padded input tables
            if (j >= probability.length) {
                // No probability must use the alias
                return alias[j];
            }

            // Note: We could check the probability before computing a deviate.
            // p(j) == 0  => alias[j]
            // p(j) == 1  => j
            // However it is assumed these edge cases are rare:
            //
            // The probability table will be 1 for approximately 1/n samples, i.e. only the
            // last unpaired probability. This is only worth checking for when the table size
            // is small. But in that case the user should zero-pad the table for performance.
            //
            // The probability table will be 0 when an input probability was zero. We
            // will assume this is also rare if modelling a discrete distribution where
            // all samples are possible. The edge case for zero-padded tables is handled above.

            // Choose between the two. Use a 53-bit long for the probability
            return (rng.nextLong() >>> 11) < probability[j] ? j : alias[j];
        }
    }

    /**
     * A fast sampler from the computed tables exploiting the small power-of-two table size.
     * This implements the algorithm as per Vose (1991):
     *
     * <pre>
     * bits = obtained required number of random bits
     * v = (some of the bits) * constant1
     * j = (rest of the bits) * constant2
     * if v <= prob[j] then
     *   return j
     * else
     *   return alias[j]
     * </pre>
     *
     * <p>This is implemented using up to 64 bits from the random generator.
     * The index for the table is computed using a mask to extract up to 11 of the lower bits
     * from an integer. The probability is computed using a second integer combined with the
     * remaining bits to create 53-bits for conversion to a double. The double is only computed
     * on demand.</p>
     *
     * <p>Note: This supports a table size of up to 2^11, or 2048, exclusive. Any larger requires
     * consuming more than 64-bits and the algorithm is not more efficient than the
     * {@link BasicAliasDiscreteSampler}.</p>
     */
    private class FastAliasDiscreteSampler implements DiscreteSampler {
        /**
         * The maximum number of bits that can be used for the index look-up.
         * This set to {@code 64 - 53} to allow the unused bits from converting a long
         * into a double to be recycled.
         */
        private static final int MAX_BITS = 11;
        /** The bit mask to isolate the lower 11 bits from an integer. */
        static final int BIT_MASK = (1 << MAX_BITS) - 1;

        /** The bit-shift to use to isolate the lower bits. */
        private final int shift;

        /**
         * Create a new instance.
         */
        FastAliasDiscreteSampler() {
            // Assume the table size is a power of 2. The trailing zeros is the
            // number of bits required.
            // Compute the shift to discard the least significant bits.
            shift = MAX_BITS - Integer.numberOfTrailingZeros(alias.length);
        }

        @Override
        public int sample() {
            final int bits = rng.nextInt();
            // Isolate lower bits and discard least significant
            final int j = (bits & BIT_MASK) >>> shift;

            // Optimisation for zero-padded input tables
            if (j >= probability.length) {
                // No probability must use the alias
                return alias[j];
            }

            // Create a uniform random deviate as a long.
            // This replicates functionality from the o.a.c.rng.core.utils.NumberFactory.makeLong
            final long longBits = (((long) rng.nextInt()) << 32) | (bits & 0xffffffffL);

            // Choose between the two. Use a 53-bit long for the probability
            return (longBits >>> 11) < probability[j] ? j : alias[j];
        }
    }

    /**
     * Creates a sampler.
     *
     * <p>The probabilities will be normalised using their sum. The only requirement is the sum
     * is positive. Padding the array with zeros will improve sampling efficiency at the cost of
     * memory.</p>
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param probabilities The list of probabilities.
     * @throws IllegalArgumentException if {@code probabilities} is null or empty, a
     * probability is negative, infinite or {@code NaN}, or the sum of all
     * probabilities is not strictly positive.
     */
    public AliasMethodDiscreteSampler(final UniformRandomProvider rng, double[] probabilities) {
        // The Alias method balances N categories with counts around the mean into N sections,
        // each allocated 'mean' observations.
        //
        // Consider 4 categories with counts 6,3,2,1. The histogram can be balanced into a
        // 2D array as 4 sections with a height of the mean:
        //
        // 6
        // 6
        // 6
        // 63   => 6366   --
        // 632     6326    |-- mean
        // 6321    6321   --
        //
        // section 0123
        //
        // Each section is divided as:
        // 0: 6=1/1
        // 1: 3=1/1
        // 2: 2=2/3; 6=1/3   (6 is the alias)
        // 3: 1=1/3; 6=2/3   (6 is the alias)
        //
        // The sample is obtained by randomly selecting a section, then choosing which category
        // from the pair based on a uniform random deviate.

        final double sumProb = validateProbabilities(probabilities);

        this.rng = rng;

        final int n = probabilities.length;

        // Partition into small and large by splitting on the average.
        // A modification is made here to handle zero probabilities first so allowing
        // input probabilities to be zero padded to a power of 2. Processing zeros first
        // ensures they are not part of the final loop to fill the probability table with 1.0,
        // i.e. zero probabilities should always be aliased before round-off error occurs.
        final double mean = sumProb / n;
        final int[] large = new int[n];
        final int[] small = new int[n];
        int largeSize = 0;
        // The cardinality of smallSize + zeroSize < n.
        // So fill the same array from either end.
        int smallSize = n;
        int zeroSize = 0;
        // Allow optimisation of input table with zero-padding by finding the last non-zero index
        int nonZeroIndex = 0;
        for (int j = 0; j < probabilities.length; j++) {
            if (probabilities[j] >= mean) {
                nonZeroIndex = j;
                large[largeSize++] = j;
            } else if (probabilities[j] == ZERO) {
                // Put all zeros at the start of small
                small[zeroSize++] = j;
            } else {
                // Note: This is also 'small' but fill the list from the end
                nonZeroIndex = j;
                small[--smallSize] = j;
            }
        }

        if (largeSize == n) {
            // Edge case for a uniform distribution
            probability = null;
            alias = null;
            delegate = new DiscreteUniformSampler(rng, 0, n - 1);
            return;
        }

        // Copy the true small probabilities after the zeros.
        if (smallSize != n) {
            final int numberOfSmall = n - smallSize;
            System.arraycopy(small, smallSize, small, zeroSize, numberOfSmall);
        }
        smallSize = n - largeSize;

        // The probabilities are modified so use a copy.
        // Note: probabilities are required only up to last nonZeroIndex
        probabilities = Arrays.copyOf(probabilities, nonZeroIndex + 1);

        // Allocate the final tables.
        // Probability table may be truncated (when zero padded).
        // The alias table is full length.
        probability = new long[probabilities.length];
        alias = new int[n];

        // This loop uses each large in turn to fill the alias table for small probabilities that
        // do not reach the requirement to fill an entire section alone (i.e. p < mean).
        // Since the sum of the small should be less than the sum of the large it should use up
        // all the small first. However floating point round-off can result in
        // misclassification of items as small or large. The Vose algorithm handles this using
        // a while loop conditioned on the size of both sets and a subsequent loop to use
        // unpaired items.
        while (largeSize != 0 && smallSize != 0) {
            /* Get the index of the small and the large probabilities. */
            final int j = small[--smallSize];
            final int k = large[--largeSize];

            // Optimisation for zero-padded input:
            // p(j) = 0 above the last nonZeroIndex
            if (j > nonZeroIndex) {
                // The remaining amount for the section is taken from the alias.
                probabilities[k] -= mean;
            } else {
                final double pj = probabilities[j];

                // Item j is a small probability that is below the mean.
                // Compute the weight of the section for item j.
                // This is scaled by 2^53 to store the probability in the range [0,2^53]
                probability[j] = (long) (LONG_53 * (pj / mean) + 0.5);

                // The remaining amount for the section is taken from the alias.
                // Effectively: probabilities[k] -= (mean - pj)
                probabilities[k] += pj - mean;
            }
            alias[j] = k;

            // Add the remaining probability to the appropriate list
            if (probabilities[k] >= mean) {
                large[largeSize++] = k;
            } else {
                small[smallSize++] = k;
            }
        }

        // Final loop conditions to consume unpaired items. Anything left must fill
        // the entire section so the probability table is set to 1 and there is no alias.
        // This will occur for 1/n samples, i.e. the last remaining unpaired probability.
        // Note: When the tables are zero-padded the remaining indices are from an input
        // probability that is above zero so the index will be allowed in the truncated
        // probability array.
        while (smallSize != 0) {
            final int j = small[--smallSize];
            probability[j] = ONE_AS_LONG;
            alias[j] = j;
        }
        // Note: The large set should never be non-empty but this can occur due to round-off error.
        while (largeSize != 0) {
            final int k = large[--largeSize];
            probability[k] = ONE_AS_LONG;
            alias[k] = k;
        }

        // Change the algorithm for small power of 2 sized tables
        if (n <= FastAliasDiscreteSampler.BIT_MASK && (n & (n - 1)) == 0) {
            // Small power of 2
            delegate = new FastAliasDiscreteSampler();
        } else {
            delegate = new BasicAliasDiscreteSampler();
        }
    }

    /**
     * Validate the probabilities sum to a finite positive number.
     *
     * @param probabilities the probabilities
     * @return the sum
     * @throws IllegalArgumentException if {@code probabilities} is null or empty, a
     * probability is negative, infinite or {@code NaN}, or the sum of all
     * probabilities is not strictly positive.
     */
    private static double validateProbabilities(double[] probabilities) {
        if (probabilities == null || probabilities.length == 0) {
            throw new IllegalArgumentException("Probabilities must not be empty.");
        }

        double sumProb = 0;
        for (final double prob : probabilities) {
            if (prob < 0 ||
                Double.isInfinite(prob) ||
                Double.isNaN(prob)) {
                throw new IllegalArgumentException("Invalid probability: " +
                                                   prob);
            }
            sumProb += prob;
        }

        if (Double.isInfinite(sumProb) || sumProb <= 0) {
            throw new IllegalArgumentException("Invalid sum of probabilities: " + sumProb);
        }
        return sumProb;
    }

    /** {@inheritDoc} */
    @Override
    public int sample() {
        return delegate.sample();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Alias method [" + rng.toString() + "]";
    }
}
