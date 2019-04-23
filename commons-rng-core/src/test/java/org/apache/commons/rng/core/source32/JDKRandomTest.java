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

import java.util.Random;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.core.BaseProvider;
import org.junit.Assert;
import org.junit.Test;

public class JDKRandomTest {
    @Test
    public void testReferenceCode() {
        final long refSeed = -1357111213L;
        final JDKRandom rng = new JDKRandom(refSeed);
        final Random jdk = new Random(refSeed);

        // This is a trivial test since "JDKRandom" delegates to "Random".

        final int numRepeats = 1000;
        for (int r = 0; r < numRepeats; r++) {
            Assert.assertEquals(r + " nextInt", jdk.nextInt(), rng.nextInt());
        }
    }

    /**
     * Test the {@link UniformRandomProvider#nextInt(int)} method matches that of
     * {@link Random#nextInt(int)}.
     *
     * <p>
     * This is required because the {@link JDKRandom} class overrides the default
     * method from {@link BaseProvider} which uses the least significant bits when n
     * is a power of 2 to delegate to the implementation in {@link Random} which
     * uses the most significant bits from the generator {@code int} value. This
     * special case is noted in the Random Javadoc (JDK 1.8):
     * </p>
     *
     * <blockquote>
     * "The algorithm treats the case where n is a power of two
     * specially: it returns the correct number of high-order bits from the
     * underlying pseudo-random number generator. In the absence of special
     * treatment, the correct number of <i>low-order</i> bits would be returned.
     * Linear congruential pseudo-random number generators such as the one
     * implemented by this class are known to have short periods in the sequence of
     * values of their low-order bits. Thus, this special case greatly increases the
     * length of the sequence of values returned by successive calls to this method
     * if n is a small power of two."
     * </blockquote>
     */
    @Test
    public void testReferenceCodeNextIntInRange() {
        final long refSeed = 97879213414L;
        final JDKRandom rng = new JDKRandom(refSeed);
        final Random jdk = new Random(refSeed);

        // Start at 2^0 and increase powers until overflow to negative
        for (int n = 1; n > 0; n <<= 1) {
            for (int r = 0; r < 10; r++) {
                Assert.assertEquals("Not equal when n is power of 2", jdk.nextInt(n), rng.nextInt(n));
                // Test some non-power of 2 values
                Assert.assertEquals("Not equal when n is not power of 2", jdk.nextInt(n + 5), rng.nextInt(n + 5));
            }
        } 
    }
}
