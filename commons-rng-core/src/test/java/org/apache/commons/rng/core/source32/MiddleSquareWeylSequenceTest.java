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

import org.apache.commons.rng.core.RandomAssert;
import org.junit.Test;

public class MiddleSquareWeylSequenceTest {
    @Test
    public void testReferenceCode() {
        /*
         * The data was generated using the following program based on the author's C code:
         *     https://mswsrng.wixsite.com/rand
         *
         * Note that this initialises the state using the Weyl increment (s).
         *
         * #include <stdio.h>
         * #include <stdint.h>
         *
         * uint64_t x, w, s;
         *
         * inline static uint32_t msws() {
         *     x *= x; x += (w += s); return x = (x>>32) | (x<<32);
         * }
         *
         * int main( int argc, const char* argv[] ) {
         *     x = w = s = 0xb5ad4eceda1ce2a9;
         *     for (int i=0; i<10; i++) {
         *         for (int j=0; j<4; j++) {
         *             printf("0x%08x, ", msws());
         *         }
         *         printf("\n");
         *     }
         * }
         */
        final long seed = 0xb5ad4eceda1ce2a9L;

        final int[] expectedSequence = {
            0x183596e3, 0xe0c92a80, 0x26cd77af, 0x50c3b2b3,
            0x0750c6f6, 0xcae2679a, 0x8c69e3cc, 0x6679fa59,
            0x38d2f364, 0x6191d1cb, 0x8377b75f, 0xc77fe21e,
            0x9cf04ee4, 0x6f07f168, 0x26e64734, 0x30ddbc83,
            0x20a21101, 0x28123009, 0x6ce78a4e, 0x324e2620,
            0x1ad918c6, 0x35b1bd00, 0x753f050d, 0x34c04844,
            0x3dc00d1a, 0xa35e01eb, 0x38b5f410, 0x69581828,
            0x400df9ee, 0xe697c6ce, 0xde681205, 0xfadc3f21,
            0x66f76c85, 0xf45339e2, 0xcaacc24c, 0x67374ddb,
            0xdadcd22b, 0xa2fa2b38, 0xe540fffd, 0xf84351ac,
        };

        RandomAssert.assertEquals(expectedSequence, new MiddleSquareWeylSequence(seed));
    }
    
    @Test
    public void testLowComplexitySeeds() {
        for (long seed : new long[] { 1, 2, 3, 4, -1, -2, -3, (1L << 63), (1L << 62) }) {
            MiddleSquareWeylSequence rng = new MiddleSquareWeylSequence(seed);
            System.out.printf("seed = %d%n", seed);
            //for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 10; j++) {
                    System.out.printf("%-12d ", rng.nextInt());
                }
                System.out.println();
            //}
        }
    }
}
