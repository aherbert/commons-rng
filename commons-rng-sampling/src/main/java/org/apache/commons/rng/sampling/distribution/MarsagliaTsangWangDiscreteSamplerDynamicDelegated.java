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

/**
 * Sampler for a discrete distribution using an optimised look-up table.
 *
 * <ul>
 *  <li>
 *   The method requires 30-bit integer probabilities that sum to 2<sup>30</sup> as described
 *   in George Marsaglia, Wai Wan Tsang, Jingbo Wang (2004) Fast Generation of Discrete
 *   Random Variables. Journal of Statistical Software. Vol. 11, Issue. 3, pp. 1-11.
 *  </li>
 * </ul>
 *
 * <p>Sampling uses 1 call to {@link UniformRandomProvider#nextInt()}.</p>
 *
 * <p>Memory requirements depend on the maximum number of possible sample values, {@code n},
 * and the values for the probabilities. Storage is optimised for {@code n}. The worst case
 * scenario is a uniform distribution of the maximum sample size. This is capped at 0.06MB for
 * {@code n <= } 2<sup>8</sup>, 17.0MB for {@code n <= } 2<sup>16</sup>, and 4.3GB for
 * {@code n <=} 2<sup>30</sup>. Realistic requirements will be in the kB range.</p>
 *
 * @since 1.3
 * @see <a href="http://dx.doi.org/10.18637/jss.v011.i03">Margsglia, et al (2004) JSS Vol.
 * 11, Issue 3</a>
 */
public class MarsagliaTsangWangDiscreteSamplerDynamicDelegated implements DiscreteSampler {
    /** The exclusive upper bound for an unsigned 8-bit integer. */
    private static final int UNSIGNED_INT_8 = 1 << 8;
    /** The exclusive upper bound for an unsigned 16-bit integer. */
    private static final int UNSIGNED_INT_16 = 1 << 16;

    /** The delegate. */
    private final DiscreteSampler delegate;

    /**
     * The option for the base of the digit used when creating the look-up table.
     *
     * <p>Increasing the size of the base increases performance at the expense of storage
     * requirements.</p>
     */
    public enum BaseOption {
        /** Tabulate using base 64 (2<sup>6</sup>) to create 5 tables. */
        BASE_64,
        /** Tabulate using base 1024 (2<sup>10</sup>) to create 3 tables. */
        BASE_1024
    }

    /**
     * Create a new instance for probabilities {@code p(i)} where the sample value
     * {@code x} is {@code i + offset}.
     *
     * <p>The sum of the probabilities must be >= 2<sup>30</sup>. Only the values for
     * cumulative probability up to 2<sup>30</sup> will be sampled.</p>
     *
     * <p>Note: This is package-private for use by discrete distribution samplers that can
     * compute their probability distribution.</p>
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param prob The probabilities.
     * @param offset The offset (must be positive).
     * @param baseOption Control the size of the base used in the look-up table.
     * @throws IllegalArgumentException if the offset is negative or the maximum sample
     * index exceeds the maximum positive {@code int} value (2<sup>31</sup> - 1).
     */
    MarsagliaTsangWangDiscreteSamplerDynamicDelegated(UniformRandomProvider rng,
                                      int[] prob,
                                      int offset,
                                      BaseOption baseOption) {
        if (offset < 0) {
            throw new IllegalArgumentException("Unsupported offset: " + offset);
        }
        if ((long) prob.length + offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Unsupported sample index: " + (prob.length + offset));
        }

        if (baseOption == BaseOption.BASE_1024) {
            delegate = new MarsagliaTsangWangDiscreteSamplerBase10Short(rng, prob, offset);
        } else {
            // Default to base 64
            delegate = new MarsagliaTsangWangDiscreteSamplerShort(rng, prob, offset);
        }
    }

    /**
     * Creates a sampler.
     *
     * <p>The probabilities will be normalised using their sum. The only requirement is the sum
     * is positive.</p>
     *
     * <p>The sum of the probabilities is normalised to 2<sup>30</sup>. Any probability less
     * than 2<sup>-30</sup> will not be observed in samples. An adjustment is made to the maximum
     * probability to compensate for round-off during conversion.</p>
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param probabilities The list of probabilities.
     * @param baseOption Control the size of the base used in the look-up table.
     * @throws IllegalArgumentException if {@code probabilities} is null or empty, a
     * probability is negative, infinite or {@code NaN}, or the sum of all
     * probabilities is not strictly positive.
     */
    public MarsagliaTsangWangDiscreteSamplerDynamicDelegated(UniformRandomProvider rng,
                                             double[] probabilities,
                                             BaseOption baseOption) {
        this(rng, normaliseProbabilities(probabilities), 0, baseOption);
    }

    /**
     * Normalise the probabilities to integers that sum to 2<sup>30</sup>.
     *
     * @param probabilities The list of probabilities.
     * @return the normalised probabilities.
     * @throws IllegalArgumentException if {@code probabilities} is null or empty, a
     * probability is negative, infinite or {@code NaN}, or the sum of all
     * probabilities is not strictly positive.
     */
    private static int[] normaliseProbabilities(double[] probabilities) {
        final double sumProb = validateProbabilities(probabilities);

        // Compute the normalisation: 2^30 / sum
        final double normalisation = (1 << 30) / sumProb;
        final int[] prob = new int[probabilities.length];
        int sum = 0;
        int max = 0;
        int mode = 0;
        for (int i = 0; i < prob.length; i++) {
            // Add 0.5 for rounding
            final int p = (int) (probabilities[i] * normalisation + 0.5);
            sum += p;
            // Find the mode (maximum probability)
            if (max < p) {
                max = p;
                mode = i;
            }
            prob[i] = p;
        }

        // The sum must be >= 2^30.
        // Here just compensate the difference onto the highest probability.
        prob[mode] += (1 << 30) - sum;

        return prob;
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
        return delegate.toString();
    }
}