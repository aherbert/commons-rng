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
import org.openjdk.jmh.annotations.CompilerControl;
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

import java.util.concurrent.ThreadLocalRandom;
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
public class GenerationPerformance3 {
    /**
     * RNG providers.
     */
//    @Param({
//            //"SPLIT_MIX_64",
//            "MWC_256",
//            //"MWC_256B",
//            "MWC_256C",
//            //"Int0",
//            //"IntCounter",
//            //"IntCounterShift",
//            "IntValue",
//            })
//    private String randomSourceName;

    /** RNG. */
    private RestorableUniformRandomProvider provider;

    private int intValue;

    /** Create the baseline values */
    @Setup
    public void setup() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        intValue = rng.nextInt();

//        if (randomSourceName.equals("IntCounter")) {
//            provider = new IntCounter();
//        } else if (randomSourceName.equals("Int0")) {
//            provider = new Int0();
//        } else if (randomSourceName.equals("IntCounterShift")) {
//            provider = new IntCounterShift();
//        } else if (randomSourceName.equals("IntValue")) {
//            provider = new IntValue();
//        } else {
//            final RandomSource randomSource = RandomSource.valueOf(randomSourceName);
//            provider = RandomSource.create(randomSource);
//        }
    }

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
                //"Int0",
                //"IntCounter",
                //"IntCounterShift",
                "IntValue",
                //"IntFlip",
                //"IntNegation",
                //"FixedRNG",
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
            } else if (randomSourceName.equals("IntCounterShift")) {
                provider = new IntCounterShift();
            } else if (randomSourceName.equals("IntValue")) {
                provider = new IntValue();
            } else if (randomSourceName.equals("IntFlip")) {
                provider = new IntFlip();
            } else if (randomSourceName.equals("IntNegation")) {
                provider = new IntNegation();
            } else if (randomSourceName.equals("FixedRNG")) {
                provider = new IntNegation();
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

    private static final class IntValue extends IntProvider {
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

    private static final class FixedRNG implements UniformRandomProvider {
        /*
         * DON'T make these final! They must be a viewed by the JVM as something that
         * cannot be optimised away.
         *
         * <p>TODO: Understand why this does not run as fast as the IntValue implementation.
         * Is it the layout in memory?
         */
        private int intValue;
        private int intValueN;
        private long longValue;
        private long longValueN;
        private float floatValue;
        private double doubleValue;
        private byte byteValue;
        private boolean booleanValue;

        FixedRNG() {
            //ThreadLocalRandom rng = ThreadLocalRandom.current();
            //byteValue = (byte) rng.nextInt(256);
            //intValue = rng.nextInt();
            //longValue = rng.nextLong();
            //booleanValue = rng.nextBoolean();
            //floatValue = rng.nextFloat();
            //doubleValue = rng.nextDouble();
        }

        @Override
        public void nextBytes(byte[] bytes) {
            // Avoid the optimisation to an array fill by including the loop counter.
            // Note this may be slower than the current RNG implementation which processes
            // chunks of bytes from the int/long.
            nextBytes(bytes, 0, bytes.length);
            //for (int i = 0; i < bytes.length; i++) {
            //    bytes[i] = (byte) (i + byteValue);
            //}
        }

        @Override
        public void nextBytes(byte[] bytes, int start, int len) {
            // Avoid the optimisation to an array fill by including the loop counter
            // Note this may be slower than the current RNG implementation which processes
            // chunks of bytes from the int/long.
            for (int i = start; i < len; i++) {
                bytes[i] = (byte) (i + byteValue);
            }
        }

        @Override
        public int nextInt() {
            return intValue;
        }

        @Override
        public int nextInt(int n) {
            return intValueN; // Will be zero
        }

        @Override
        public long nextLong() {
            return longValue;
        }

        @Override
        public long nextLong(long n) {
            return longValueN; // Will be zero
        }

        @Override
        public boolean nextBoolean() {
            return booleanValue;
        }

        @Override
        public float nextFloat() {
            return floatValue;
        }

        @Override
        public double nextDouble() {
            return doubleValue;
        }
    }

    private static class IntFlip extends IntProvider {
        /**
         * The fixed value to return.
         *
         * <p>DON'T make this final! This must be a viewed by the JVM as something that
         * cannot be optimised away.
         */
        private int value = (int) System.currentTimeMillis();

        @Override
        public int next() {
            // Flip the bits
            value = ~value;
            return value;
        }
    }

    private static class IntNegation extends IntProvider {
        /**
         * The fixed value to return.
         *
         * <p>DON'T make this final! This must be a viewed by the JVM as something that
         * cannot be optimised away.
         */
        private int value = (int) System.currentTimeMillis();

        @Override
        public int next() {
            // Negate
            value = -value;
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
//    @Param({
//        //"1", 
////        "5",
////        "10",
////        "15",
////        "20",
////        "25",
//          "500000",
//          "1000000",
//          "1500000",
//          "2000000",
//          "2500000",
//        })
    private int numValues;


    public void target_blank(Sources sources) {
        sources.getGenerator().nextInt();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void target_dontInline(Sources sources) {
        sources.getGenerator().nextInt();
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    public void target_inline(Sources sources) {
        sources.getGenerator().nextInt();
    }

//@Benchmark
public void baselineMethodCall() {
    // do nothing, this is a baseline for JMH running a method
}

//@Benchmark
public int baselineConsumeInt() {
    return intValue;
}

    //@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
    //@Benchmark
    public void nextInt1() {
        provider.nextInt();
    }

    //@Benchmark
    public void nextIntBlank(Sources sources) {
        target_blank(sources);
    }

    //@Benchmark
    public void nextIntInline(Sources sources) {
        target_inline(sources);
    }

    //@Benchmark
    public void nextIntDontInline(Sources sources) {
        target_dontInline(sources);
    }

//@Benchmark
public int nextInt1(Sources sources) {
    return sources.getGenerator().nextInt();
}

@Benchmark
public long nextLong1(Sources sources) {
    return sources.getGenerator().nextLong();
}

    //@Benchmark
    public void nextInt1b(Sources sources) {
        sink(sources.getGenerator().nextInt());
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static void sink(int v) {
        // IT IS VERY IMPORTANT TO MATCH THE SIGNATURE TO AVOID AUTOBOXING.
        // The method intentionally does nothing.
    }

    /**
     */
    //@Benchmark
    public long nextLong1() {
        return provider.nextLong();
    }

    /**
     * @param bh Data sink.
     */
    @BenchmarkMode(Mode.AverageTime)
    //@Benchmark
    public void nextInt(Blackhole bh) {
        for (int i = 0; i < numValues; i++) {
            bh.consume(provider.nextInt());
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
