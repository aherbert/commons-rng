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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

import org.apache.commons.rng.RestorableUniformRandomProvider;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;

/**
 * Executes benchmark to compare the speed of generation of random numbers
 * from the various source providers.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-server", "-Xms128M", "-Xmx128M"})
public class GenerationPerformance {
    /**
     * The benchmark state (retrieve the various "RandomSource"s).
     */
    @State(Scope.Benchmark)
    public static class Sources {
        /**
         * RNG providers.
         */
        @Param({
                //"SPLIT_MIX_64",
                "MWC_256",
                "MWC_256B",
                "MWC_256C",
                //"Counter",
                })
        private String randomSourceName;

        /** RNG. */
        private RestorableUniformRandomProvider provider;

        /**
         * @return the RNG.
         */
        public UniformRandomProvider getGenerator() {
            return provider;
        }

        /** Instantiates generator. */
        @Setup
        public void setup() {
            final RandomSource randomSource = RandomSource.valueOf(randomSourceName);
            provider = RandomSource.create(randomSource);
        }
    }

    /**
     * Number of random values to generate.
     */
    @Param({
        //"1", 
        //"100", 
        //"10000", 
        "1000000",
        })
    private int numValues;

    /**
     * @param sources Source of randomness.
     * @param bh Data sink.
     */
    @Benchmark
    public void nextInt(Sources sources, Blackhole bh) {
        int value = 0;
        UniformRandomProvider rng = sources.getGenerator(); 
        for (int i = 0; i < numValues; i++) {
            value = rng.nextInt();
        }
        bh.consume(value);
    }

    /**
     * @param sources Source of randomness.
     * @param bh Data sink.
     */
    @Benchmark
    public void nextLong(Sources sources, Blackhole bh) {
        long value = 0;
        UniformRandomProvider rng = sources.getGenerator(); 
        for (int i = 0; i < numValues; i++) {
            value = rng.nextLong();
        }
        bh.consume(value);
    }

    /**
     * @param sources Source of randomness.
     * @param bh Data sink.
     */
    @Benchmark
    public void nextFloat(Sources sources, Blackhole bh) {
        float value = 0;
        UniformRandomProvider rng = sources.getGenerator(); 
        for (int i = 0; i < numValues; i++) {
            value = rng.nextFloat();
        }
        bh.consume(value);
    }

    /**
     * @param sources Source of randomness.
     * @param bh Data sink.
     */
    @Benchmark
    public void nextDouble(Sources sources, Blackhole bh) {
        double value = 0;
        UniformRandomProvider rng = sources.getGenerator(); 
        for (int i = 0; i < numValues; i++) {
            value = rng.nextDouble();
        }
        bh.consume(value);
    }
}
