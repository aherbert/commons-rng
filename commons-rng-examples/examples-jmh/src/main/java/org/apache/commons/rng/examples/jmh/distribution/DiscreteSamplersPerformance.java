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

package org.apache.commons.rng.examples.jmh.distribution;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.examples.jmh.RandomSources;
import org.apache.commons.rng.sampling.distribution.DiscreteSampler;
import org.apache.commons.rng.sampling.distribution.DiscreteUniformSampler;
import org.apache.commons.rng.sampling.distribution.GeometricSampler;
import org.apache.commons.rng.sampling.distribution.LargeMeanPoissonSampler;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangBinomialSampler;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangBinomialSampler2;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSampler;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSampler2D;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerBase10;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerBase10Byte;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerBase10Byte2D;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerBase10Short;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerBase10Short2D;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerBase10_2D;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerByte;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerByte2D;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerDynamic;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerDynamic.BaseOption;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerDynamicDelegated;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerShort;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangDiscreteSamplerShort2D;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangSmallMeanPoissonSampler;
import org.apache.commons.rng.sampling.distribution.MarsagliaTsangWangSmallMeanPoissonSampler2;
import org.apache.commons.rng.sampling.distribution.RejectionInversionZipfSampler;
import org.apache.commons.rng.sampling.distribution.SmallMeanPoissonSampler;
import org.apache.commons.rng.simple.RandomSource;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Executes benchmark to compare the speed of generation of random numbers
 * from the various source providers for different types of {@link DiscreteSampler}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-server", "-Xms128M", "-Xmx128M"})
public class DiscreteSamplersPerformance {
    /**
     * The {@link DiscreteSampler} samplers to use for testing. Creates the sampler for each
     * {@link RandomSource} in the default {@link RandomSources}.
     */
    @State(Scope.Benchmark)
    public static class Sources extends RandomSources {
        /**
         * The sampler type.
         */
        @Param({//"DiscreteUniformSampler",
                //"RejectionInversionZipfSampler",
                //"SmallMeanPoissonSampler",
                //"LargeMeanPoissonSampler",
                //"GeometricSampler",
                "MarsagliaTsangWangDiscreteSampler",
                "MarsagliaTsangWangDiscreteSampler2D",
                "MarsagliaTsangWangDiscreteSamplerByte",
                "MarsagliaTsangWangDiscreteSamplerShort",
                "MarsagliaTsangWangDiscreteSamplerByte2D",
                "MarsagliaTsangWangDiscreteSamplerShort2D",
                "MarsagliaTsangWangDiscreteSamplerBase10",
                "MarsagliaTsangWangDiscreteSamplerBase10_2D",
                "MarsagliaTsangWangDiscreteSamplerBase10Byte",
                "MarsagliaTsangWangDiscreteSamplerBase10Short",
                "MarsagliaTsangWangDiscreteSamplerBase10Byte2D",
                "MarsagliaTsangWangDiscreteSamplerBase10Short2D",
                "MarsagliaTsangWangDiscreteSamplerDynamic",
                "MarsagliaTsangWangDiscreteSamplerDynamicBase10",
                "MarsagliaTsangWangDiscreteSamplerDynamicDelegated",
                "MarsagliaTsangWangDiscreteSamplerDynamicDelegatedBase10",
                //"MarsagliaTsangWangSmallMeanPoissonSampler",
                //"MarsagliaTsangWangSmallMeanPoissonSampler2",
                //"MarsagliaTsangWangBinomialSampler",
                //"MarsagliaTsangWangBinomialSampler2",
                })
        private String samplerType;

        /**
         * The sampler type.
         */
        @Param({
                "4",
                "8",
                "16",
                "32",
                "64",
                "128",
                })
        private int length;

        /** The sampler. */
        private DiscreteSampler sampler;

        /**
         * @return the sampler.
         */
        public DiscreteSampler getSampler() {
            return sampler;
        }

        /** Instantiates sampler. */
        @Override
        @Setup
        public void setup() {
            super.setup();
            final UniformRandomProvider rng = getGenerator();

            // This will be normalised by the sampler
            final double[] probabilities = new double[length];
            for (int i = 0; i < probabilities.length; i++) {
                probabilities[i] = (i + 1.0) / probabilities.length;
            }

            if ("DiscreteUniformSampler".equals(samplerType)) {
                sampler = new DiscreteUniformSampler(rng, -98, 76);
            } else if ("RejectionInversionZipfSampler".equals(samplerType)) {
                sampler = new RejectionInversionZipfSampler(rng, 43, 2.1);
            } else if ("SmallMeanPoissonSampler".equals(samplerType)) {
                sampler = new SmallMeanPoissonSampler(rng, 8.9);
            } else if ("LargeMeanPoissonSampler".equals(samplerType)) {
                // Note: Use with a fractional part to the mean includes a small mean sample
                sampler = new LargeMeanPoissonSampler(rng, 41.7);
            } else if ("GeometricSampler".equals(samplerType)) {
                sampler = new GeometricSampler(rng, 0.21);
            } else if ("MarsagliaTsangWangDiscreteSampler".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSampler(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSampler2D".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSampler2D(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerByte".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerByte(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerShort".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerShort(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerByte2D".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerByte2D(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerShort2D".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerShort2D(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerBase10".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerBase10(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerBase10_2D".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerBase10_2D(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerBase10Byte".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerBase10Byte(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerBase10Short".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerBase10Short(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerBase10Byte2D".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerBase10Byte2D(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerBase10Short2D".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerBase10Short2D(rng, probabilities);
            } else if ("MarsagliaTsangWangDiscreteSamplerDynamic".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerDynamic(rng, probabilities, BaseOption.BASE_64);
            } else if ("MarsagliaTsangWangDiscreteSamplerDynamicBase10".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerDynamic(rng, probabilities, BaseOption.BASE_1024);
            } else if ("MarsagliaTsangWangDiscreteSamplerDynamicDelegated".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerDynamicDelegated(rng, probabilities, MarsagliaTsangWangDiscreteSamplerDynamicDelegated.BaseOption.BASE_64);
            } else if ("MarsagliaTsangWangDiscreteSamplerDynamicDelegatedBase10".equals(samplerType)) {
                sampler = new MarsagliaTsangWangDiscreteSamplerDynamicDelegated(rng, probabilities, MarsagliaTsangWangDiscreteSamplerDynamicDelegated.BaseOption.BASE_1024);
            } else if ("MarsagliaTsangWangSmallMeanPoissonSampler".equals(samplerType)) {
                sampler = new MarsagliaTsangWangSmallMeanPoissonSampler(rng, 22.9);
            } else if ("MarsagliaTsangWangSmallMeanPoissonSampler2".equals(samplerType)) {
                sampler = new MarsagliaTsangWangSmallMeanPoissonSampler2(rng, 22.9);
            } else if ("MarsagliaTsangWangBinomialSampler".equals(samplerType)) {
                sampler = new MarsagliaTsangWangBinomialSampler(rng, 67, 0.33);
            } else if ("MarsagliaTsangWangBinomialSampler2".equals(samplerType)) {
                sampler = new MarsagliaTsangWangBinomialSampler2(rng, 67, 0.33);
            }
        }
    }

    // Benchmarks methods below.

    /**
     * The value.
     *
     * <p>This must NOT be final!</p>
     */
    private int value;

    /**
     * Baseline for the JMH timing overhead for production of an {@code int} value.
     *
     * @return the {@code int} value
     */
    @Benchmark
    public int baseline() {
        return value;
    }

    /**
     * Run the sampler.
     *
     * @param sources Source of randomness.
     * @return the sample value
     */
    @Benchmark
    public int nextInt(RandomSources sources) {
        return sources.getGenerator().nextInt();
    }

    /**
     * Run the sampler.
     *
     * @param sources Source of randomness.
     * @return the sample value
     */
    @Benchmark
    public int sample(Sources sources) {
        return sources.getSampler().sample();
    }
}
