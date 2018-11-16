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
package org.apache.commons.rng.core;

import java.util.Arrays;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.commons.rng.core.source32.JDKRandom;
import org.apache.commons.rng.core.source32.Well512a;
import org.apache.commons.rng.core.source32.Well1024a;
import org.apache.commons.rng.core.source32.Well19937a;
import org.apache.commons.rng.core.source32.Well19937c;
import org.apache.commons.rng.core.source32.Well44497a;
import org.apache.commons.rng.core.source32.Well44497b;
import org.apache.commons.rng.core.source32.ISAACRandom;
import org.apache.commons.rng.core.source32.MersenneTwister;
import org.apache.commons.rng.core.source32.MultiplyWithCarry256;
import org.apache.commons.rng.core.source32.KISSRandom;
import org.apache.commons.rng.core.source64.SplitMix64;
import org.apache.commons.rng.core.source64.XorShift1024Star;
import org.apache.commons.rng.core.source64.TwoCmres;
import org.apache.commons.rng.core.source64.MersenneTwister64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.rng.RestorableUniformRandomProvider;

/**
 * The purpose of this class is to provide the list of all generators
 * implemented in the library.
 * The list must be updated with each new RNG implementation.
 *
 * @see #list()
 * @see #list32()
 * @see #list64()
 */
public class ProvidersList {
    /** List of all RNGs implemented in the library. */
    private static final List<RestorableUniformRandomProvider[]> LIST =
        new ArrayList<RestorableUniformRandomProvider[]>();
    /** List of 32-bits based RNGs. */
    private static final List<RestorableUniformRandomProvider[]> LIST32 =
        new ArrayList<RestorableUniformRandomProvider[]>();
    /** List of 64-bits based RNGs. */
    private static final List<RestorableUniformRandomProvider[]> LIST64 =
        new ArrayList<RestorableUniformRandomProvider[]>();

    /** 
     * Provide a random byte seed encoded as a hex string (length must be even).
     *
     * <p>1024 bytes = 2048 hex characters. This provides 128 longs and 256 integers.
     */
    private static final String HEX_SEED = 
              "75d1fb070512e503f9635706318f2dabbd6e2120addbd50c8ff8272654f3082c"
            + "edf244845db03415bf661051d9a487c622b6411a6773596f459465c72d59e870"
            + "c6bd90089a6fb7294d3beea8feba7d8307745cbd1233b6f85ff0294f87483d9a"
            + "ea77939ced0eb7e41617b52ac5a0d32d49c456e7b4c2485bd8d8be990f626458"
            + "86c5194aa34eb1f83efc38808ebf1fcd813667f88e7a76191dee40f662968a8c"
            + "71cf6493497ad77e00efbb59bfbf9b7664f8c32b4f5f29064329174bbce4870d"
            + "bb6bfd683415428d1c2c76eacca237df7119ecf1e6eac7cc97f3548bb10255b0"
            + "74a44010fa4811fc622104cea4ca045c674bbaa65f2273879301c4902bdb2f72"
            + "b9e7b1f5414704bb240a6a4d490a39739a557338b1e58cb5e81d8157172482d0"
            + "407795dffa92caacf3db3c848e1f2962bd5e8ddd4691a49779eea65d38d9d584"
            + "9b7ea054677618a9f56893877c34bb5d928308c8be19e07627b646cc743d0ddf"
            + "44757e64c1268eeb5e88a55a718f4fb9ba313b68ab1729efff6989192c95a2d8"
            + "42292766523b8d03e2dc10b1157b45909ecf239e59a4334eafa0b48c39a980dc"
            + "0f4238dcdaf306a57a29758a23f992eb7d13439ad982ec5a83aac630a31e05a3"
            + "0724a7babb5ebbcc00144c90a9a79c976f7cdd516fd94a432e815e3aeffdccb0"
            + "515a409dcfe7f492c0015234706eff83eda607cf76ec9b0f74a6d3050fc1f469"
            + "42475ef814847c5053fffa1e5d732f8954f1d6a678ce88e19ca92f6d5b945ac3"
            + "f872c05f3481659f27f570f3d387e1a75bc17408d454c6b4190d78c6aeac2cc5"
            + "0d8042b373ca4f149e32b81933076c23c2e3e36d7ba29079dd0e605b617c6378"
            + "ee714b66cb0959f4ff127cf1f0ad42919780fc51174c8818a630d32bfca5b03c"
            + "698e63f774dc979a27dddc795d5146ab49baf32b79014bf2e533e82d663da5d8"
            + "42d57fbe1e1a270ccb17c6daf1ea6cad92a4f6b311dee23f5362106d0cda8130"
            + "ae391221e790a610b6bbd9f64bf988783a5fde4b0d3504a417aac0a1a70b8c4a"
            + "6395ae21e41c1efa31a37737b15d4c8c77909306c41028ea9e04d7f0141b2c13"
            + "d6e0d20a0df619b575eeb1c6aebd7f03b56060c4bf4cf1fdcea3f0a9d03d8e61"
            + "54d8d9c92fb5eba0f3f3ddaa82190c766d0c5acdc52b29cf4dfe45731434e443"
            + "8652301bbba00bad464a467abfe46283adfd05bffad627171140fbbbf6d080a0"
            + "9971e17c0771abe6e354eb2e3330449080cf2133ac5b4a4240c357c50b5b144b"
            + "7c8fd76935fbb2741b4def4ea7f2455d794e600a121d16172fc4c951ba152b89"
            + "99232249e456e47ed12270959e2d5ae97a73b6d448a958e9c644e51af7199ce8"
            + "ed6a277f88a599093214e70832fe52ebf99862ce2e59da603a909075c238a8cf"
            + "42f359f7d269716bf53fcd10e04f3149b55dafce78cc8d5f2753723b1a202d86";

    static {
        try {
            // Decode the seed into bytes.
            byte[] bytes = Hex.decodeHex(HEX_SEED);
            
            bytes = new SecureRandom().generateSeed(bytes.length);
            System.out.println(Hex.encodeHexString(bytes).replaceAll("(.{64})", "$1\n"));

            // Create long seeds
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
            long[] longSeed = new long[bytes.length / 8];
            if (longSeed.length < 10) {
                throw new Exception("Not enough seed bytes");
            }
            for (int i = 0; i < longSeed.length; i++) {
                longSeed[i] = dis.readLong();
            }
            dis.close();

            // Create int seeds
            dis = new DataInputStream(new ByteArrayInputStream(bytes));
            int[] intSeed = new int[bytes.length / 4];
            for (int i = 0; i < intSeed.length; i++) {
                intSeed[i] = dis.readInt();
            }
            dis.close();

            // "int"-based RNGs.
            add(LIST32, new JDKRandom(longSeed[0]));
            add(LIST32, new MersenneTwister(intSeed));
            add(LIST32, new Well512a(intSeed));
            add(LIST32, new Well1024a(intSeed));
            add(LIST32, new Well19937a(intSeed));
            add(LIST32, new Well19937c(intSeed));
            add(LIST32, new Well44497a(intSeed));
            add(LIST32, new Well44497b(intSeed));
            add(LIST32, new ISAACRandom(intSeed));
            add(LIST32, new MultiplyWithCarry256(intSeed));
            add(LIST32, new KISSRandom(intSeed));
            // ... add more here.

            // "long"-based RNGs.
            add(LIST64, new SplitMix64(longSeed[0]));
            add(LIST64, new XorShift1024Star(longSeed));
            add(LIST64, new TwoCmres(intSeed[0]));
            add(LIST64, new TwoCmres(intSeed[0], 5, 8));
            add(LIST64, new MersenneTwister64(longSeed));
            // ... add more here.

            // Do not modify the remaining statements.
            // Complete list.
            LIST.addAll(LIST32);
            LIST.addAll(LIST64);
        } catch (Exception e) {
            System.err.println("Unexpected exception while creating the list of generators: " + e);
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }

    /**
     * Class contains only static methods.
     */
    private ProvidersList() {}

    /**
     * Helper to statisfy Junit requirement that each parameter set contains
     * the same number of objects.
     */
    private static void add(List<RestorableUniformRandomProvider[]> list,
                            RestorableUniformRandomProvider rng) {
        list.add(new RestorableUniformRandomProvider[] { rng });
    }

    /**
     * Subclasses that are "parametric" tests can forward the call to
     * the "@Parameters"-annotated method to this method.
     *
     * @return the list of all generators.
     */
    public static Iterable<RestorableUniformRandomProvider[]> list() {
        return Collections.unmodifiableList(LIST);
    }

    /**
     * Subclasses that are "parametric" tests can forward the call to
     * the "@Parameters"-annotated method to this method.
     *
     * @return the list of 32-bits based generators.
     */
    public static Iterable<RestorableUniformRandomProvider[]> list32() {
        return Collections.unmodifiableList(LIST32);
    }

    /**
     * Subclasses that are "parametric" tests can forward the call to
     * the "@Parameters"-annotated method to this method.
     *
     * @return the list of 64-bits based generators.
     */
    public static Iterable<RestorableUniformRandomProvider[]> list64() {
        return Collections.unmodifiableList(LIST64);
    }
}
