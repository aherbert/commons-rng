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
import org.apache.commons.rng.core.source32.IntProvider;
import org.apache.commons.rng.simple.RandomSource;

/**
 * Executes benchmark to compare the speed of generation of random numbers
 * from the various source providers.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-server", "-Xms128M", "-Xmx128M"})
public class GenerationPerformance1 {
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
                //"MWC_256",
                //"MWC_256B",
                //"MWC_256C",
                //"Int0",
                //"IntCounter",
                //"IntCounterShift",
                "IntValue",
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
            if (randomSourceName.equals("IntCounter")) {
                provider = new IntCounter();
            } else if (randomSourceName.equals("Int0")) {
                provider = new Int0();
            } else if (randomSourceName.equals("IntValue")) {
                provider = new IntValue();
            } else if (randomSourceName.equals("IntCounterShift")) {
                provider = new IntCounterShift();
            } else {
                final RandomSource randomSource = RandomSource.valueOf(randomSourceName);
                provider = RandomSource.create(randomSource);
            }
        }
    }

    private static class Int0 extends IntProvider {
        @Override
        public int next() {
            return 0;
        }
    }

private static class IntValue extends IntProvider {
    /**
     * The fixed value to return.
     *
     * <p><strong>DON'T</strong> make this final!
     * This must be a viewed by the JVM as something that cannot be optimised away.
     */
    private int value; // = (int) System.currentTimeMillis();

    @Override
    public int next() {
        // This will not be optimised away since the source value is not final.
        return value;
    }
}

private static class IntCounter extends IntProvider {
    int state;

    @Override
    public int next() {
        // The least significant byte will change. Others bytes may change.
        // Not a good baseline as most of the time only the most significant
        // bits of an int are used.
        return ++state;

        // Ensures the most significant byte changes.
        // Not a good baseline as the reverse operation is expensive.
        //return Integer.reverse(++state);

        // The most significant byte will always change. Other bytes may change,
        // Not a good baseline as the reverse operation is expensive.
        //return Integer.reverseBytes(++state);

        // The most significant byte will always change. Other bytes are zero.
        //return ++state << 24;

        // The most significant byte will always change. Other bytes may change.
        // No bytes are lost.
        //return ++state << 24 | state >>> 24;
    }
}

    private static class IntCounterShift extends IntProvider {
        int state;

        @Override
        public int next() {
            // The least significant byte will change. Others bytes may change.
            // Not a good baseline as most of the time only the most significant
            // bits of an int are used.
            //return ++state;

            // Ensures the most significant byte changes.
            // Not a good baseline as the reverse operation is expensive.
            //return Integer.reverse(++state);

            // The most significant byte will always change. Other bytes may change,
            // Not a good baseline as the reverse operation is expensive.
            //return Integer.reverseBytes(++state);

            // The most significant byte will always change. Other bytes are zero.
            //return ++state << 24;

            // The most significant byte will always change. Other bytes may change.
            // No bytes are lost.
            return ++state << 24 | state >>> 24;
        }
    }

    /**
     * Number of random values to generate.
     */
    @Param({
        //"1", 
//        "5",
//        "10",
//        "15",
//        "20",
//        "25",
//          "500000",
//          "1000000",
//          "1500000",
//          "2000000",
          "2500000",
        })
    private int numValues;

    /**
     * @param sources Source of randomness.
     */
    //@Benchmark
    public int nextInt1(Sources sources) {
        return sources.getGenerator().nextInt();
    }

    /**
     * @param sources Source of randomness.
     */
    //@Benchmark
    public long nextLong1(Sources sources) {
        return sources.getGenerator().nextLong();
    }

    /**
     * @param sources Source of randomness.
     * @param bh Data sink.
     */
@Benchmark
public void nextInt(Sources sources, Blackhole bh) {
    for (int i = numValues; i > 0; i--) {
        bh.consume(sources.getGenerator().nextInt());
    }
}
//
//    /**
//     * @param sources Source of randomness.
//     * @param bh Data sink.
//     */
//    @Benchmark
//    public void nextLong(Sources sources, Blackhole bh) {
//        long value = 0;
//        UniformRandomProvider rng = sources.getGenerator(); 
//        for (int i = 0; i < numValues; i++) {
//            value = rng.nextLong();
//        }
//        bh.consume(value);
//    }
//
//    /**
//     * @param sources Source of randomness.
//     * @param bh Data sink.
//     */
//    @Benchmark
//    public void nextFloat(Sources sources, Blackhole bh) {
//        float value = 0;
//        UniformRandomProvider rng = sources.getGenerator(); 
//        for (int i = 0; i < numValues; i++) {
//            value = rng.nextFloat();
//        }
//        bh.consume(value);
//    }
//
//    /**
//     * @param sources Source of randomness.
//     * @param bh Data sink.
//     */
//    @Benchmark
//    public void nextDouble(Sources sources, Blackhole bh) {
//        double value = 0;
//        UniformRandomProvider rng = sources.getGenerator(); 
//        for (int i = 0; i < numValues; i++) {
//            value = rng.nextDouble();
//        }
//        bh.consume(value);
//    }
}
