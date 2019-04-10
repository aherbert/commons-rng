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

package org.apache.commons.rng.core.source32;

import org.apache.commons.rng.core.util.NumberFactory;

/**
 * Middle Square Weyl Sequence Random Number Generator.
 *
 * <p>A fast all-purpose 32-bit generator. Memory footprint is 192 bits and the period is at least
 * {@code 2^64}.</p>
 *
 * <p>Implementation is based on the paper
 * <a href="https://arxiv.org/abs/1704.00358v3">Middle Square Weyl Sequence RNG</a>.</p>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Middle-square_method">Middle Square Method</a>
 *
 * @since 1.3
 */
public class MiddleSquareWeylSequence extends IntProvider {
    /** Size of the state vector. */
    private static final int STATE_SIZE = 3;

    /** State of the generator. */
    private long x1;
    /** State of the Weyl sequence. */
    private long w1;
    /**
     * Increment for the Weyl sequence. This must be odd to ensure a full period.
     *
     * <p>This is not final to support the restore functionality.</p>
     */
    private long s1;

    /**
     * Creates a new instance.
     *
     * @param seed Initial seed.
     */
    public MiddleSquareWeylSequence(Long seed) {
        this(seed.longValue());
    }

    /**
     * Creates a new instance.
     *
     * @param seed Initial seed.
     */
    public MiddleSquareWeylSequence(long seed) {
        // Note: pp. 6:
        // "If x or w are used to provide a different
        // initialization, there is some danger that overlapping data will be produced."
        // The example given is an initial x of 0 or 2^32 which produce the same square
        // so if the Weyl increment is the same the output will be identical.
        //
        // Thus the state is not explicitly allowed to be set separately from the seed.
        // Here we initialise the state and the sequence using the seed to ensure that
        // if constructed using sequential numbers the states will be different even
        // if the increment created from the seed is the same.
        // Note that the effect is that if seeded using an example seed value from the
        // MSWS RNG paper the generator will match:
        // x = w = s = seed
        x1 = seed;
        w1 = seed;
        s1 = createWeylIncrement(seed);
    }

    /**
     * Creates a suitable increment for the Weyl sequence. The returned number will meet
     * the following conditions:</p>
     *
     * <ul>
     *   <li>The number is odd to ensure a full period
     *   <li>"The constant s should be an irregular bit pattern with roughly half of the
     *       bits set to one and half of the bits set to zero. The best statistics have been
     *       obtained with this type of pattern. Unduly sparse or dense values do not
     *       produce good results." (pp. 6)
     * </ul>
     *
     * <p>In this method the definition of randomness is the number of bit changes found in the
     * sequence. This is modelled as a Binomial distribution (BD) with <i>n</i> the number
     * of changes and <i>p</i> the probability of a change set to {@code 0.5}. The method
     * ensures that the upper and lower 32 bits of the long value have randomness expected
     * in 99.5% of random integers. The cumulative probability of BD(n=31,p=0.5) p(X<9) is
     * 0.0053. Using a cut-off of at least 9 state changes in the upper and lower bits
     * will modify approximately {@code 1 - (1-0.0053)^2} = 1.06% of input random numbers.<p>
     *
     * <p>Note that this method will not effect numbers that already satisfy the conditions.
     * This allows seeding using random numbers to be largely unaffected.<p>
     *
     * @param value the value
     * @return the increment
     * @see <a href="https://en.wikipedia.org/wiki/Weyl_sequence">Weyl sequence</a>
     */
    private static long createWeylIncrement(long value) {
        // TODO:
        // The original paper suggests seeding using random hexadecimal digits.
        // "The digits are chosen so that the upper 8 are different and the lower 8 are
        // different and then 1 is ored into the result to make the constant odd."
        // This method requires a random number generator to create the random hex digits.
        // The author's example code provides a method using an embedded MSWS RNG as
        // the generator. The method just digests the output from the call to nextInt
        // until 8 unique hex digits are found.
        // Note that a single call to nextInt will generate <=8 unique hex digits.
        // These could form the integer stream used in a method to get an index as
        // per nextInt(int) since that uses % n to get the remainder we can produce
        // random ints in the range 0-7 for a Fisher-Yates shuffle of the 8 hex digits.
        // Getting a full shuffle for one random int.
        // An alternative is to use successive hex digits from the long value
        // as an index into the array of 8 hex digits, consuming them on a 1-to-1 basis.

        // This method of boosting the transitions will result in the same generators
        // in pairs if constructed using a sequential series from 0.
        // A sequential series of n += 2 from 1 will be OK.

        final int upper = boostTransitions(NumberFactory.extractHi(value));
        // Note: Ensure the value is odd by combining with 1
        final int lower = boostTransitions(NumberFactory.extractLo(value) | 1);
        return NumberFactory.makeLong(upper, lower);
    }

    /**
     * Boost bit state transitions in the value.
     *
     * @param value the value
     * @return the new value
     */
    private static int boostTransitions(int value) {
        // Use signed shift to correctly count transitions at the ends
      final int n = Integer.bitCount(value ^ (value >> 1));
      // Set the value at the lowest 0.5% using a BinomialDistribution:
      // p(X<9|n=31,p=0.5) = 0.0053
      // Note: The xor mask will not effect the last bit (which may be odd) since 0xa = 0b1010.
      return (n < 9) ? value ^ 0xaaaaaaaa : value;
    }

    /** {@inheritDoc} */
    @Override
    protected byte[] getStateInternal() {
        return composeStateInternal(NumberFactory.makeByteArray(new long[] {x1, w1, s1}),
                                    super.getStateInternal());
    }

    /** {@inheritDoc} */
    @Override
    protected void setStateInternal(byte[] s) {
        final byte[][] c = splitStateInternal(s, STATE_SIZE * 8);

        final long[] tmp = NumberFactory.makeLongArray(c[0]);
        x1 = tmp[0];
        w1 = tmp[1];
        s1 = tmp[2];

        super.setStateInternal(c[1]);
    }

    /** {@inheritDoc} */
    @Override
    public int next() {
        x1 *= x1;
        x1 += w1 += s1;
        return (int) (x1 = (x1 >>> 32) | (x1 << 32));
    }
}
