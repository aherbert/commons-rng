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
 * Compute a sample from a discrete probability distribution. The cumulative probability
 * distribution is searched using a guide table to set an initial start point. This implementation
 * is based on:
 *
 * <ul>
 *  <li>
 *   <blockquote>
 *    Devroye, Luc (1986). Non-Uniform Random Variate Generation.
 *    New York: Springer-Verlag. Chapter 3.2.4 "The method of guide tables" p. 96.
 *   </blockquote>
 *  </li>
 * </ul>
 *
 * <p>The size of the guide table can be controlled using a parameter. A larger guide table
 * will improve performance at the cost of storage space.</p>
 *
 * <p>Sampling uses {@link UniformRandomProvider#nextDouble()}.</p>
 *
 * @since 1.3
 */
public class GuideTableDiscreteSampler
    implements DiscreteSampler {
    /** Underlying source of randomness. */
    private final UniformRandomProvider rng;
    /**
     * The cumulative probability table.
     */
    private final double[] cumulativeProbabilities;
    /**
     * The inverse cumulative probability guide table. This is a map between the cumulative
     * probability (f(x)) and the value x. It is used to set the initial point for search
     * of the cumulative probability table.
     *
     * <p>The index in the map is obtained using {@code f(x) * map.length}. The value
     * stored at the index is value {@code x} such that it is the inclusive upper bound
     * on the sample value for searching the cumulative probability table. Any search probability
     * mapped to {@code x} that is above the corresponding value in the cumulative probability
     * table must be from sample {@code x+1}. Otherwise the table search from the guide point
     * is towards zero.</p>
     */
    private final int[] guideTable;

    /**
     * Create a new instance.
     *
     * <p>The size of the guide table is {@code alpha * probabilities.length}.
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param probabilities The probabilities.
     * @param alpha The alpha factor used to set the guide table size.
     * @throws IllegalArgumentException if {@code probabilities} is null or empty, a
     * probability is negative, infinite or {@code NaN}, the sum of all
     * probabilities is not strictly positive, or {@code alpha} is not strictly positive.
     */
    GuideTableDiscreteSampler(UniformRandomProvider rng,
                              double[] probabilities,
                              double alpha) {
        // Minimal set-up validation
        if (probabilities == null || probabilities.length == 0) {
            throw new IllegalArgumentException("Probabilities must not be empty.");
        }
        if (alpha <= 0) {
            throw new IllegalArgumentException("Alpha must be strictly positive.");
        }

        final int size = probabilities.length;
        cumulativeProbabilities = new double[size];

        double sumProb = 0;
        int count = 0;
        for (final double prob : probabilities) {
            if (prob < 0 ||
                Double.isInfinite(prob) ||
                Double.isNaN(prob)) {
                throw new IllegalArgumentException("Invalid probability: " +
                                                   prob);
            }

            // Compute and store cumulative probability.
            sumProb += prob;
            cumulativeProbabilities[count++] = sumProb;
        }

        if (Double.isInfinite(sumProb) || sumProb <= 0) {
            throw new IllegalArgumentException("Invalid sum of probabilities: " + sumProb);
        }

        this.rng = rng;

        // Note: Performance can be improved by increasing the guide table size
        guideTable = new int[(int) Math.ceil(alpha * size) + 1];

        // Compute and store cumulative probability.
        for (int i = 0; i < size; i++) {
            final double norm = cumulativeProbabilities[i] / sumProb;
            cumulativeProbabilities[i] = (norm < 1) ? norm : 1.0;

            // Set the guide table value as an exclusive upper bound
            guideTable[getGuideTableIndex(cumulativeProbabilities[i])] = i + 1;
        }

        // Edge case for round-off
        cumulativeProbabilities[size - 1] = 1.0;
        guideTable[getGuideTableIndex(1.0)] = size;

        // Fill missing values in the guide table
        for (int i = 1; i < guideTable.length; i++) {
            guideTable[i] = Math.max(guideTable[i - 1], guideTable[i]);
        }
    }

    /**
     * Gets the guide table index for the probability. This is obtained using
     * {@code p * (guideTable.length - 1)} so is inside the length of the table.
     *
     * @param p the cumulative probability
     * @return the guide table index
     */
    private int getGuideTableIndex(double p) {
        // Note: This is only ever called when p is in the range of the cumulative
        // probability table. So assume 0 <= p <= 1.
        return (int) (p * (guideTable.length - 1));
    }

    /** {@inheritDoc} */
    @Override
    public int sample() {
        // Compute a probability
        final double u = rng.nextDouble();

        // Initialise the search using the guide table to find an initial guess.
        // The table provides an upper bound on the sample for a known cumulative probability.
        int sample = guideTable[getGuideTableIndex(u)];
        // Search down.
        // In the edge case where u is 1.0 then sample will be 1 outside the range of the
        // cumulative probability table and this will decrement to a valid range.
        // In the case where u is mapped to the same guide table index as a lower
        // cumulative probability then this will not decrement and return the exclusive
        // upper bound.
        while (sample != 0 && u <= cumulativeProbabilities[sample - 1]) {
            sample--;
        }
        return sample;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Guide table deviate [" + rng.toString() + "]";
    }
}
