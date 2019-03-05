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

package org.apache.commons.rng.examples.jmh;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.core.source64.LongProvider;
import org.apache.commons.rng.core.source64.SplitMix64;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Executes benchmark to compare the speed of generation of integer primitives
 * using the SplitMix algorithm.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = { "-server", "-Xms128M", "-Xmx128M" })
public class SplitMixPerformance {

    /** The seed. */
    private static long seed = ThreadLocalRandom.current().nextLong();

    /**
     * The SplitMix method
     */
    @State(Scope.Benchmark)
    public static class SplitMixGenerator {
        @Param({ "native", "cached" })
        private String method;

        /** RNG. */
        private UniformRandomProvider provider;

        /**
         * @return the RNG.
         */
        public UniformRandomProvider getGenerator() {
            return provider;
        }

        /** Instantiates generator. */
        @Setup
        public void setup() {
            if ("native".equals(method)) {
                provider = new SplitMix64Native();
            } else {
                provider = new SplitMix64(seed);
            }
        }
    }

    /**
     * Change the SplitMix64 algorithm to natively generate int values.
     */
    private static final class SplitMix64Native extends LongProvider {
        /** The state. */
        private long state = seed;

        /**
         * Get the next long.
         *
         * @return the long
         */
        @Override
        public final long next() {
            long z = state += 0x9e3779b97f4a7c15L;
            z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
            z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
            return z ^ (z >>> 31);
        }

        /**
         * Get the next int.
         *
         * <p>Returns the 32 high bits of Stafford variant 4 mix64 function as int.
         *
         * @return the int
         */
        @Override
        public final int nextInt() {
            long z = state += 0x9e3779b97f4a7c15L;
            z = (z ^ (z >>> 33)) * 0x62a9d9ed799705f5L;
            return (int)(((z ^ (z >>> 28)) * 0xcb24d0a5c88c35b3L) >>> 32);
        }
    }

    // Benchmark methods

    /**
     * @param source the source
     * @return the int
     */
    @Benchmark
    public int nextInt(SplitMixGenerator source) {
        return source.getGenerator().nextInt();
    }

    /**
     * @param source the source
     * @return the long
     */
    @Benchmark
    public long nextLong(SplitMixGenerator source) {
        return source.getGenerator().nextLong();
    }
}
