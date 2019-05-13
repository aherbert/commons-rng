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
 * <p>The sampler supports the following distributions:</p>
 *
 * <ul>
 *  <li>Any discrete probability distribution (probabilities must be provided)
 *  <li>Poisson distribution up to {@code mean = 1024}
 *  <li>Binomial distribution up to {@code trials = 65535}
 * </ul>
 *
 * @since 1.3
 * @see <a href="http://dx.doi.org/10.18637/jss.v011.i03">Margsglia, et al (2004) JSS Vol.
 * 11, Issue 3</a>
 */
public abstract class MarsagliaTsangWangDiscreteSampler implements DiscreteSampler {
    /** The value 2<sup>8</sup> as an {@code int}. */
    private static final int INT_8 = 1 << 8;
    /** The value 2<sup>16</sup> as an {@code int}. */
    private static final int INT_16 = 1 << 16;
    /** The value 2<sup>30</sup> as an {@code int}. */
    private static final int INT_30 = 1 << 30;
    /** The value 2<sup>31</sup> as a {@code double}. */
    private static final double DOUBLE_31 = 1L << 31;

    /** The general name of any discrete probability distribution. */
    private static final String DISCRETE_NAME = "discrete";
    /** The name of the Poisson distribution. */
    private static final String POISSON_NAME = "Poisson";
    /** The name of the Binomial distribution. */
    private static final String BINOMIAL_NAME = "Binomial";

    /**
     * Upper bound on the mean for the Poisson distribution.
     *
     * <p>The original source code provided in Marsaglia, et al (2004) has no explicit
     * limit but the code fails at mean >= 1941 as the transform to compute p(x=mode)
     * produces infinity. Use a conservative limit of 1024.</p>
     */
    private static final double MAX_POISSON_MEAN = 1024;

    /** Underlying source of randomness. */
    protected final UniformRandomProvider rng;

    /** The name of the distribution. */
    private final String distributionName;

    // =========================================================================
    // Implementation note:
    //
    // This sampler uses prepared look-up tables that are searched using a single
    // random int variate. The look-up tables contain the sample value. The tables
    // are constructed using probabilities that sum to 2^30. The original
    // paper by Marsaglia, et al (2004) describe use of 5, 3, or 2 look-up tables
    // indexed using digits of base 2^6, 2^10 or 2^15. Currently only base 64 (2^6)
    // is supported using 5 look-up tables.
    //
    // The implementations use 8, 16 or 32 bit storage tables to support different
    // distribution sizes with optimal storage. Separate class implementations of
    // the same algorithm allow array storage to be accessed directly from 1D tables.
    // This provides a performance gain over using abstracted storage accessed via
    // an interface or a single 2D table.
    //
    // To allow the optimal implementation to be chosen the sampler is created
    // using factory methods. The sampler supports any probability distribution
    // when provided via an array of probabilities and the Poisson and Binomial
    // distributions for a restricted set of parameters. The restrictions are
    // imposed by the requirement to compute the entire probability distribution
    // from the controlling parameter(s) using a recursive method.
    // =========================================================================

    /**
     * An implementation for the sample algorithm based on the decomposition of the
     * index in the range {@code [0,2^30)} into 5 base-64 digits with 8-bit backing storage.
     */
    private static class MarsagliaTsangWangBase64Int8DiscreteSampler
        extends MarsagliaTsangWangDiscreteSampler {
        /** The mask to convert a {@code byte} to an unsigned 8-bit integer. */
        private static final int MASK = 0xff;

        /** Limit for look-up table 1. */
        private final int t1;
        /** Limit for look-up table 2. */
        private final int t2;
        /** Limit for look-up table 3. */
        private final int t3;
        /** Limit for look-up table 4. */
        private final int t4;

        /** Look-up table table1. */
        private byte[] table1;
        /** Look-up table table2. */
        private byte[] table2;
        /** Look-up table table3. */
        private byte[] table3;
        /** Look-up table table4. */
        private byte[] table4;
        /** Look-up table table5. */
        private byte[] table5;

        /**
         * @param rng Generator of uniformly distributed random numbers.
         * @param distributionName Distribution name.
         * @param prob The probabilities.
         * @param offset The offset (must be positive).
         */
        MarsagliaTsangWangBase64Int8DiscreteSampler(UniformRandomProvider rng,
                                                    String distributionName,
                                                    int[] prob,
                                                    int offset) {
            super(rng, distributionName);

            // Get table sizes for each base-64 digit
            int n1 = 0;
            int n2 = 0;
            int n3 = 0;
            int n4 = 0;
            int n5 = 0;
            for (final int m : prob) {
                n1 += getBase64Digit(m, 1);
                n2 += getBase64Digit(m, 2);
                n3 += getBase64Digit(m, 3);
                n4 += getBase64Digit(m, 4);
                n5 += getBase64Digit(m, 5);
            }

            table1 = new byte[n1];
            table2 = new byte[n2];
            table3 = new byte[n3];
            table4 = new byte[n4];
            table5 = new byte[n5];

            // Compute offsets
            t1 = n1 << 24;
            t2 = t1 + (n2 << 18);
            t3 = t2 + (n3 << 12);
            t4 = t3 + (n4 << 6);
            n1 = n2 = n3 = n4 = n5 = 0;

            // Fill tables
            for (int i = 0; i < prob.length; i++) {
                final int m = prob[i];
                // Primitive type conversion will extract lower 8 bits
                final byte k = (byte) (i + offset);
                fill(table1, n1, n1 += getBase64Digit(m, 1), k);
                fill(table2, n2, n2 += getBase64Digit(m, 2), k);
                fill(table3, n3, n3 += getBase64Digit(m, 3), k);
                fill(table4, n4, n4 += getBase64Digit(m, 4), k);
                fill(table5, n5, n5 += getBase64Digit(m, 5), k);
            }
        }

        /**
         * Fill the table with the value.
         *
         * @param table Table.
         * @param from Lower bound index (inclusive)
         * @param to Upper bound index (exclusive)
         * @param value Value.
         */
        private static void fill(byte[] table, int from, int to, byte value) {
            while (from < to) {
                table[from++] = value;
            }
        }

        /** {@inheritDoc} */
        @Override
        public int sample() {
            final int j = rng.nextInt() >>> 2;
            if (j < t1) {
                return table1[j >>> 24] & MASK;
            }
            if (j < t2) {
                return table2[(j - t1) >>> 18] & MASK;
            }
            if (j < t3) {
                return table3[(j - t2) >>> 12] & MASK;
            }
            if (j < t4) {
                return table4[(j - t3) >>> 6] & MASK;
            }
            // Note the tables are filled on the assumption that the sum of the probabilities.
            // is >=2^30. If this is not true then the final table table5 will be smaller by the
            // difference. So the tables *must* be constructed correctly.
            return table5[j - t4] & MASK;
        }
    }

    /**
     * An implementation for the sample algorithm based on the decomposition of the
     * index in the range {@code [0,2^30)} into 5 base-64 digits with 16-bit backing storage.
     */
    private static class MarsagliaTsangWangBase64Int16DiscreteSampler
        extends MarsagliaTsangWangDiscreteSampler {
        /** The mask to convert a {@code byte} to an unsigned 16-bit integer. */
        private static final int MASK = 0xffff;

        /** Limit for look-up table 1. */
        private final int t1;
        /** Limit for look-up table 2. */
        private final int t2;
        /** Limit for look-up table 3. */
        private final int t3;
        /** Limit for look-up table 4. */
        private final int t4;

        /** Look-up table table1. */
        private short[] table1;
        /** Look-up table table2. */
        private short[] table2;
        /** Look-up table table3. */
        private short[] table3;
        /** Look-up table table4. */
        private short[] table4;
        /** Look-up table table5. */
        private short[] table5;

        /**
         * @param rng Generator of uniformly distributed random numbers.
         * @param distributionName Distribution name.
         * @param prob The probabilities.
         * @param offset The offset (must be positive).
         */
        MarsagliaTsangWangBase64Int16DiscreteSampler(UniformRandomProvider rng,
                                                     String distributionName,
                                                     int[] prob,
                                                     int offset) {
            super(rng, distributionName);

            // Get table sizes for each base-64 digit
            int n1 = 0;
            int n2 = 0;
            int n3 = 0;
            int n4 = 0;
            int n5 = 0;
            for (final int m : prob) {
                n1 += getBase64Digit(m, 1);
                n2 += getBase64Digit(m, 2);
                n3 += getBase64Digit(m, 3);
                n4 += getBase64Digit(m, 4);
                n5 += getBase64Digit(m, 5);
            }

            table1 = new short[n1];
            table2 = new short[n2];
            table3 = new short[n3];
            table4 = new short[n4];
            table5 = new short[n5];

            // Compute offsets
            t1 = n1 << 24;
            t2 = t1 + (n2 << 18);
            t3 = t2 + (n3 << 12);
            t4 = t3 + (n4 << 6);
            n1 = n2 = n3 = n4 = n5 = 0;

            // Fill tables
            for (int i = 0; i < prob.length; i++) {
                final int m = prob[i];
                // Primitive type conversion will extract lower 16 bits
                final short k = (short) (i + offset);
                fill(table1, n1, n1 += getBase64Digit(m, 1), k);
                fill(table2, n2, n2 += getBase64Digit(m, 2), k);
                fill(table3, n3, n3 += getBase64Digit(m, 3), k);
                fill(table4, n4, n4 += getBase64Digit(m, 4), k);
                fill(table5, n5, n5 += getBase64Digit(m, 5), k);
            }
        }

        /**
         * Fill the table with the value.
         *
         * @param table Table.
         * @param from Lower bound index (inclusive)
         * @param to Upper bound index (exclusive)
         * @param value Value.
         */
        private static void fill(short[] table, int from, int to, short value) {
            while (from < to) {
                table[from++] = value;
            }
        }

        /** {@inheritDoc} */
        @Override
        public int sample() {
            final int j = rng.nextInt() >>> 2;
            if (j < t1) {
                return table1[j >>> 24] & MASK;
            }
            if (j < t2) {
                return table2[(j - t1) >>> 18] & MASK;
            }
            if (j < t3) {
                return table3[(j - t2) >>> 12] & MASK;
            }
            if (j < t4) {
                return table4[(j - t3) >>> 6] & MASK;
            }
            // Note the tables are filled on the assumption that the sum of the probabilities.
            // is >=2^30. If this is not true then the final table table5 will be smaller by the
            // difference. So the tables *must* be constructed correctly.
            return table5[j - t4] & MASK;
        }
    }

    /**
     * An implementation for the sample algorithm based on the decomposition of the
     * index in the range {@code [0,2^30)} into 5 base-64 digits with 32-bit backing storage.
     */
    private static class MarsagliaTsangWangBase64Int32DiscreteSampler
        extends MarsagliaTsangWangDiscreteSampler {

        /** Limit for look-up table 1. */
        private final int t1;
        /** Limit for look-up table 2. */
        private final int t2;
        /** Limit for look-up table 3. */
        private final int t3;
        /** Limit for look-up table 4. */
        private final int t4;

        /** Look-up table table1. */
        private int[] table1;
        /** Look-up table table2. */
        private int[] table2;
        /** Look-up table table3. */
        private int[] table3;
        /** Look-up table table4. */
        private int[] table4;
        /** Look-up table table5. */
        private int[] table5;

        /**
         * @param rng Generator of uniformly distributed random numbers.
         * @param distributionName Distribution name.
         * @param prob The probabilities.
         * @param offset The offset (must be positive).
         */
        MarsagliaTsangWangBase64Int32DiscreteSampler(UniformRandomProvider rng,
                                                     String distributionName,
                                                     int[] prob,
                                                     int offset) {
            super(rng, distributionName);

            // Get table sizes for each base-64 digit
            int n1 = 0;
            int n2 = 0;
            int n3 = 0;
            int n4 = 0;
            int n5 = 0;
            for (final int m : prob) {
                n1 += getBase64Digit(m, 1);
                n2 += getBase64Digit(m, 2);
                n3 += getBase64Digit(m, 3);
                n4 += getBase64Digit(m, 4);
                n5 += getBase64Digit(m, 5);
            }

            table1 = new int[n1];
            table2 = new int[n2];
            table3 = new int[n3];
            table4 = new int[n4];
            table5 = new int[n5];

            // Compute offsets
            t1 = n1 << 24;
            t2 = t1 + (n2 << 18);
            t3 = t2 + (n3 << 12);
            t4 = t3 + (n4 << 6);
            n1 = n2 = n3 = n4 = n5 = 0;

            // Fill tables
            for (int i = 0; i < prob.length; i++) {
                final int m = prob[i];
                final int k = i + offset;
                fill(table1, n1, n1 += getBase64Digit(m, 1), k);
                fill(table2, n2, n2 += getBase64Digit(m, 2), k);
                fill(table3, n3, n3 += getBase64Digit(m, 3), k);
                fill(table4, n4, n4 += getBase64Digit(m, 4), k);
                fill(table5, n5, n5 += getBase64Digit(m, 5), k);
            }
        }

        /**
         * Fill the table with the value.
         *
         * @param table Table.
         * @param from Lower bound index (inclusive)
         * @param to Upper bound index (exclusive)
         * @param value Value.
         */
        private static void fill(int[] table, int from, int to, int value) {
            while (from < to) {
                table[from++] = value;
            }
        }

        /** {@inheritDoc} */
        @Override
        public int sample() {
            final int j = rng.nextInt() >>> 2;
            if (j < t1) {
                return table1[j >>> 24];
            }
            if (j < t2) {
                return table2[(j - t1) >>> 18];
            }
            if (j < t3) {
                return table3[(j - t2) >>> 12];
            }
            if (j < t4) {
                return table4[(j - t3) >>> 6];
            }
            // Note the tables are filled on the assumption that the sum of the probabilities.
            // is >=2^30. If this is not true then the final table table5 will be smaller by the
            // difference. So the tables *must* be constructed correctly.
            return table5[j - t4];
        }
    }

    /**
     * Return a fixed result for the Binomial distribution. This is a special class to handle
     * an edge case of probability of success equal to 0 or 1.
     */
    private static class MarsagliaTsangWangFixedResultBinomialSampler
        extends MarsagliaTsangWangDiscreteSampler {
        /** The result. */
        private final int result;

        /**
         * @param result Result.
         */
        MarsagliaTsangWangFixedResultBinomialSampler(int result) {
            super(null, BINOMIAL_NAME);
            this.result = result;
        }

        @Override
        public int sample() {
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return BINOMIAL_NAME + " deviate";
        }
    }

    /**
     * Return an inversion result for the Binomial distribution. This assumes the
     * following:
     *
     * <pre>
     * Binomial(n, p) = 1 - Binomial(n, 1 - p)
     * </pre>
     */
    private static class MarsagliaTsangWangInversionBinomialSampler
        extends MarsagliaTsangWangDiscreteSampler {
        /** The number of trials. */
        private final int trials;
        /** The Binomial distribution sampler. */
        private final MarsagliaTsangWangDiscreteSampler sampler;

        /**
         * @param trials Number of trials.
         * @param sampler Binomial distribution sampler.
         */
        MarsagliaTsangWangInversionBinomialSampler(int trials,
                                                   MarsagliaTsangWangDiscreteSampler sampler) {
            super(null, BINOMIAL_NAME);
            this.trials = trials;
            this.sampler = sampler;
        }

        @Override
        public int sample() {
            return trials - sampler.sample();
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return sampler.toString();
        }
    }

    /**
     * @param rng Generator of uniformly distributed random numbers.
     * @param distributionName Distribution name.
     */
    protected MarsagliaTsangWangDiscreteSampler(UniformRandomProvider rng,
                                                String distributionName) {
        this.rng = rng;
        this.distributionName = distributionName;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Marsaglia Tsang Wang " + distributionName + " deviate [" + rng.toString() + "]";
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
     * Create a new instance for probabilities {@code p(i)} where the sample value {@code x} is
     * {@code i + offset}.
     *
     * <p>The sum of the probabilities must be >= 2<sup>30</sup>. Only the
     * values for cumulative probability up to 2<sup>30</sup> will be sampled.</p>
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param distributionName Distribution name.
     * @param prob The probabilities.
     * @param offset The offset (must be positive).
     * @return Sampler.
     */
    private static MarsagliaTsangWangDiscreteSampler createSampler(UniformRandomProvider rng,
                                                                   String distributionName,
                                                                   int[] prob,
                                                                   int offset) {
        // Note: No argument checks for private method.

        // Choose implementation based on the maximum index
        final int maxIndex = prob.length + offset - 1;
        if (maxIndex < INT_8) {
            return new MarsagliaTsangWangBase64Int8DiscreteSampler(rng, distributionName, prob, offset);
        }
        if (maxIndex < INT_16) {
            return new MarsagliaTsangWangBase64Int16DiscreteSampler(rng, distributionName, prob, offset);
        }
        return new MarsagliaTsangWangBase64Int32DiscreteSampler(rng, distributionName, prob, offset);
    }

    // =========================================================================
    // The following factory methods are the public API to construct a sampler for:
    // - Any discrete probability distribution (from provided double[] probabilities)
    // - Poisson distribution for mean <= 1024
    // - Binomial distribution for trials <= 65535
    // =========================================================================

    /**
     * Creates a sampler for a given probability distribution.
     *
     * <p>The probabilities will be normalised using their sum. The only requirement
     * is the sum is positive.</p>
     *
     * <p>The sum of the probabilities is normalised to 2<sup>30</sup>. Note that
     * probabilities are adjusted to the nearest 1<sup>-30</sup> due to round-off during
     * the normalisation conversion. Consequently any probability less than 2<sup>-31</sup>
     * will not be observed in samples.</p>
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param probabilities The list of probabilities.
     * @return Sampler.
     * @throws IllegalArgumentException if {@code probabilities} is null or empty, a
     * probability is negative, infinite or {@code NaN}, or the sum of all
     * probabilities is not strictly positive.
     */
    public static MarsagliaTsangWangDiscreteSampler createDiscreteDistribution(UniformRandomProvider rng,
                                                                               double[] probabilities) {
        return createSampler(rng, DISCRETE_NAME, normaliseProbabilities(probabilities), 0);
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
        final double normalisation = INT_30 / sumProb;
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
        prob[mode] += INT_30 - sum;

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

    /**
     * Creates a sampler for the Poisson distribution.
     *
     * <p>Any probability less than 2<sup>-31</sup> will not be observed in samples.</p>
     *
     * <p>Storage requirements depend on the tabulated probability values. Example storage
     * requirements are listed below.</p>
     *
     * <pre>
     * mean      table size     kB
     * 0.25      882            0.88
     * 0.5       1135           1.14
     * 1         1200           1.20
     * 2         1451           1.45
     * 4         1955           1.96
     * 8         2961           2.96
     * 16        4410           4.41
     * 32        6115           6.11
     * 64        8499           8.50
     * 128       11528          11.53
     * 256       15935          31.87
     * 512       20912          41.82
     * 1024      30614          61.23
     * </pre>
     *
     * <p>Note: Storage changes to 2 bytes per index between {@code mean=128} and {@code mean=256}.</p>
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param mean Mean.
     * @return Sampler.
     * @throws IllegalArgumentException if {@code mean <= 0} or {@code mean > 1024}.
     */
    public static MarsagliaTsangWangDiscreteSampler createPoissonDistribution(UniformRandomProvider rng,
                                                                              double mean) {
        if (mean <= 0) {
            throw new IllegalArgumentException("mean is not strictly positive: " + mean);
        }
        // The algorithm is not valid if Math.floor(mean) is not an integer.
        if (mean > MAX_POISSON_MEAN) {
            throw new IllegalArgumentException("mean " + mean + " > " + MAX_POISSON_MEAN);
        }

        // Probabilities are 30-bit integers, assumed denominator 2^30
        int[] prob;
        // This is the minimum sample value: prob[x - offset] = p(x)
        int offset;

        // Generate P's from 0 if mean < 21.4
        if (mean < 21.4) {
            final double p0 = Math.exp(-mean);

            // Recursive update of Poisson probability until the value is too small
            // p(x + 1) = p(x) * mean / (x + 1)
            double p = p0;
            int i;
            for (i = 1; p * DOUBLE_31 >= 1; i++) {
                p *= mean / i;
            }

            // Fill P as (30-bit integers)
            offset = 0;
            final int size = i - 1;
            prob = new int[size];

            p = p0;
            prob[0] = toUnsignedInt30(p);
            // The sum must exceed 2^30. In edges cases this is false due to round-off.
            int sum = prob[0];
            for (i = 1; i < prob.length; i++) {
                p *= mean / i;
                prob[i] = toUnsignedInt30(p);
                sum += prob[i];
            }

            // If the sum is < 2^30 add the remaining sum to the mode (floor(mean)).
            prob[(int) mean] += Math.max(0, INT_30 - sum);
        } else {
            // If mean >= 21.4, generate from largest p-value up, then largest down.
            // The largest p-value will be at the mode (floor(mean)).

            // Find p(x=mode)
            final int mode = (int) mean;
            // This transform is stable until mean >= 1941 where p will result in Infinity
            // before the divisor i is large enough to start reducing the product (i.e. i > c).
            final double c = mean * Math.exp(-mean / mode);
            double p = 1.0;
            int i;
            for (i = 1; i <= mode; i++) {
                p *= c / i;
            }
            final double pMode = p;
            // Note this will exit when i overflows to negative so no check on the range
            for (i = mode + 1; p * DOUBLE_31 >= 1; i++) {
                p *= mean / i;
            }
            final int last = i - 2;
            p = pMode;
            int j = -1;
            for (i = mode - 1; i >= 0; i--) {
                p *= (i + 1) / mean;
                if (p * DOUBLE_31 < 1) {
                    j = i;
                    break;
                }
            }

            // Fill P as (30-bit integers)
            offset = j + 1;
            final int size = last - offset + 1;
            prob = new int[size];

            p = pMode;
            prob[mode - offset] = toUnsignedInt30(p);
            // The sum must exceed 2^30. In edges cases this is false due to round-off.
            int sum = prob[mode - offset];
            for (i = mode + 1; i <= last; i++) {
                p *= mean / i;
                prob[i - offset] = toUnsignedInt30(p);
                sum += prob[i - offset];
            }
            p = pMode;
            for (i = mode - 1; i >= offset; i--) {
                p *= (i + 1) / mean;
                prob[i - offset] = toUnsignedInt30(p);
                sum += prob[i - offset];
            }

            // If the sum is < 2^30 add the remaining sum to the mode.
            // If above 2^30 then the effect is truncation of the long tail of the distribution.
            prob[mode - offset] += Math.max(0, INT_30 - sum);
        }

        return createSampler(rng, POISSON_NAME, prob, offset);
    }

    /**
     * Creates a sampler for the Binomial distribution.
     *
     * <p>Any probability less than 2<sup>-31</sup> will not be observed in samples.</p>
     *
     * <p>Storage requirements depend on the tabulated probability values. Example storage
     * requirements are listed below (in kB).</p>
     *
     * <pre>
     *          p
     * trials   0.5    0.1   0.01  0.001
     *    4     0.1    0.6    0.4    0.4
     *   16     0.7    1.1    0.8    0.4
     *   64     4.7    2.4    1.1    0.5
     *  256     8.6    5.2    1.9    0.8
     * 1024    15.6    9.5    3.3    0.9
     * </pre>
     *
     * <p>The method requires that the Binomial distribution probability at {@code x=0} can be computed.
     * This will fail when {@code (1 - p)^trials == 0} which requires {@code trials} to be large
     * and/or {@code p} to be small. In this case an exception is raised.</p>
     *
     * @param rng Generator of uniformly distributed random numbers.
     * @param trials Number of trials.
     * @param p Probability of success.
     * @return Sampler.
     * @throws IllegalArgumentException if {@code trials < 0} or {@code trials >= 2^16},
     * {@code p} is not in the range {@code [0-1]}, or the probability distribution cannot
     * be computed.
     */
    public static MarsagliaTsangWangDiscreteSampler createBinomialDistribution(UniformRandomProvider rng,
                                                                               int trials,
                                                                               double p) {
        if (trials < 0) {
            throw new IllegalArgumentException("Trials is not positive: " + trials);
        }
        if (p < 0 || p > 1) {
            throw new IllegalArgumentException("Probability is not in range [0,1]: " + p);
        }

        // Handle edge cases
        if (p == 0) {
            return new MarsagliaTsangWangFixedResultBinomialSampler(0);
        }
        if (p == 1) {
            return new MarsagliaTsangWangFixedResultBinomialSampler(trials);
        }

        // A simple check using the supported index size.
        if (trials >= INT_16) {
            throw new IllegalArgumentException("Unsupported number of trials: " + trials);
        }

        // The maximum supported value for Math.exp is approximately -744.
        // This occurs when trials is large and p is close to 1.
        // Handle this by using an inversion: generate j=Binomial(n,1-p), return n-j
        final boolean inversion = p > 0.5;
        if (inversion) {
            p = 1 - p;
        }

        // Check if the distribution can be computed
        final double p0 = Math.exp(trials * Math.log(1 - p));
        if (p0 < Double.MIN_VALUE) {
            throw new IllegalArgumentException("Unable to compute distribution");
        }

        // First find size of probability array
        double t = p0;
        final double h = p / (1 - p);
        // Find first probability
        int begin = 0;
        if (t * DOUBLE_31 < 1) {
            // Somewhere after p(0)
            // Note:
            // If this loop is entered p(0) is < 2^-31.
            // This has been tested at the extreme for p(0)=Double.MIN_VALUE and either
            // p=0.5 or trials=2^16-1 and does not fail to find the beginning.
            for (int i = 1; i <= trials; i++) {
                t *= (trials + 1 - i) * h / i;
                if (t * DOUBLE_31 >= 1) {
                    begin = i;
                    break;
                }
            }
        }
        // Find last probability
        int end = trials;
        for (int i = begin + 1; i <= trials; i++) {
            t *= (trials + 1 - i) * h / i;
            if (t * DOUBLE_31 < 1) {
                end = i - 1;
                break;
            }
        }
        final int size = end - begin + 1;
        final int offset = begin;

        // Then assign probability values as 30-bit integers
        final int[] prob = new int[size];
        t = p0;
        for (int i = 1; i <= begin; i++) {
            t *= (trials + 1 - i) * h / i;
        }
        int sum = toUnsignedInt30(t);
        prob[0] = sum;
        for (int i = begin + 1; i <= end; i++) {
            t *= (trials + 1 - i) * h / i;
            prob[i - begin] = toUnsignedInt30(t);
            sum += prob[i - begin];
        }

        // If the sum is < 2^30 add the remaining sum to the mode (floor((n+1)p))).
        // If above 2^30 then the effect is truncation of the long tail of the distribution.
        final int mode = (int) ((trials + 1) * p) - offset;
        prob[mode] += Math.max(0, INT_30 - sum);

        final MarsagliaTsangWangDiscreteSampler sampler = createSampler(rng, BINOMIAL_NAME, prob, offset);

        if (inversion) {
            return new MarsagliaTsangWangInversionBinomialSampler(trials, sampler);
        }
        return sampler;
    }

    /**
     * Convert the probability to an unsigned integer in the range [0,2^30].
     *
     * @param p the probability
     * @return the integer
     */
    private static int toUnsignedInt30(double p) {
        return (int) (p * INT_30 + 0.5);
    }
}
