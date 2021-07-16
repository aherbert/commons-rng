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

import org.junit.Assert;
import org.junit.Test;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.core.source64.SplitMix64;
import org.apache.commons.rng.sampling.RandomAssert;
import org.apache.commons.rng.sampling.distribution.StableSampler.SpecialMath;
import org.apache.commons.rng.sampling.distribution.StableSampler.Beta0CMSStableSampler;
import org.apache.commons.rng.sampling.distribution.StableSampler.Beta0WeronStableSampler;
import org.apache.commons.rng.sampling.distribution.StableSampler.CMSStableSampler;
import org.apache.commons.rng.sampling.distribution.StableSampler.WeronStableSampler;
import org.apache.commons.rng.simple.RandomSource;

/**
 * Tests for the class {@link StableSampler}.
 *
 * <p>Note: Samples from the stable distribution are tested in
 * {@link ContinuousSamplerParametricTest}.
 *
 * <p>This contains tests for the assumptions made by the {@link StableSampler} implementation
 * of the Chambers-Mallows-Stuck (CMS) method as described in
 * Chambers, Mallows &amp; Stuck (1976) "A Method for Simulating Stable Random Variables".
 * Journal of the American Statistical Association. 71 (354): 340–344.
 *
 * <p>The test class contains copy implementations of the routines in the {@link StableSampler}
 * to test the algorithms with various parameters. This avoids excess manipulation
 * of the RNG provided to the stable sampler to test edge cases and also allows
 * calling the algorithm with values that are eliminated by the sampler (e.g. u=0).
 *
 * <p>Some tests of the sampler are performed that manipulate the underlying RNG to create
 * extreme values for the random deviates. This hits edges cases where the computation has
 * to be corrected.
 */
public class StableSamplerTest {
    /** pi / 2. */
    private static final double PI_2 = Math.PI / 2;
    /** pi / 4. */
    private static final double PI_4 = Math.PI / 4;
    /** pi/4 scaled by 2^-53. */
    private static final double PI_4_SCALED = 0x1.0p-55 * Math.PI;
    /** The interval between successive values of a uniform variate u.
     * This is the gap between the 2^53 dyadic rationals in [0, 1). */
    private static final double DU = 0x1.0p-53;
    /** The smallest non-zero sample from the ZigguratSampler.Exponential sampler. */
    private static final double SMALL_W = 7.39737323516E-19;
    /** A largest sample from the ZigguratSampler.Exponential after 4 recursions of the sample method.  */
    private static final double LARGE_W = 4 * 7.56927469415;
    /** The smallest value for alpha where 1 - (1-alpha) = alpha. */
    private static final double SMALLEST_ALPHA = 1.0 - nextDown(1.0);

    private static final double VALID_ALPHA = 1.23;
    private static final double VALID_BETA = 0.23;
    private static final double VALID_GAMMA = 2.34;
    private static final double VALID_DELTA = 3.45;

    @Test(expected = IllegalArgumentException.class)
    public void testAlphaZeroThrows() {
        createStableSampler(0.0, VALID_BETA, VALID_GAMMA, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAlphaBelowZeroThrows() {
        createStableSampler(nextDown(0.0), VALID_BETA, VALID_GAMMA, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAlphaTooCloseToZeroThrows() {
        // The realistic range for alpha is not Double.MIN_VALUE.
        // The number 1 - alpha must not be 1.
        // This is valid
        StableSampler s = createStableSampler(SMALLEST_ALPHA, VALID_BETA, VALID_GAMMA, VALID_DELTA);
        Assert.assertNotNull(s);

        // Smaller than this is still above zero but 1 - alpha == 1
        final double alphaTooSmall = SMALLEST_ALPHA / 2;
        Assert.assertNotEquals("Expected alpha to be positive", 0.0, alphaTooSmall, 0.0);
        Assert.assertEquals("Expected rounding to 1", 1.0, 1 - alphaTooSmall, 0.0);

        // Because alpha is effectively zero this will throw
        createStableSampler(alphaTooSmall, VALID_BETA, VALID_GAMMA, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAlphaAboveTwoThrows() {
        createStableSampler(nextUp(2.0), VALID_BETA, VALID_GAMMA, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAlphaNaNThrows() {
        createStableSampler(Double.NaN, VALID_BETA, VALID_GAMMA, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBetaBelowMinusOneThrows() {
        createStableSampler(VALID_ALPHA, nextDown(-1.0), VALID_GAMMA, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBetaAboveOneThrows() {
        createStableSampler(VALID_ALPHA, nextUp(1.0), VALID_GAMMA, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBetaNaNThrows() {
        createStableSampler(VALID_ALPHA, Double.NaN, VALID_GAMMA, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGammaNotStrictlyPositiveThrows() {
        createStableSampler(VALID_ALPHA, VALID_BETA, 0.0, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGammaInfThrows() {
        createStableSampler(VALID_ALPHA, VALID_BETA, Double.POSITIVE_INFINITY, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGammaNaNThrows() {
        createStableSampler(VALID_ALPHA, VALID_BETA, Double.NaN, VALID_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeltaInfThrows() {
        createStableSampler(VALID_ALPHA, VALID_BETA, VALID_GAMMA, Double.POSITIVE_INFINITY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeltaNegInfThrows() {
        createStableSampler(VALID_ALPHA, VALID_BETA, VALID_GAMMA, Double.NEGATIVE_INFINITY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeltaNaNThrows() {
        createStableSampler(VALID_ALPHA, VALID_BETA, VALID_GAMMA, Double.NaN);
    }

    /**
     * Creates the stable sampler. Used for factory constructor tests with a default RNG.
     *
     * @param alpha Stability parameter. Must be in range {@code (0, 2]}.
     * @param beta Skewness parameter. Must be in range {@code [-1, 1]}.
     * @param gamma Scale parameter. Must be strictly positive and finite.
     * @param delta Location parameter. Must be finite.
     * @return the stable sampler
     */
    private static StableSampler createStableSampler(double alpha, double beta, double gamma, double delta) {
        final UniformRandomProvider rng = new SplitMix64(0L);
        return StableSampler.of(rng, alpha, beta, gamma, delta);
    }

    /**
     * Assumption test:
     * Test the limits of the value {@code tau} at the extreme limits of {@code alpha}.
     * The expression is evaluated against the original CMS algorithm. The method
     * has been updated to ensure symmetry around zero.
     *
     * <p>The test demonstrates that tau can be zero even when beta is not zero. Thus
     * the choice of a beta=0 sampler must check tau and not beta.
     */
    @Test
    public void testTauLimits() {
        // At the limit of beta, tau ranges from 2/pi to 0 as alpha moves away from 1.
        final double beta = 1;

        // alpha -> 2: tau -> 0
        // alpha -> 0: tau -> 0
        Assert.assertEquals(0.0, CMSStableSampler.getTau(2, beta), 0.0);
        Assert.assertEquals(0.0, CMSStableSampler.getTau(0, beta), 0.0);

        // Full range over 0 to 2.
        for (int i = 0; i <= 512; i++) {
            // This is a power of 2 so the symmetric test uses an exact mirror
            final double alpha = (double) i / 256;
            final double tau = CMSStableSampler.getTau(alpha, beta);
            final double expected = getTauOriginal(alpha, beta);
            Assert.assertEquals(expected, tau, 1e-15);

            // Symmetric
            Assert.assertEquals(tau, CMSStableSampler.getTau(2 - alpha, beta), 0.0);
        }

        // alpha -> 1: tau -> beta / (pi / 2) = 0.6366
        final double limit = beta / PI_2;
        Assert.assertEquals(limit, CMSStableSampler.getTau(1, beta), 0.0);
        for (double alpha : new double[] {1.01, 1 + 1e-6, 1, 1 - 1e-6, 0.99}) {
            final double tau = CMSStableSampler.getTau(alpha, beta);
            final double expected = getTauOriginal(alpha, beta);
            Assert.assertEquals(expected, tau, 1e-15);
            // Approach the limit
            Assert.assertEquals(limit, tau, Math.abs(1 - alpha) + 1e-15);
        }

        // It can be zero if beta is zero or close to zero when alpha != 1.
        // This requires we check tau==0 instead of beta==0 to switch to
        // a beta = 0 sampler.
        Assert.assertEquals(0.0, CMSStableSampler.getTau(1.3, 0.0), 0.0);
        Assert.assertEquals(0.0, CMSStableSampler.getTau(1.5, Double.MIN_VALUE), 0.0);
        Assert.assertNotEquals(0.0, CMSStableSampler.getTau(1.0, Double.MIN_VALUE), 0.0);

        // The sign of beta determines the sign of tau.
        Assert.assertEquals(0.5, CMSStableSampler.getTau(1.5, beta), 0.0);
        Assert.assertEquals(0.5, CMSStableSampler.getTau(0.5, beta), 0.0);
        Assert.assertEquals(-0.5, CMSStableSampler.getTau(1.5, -beta), 0.0);
        Assert.assertEquals(-0.5, CMSStableSampler.getTau(0.5, -beta), 0.0);

        // Check monototic at the transition point to switch to a different computation.
        final double tau1 = CMSStableSampler.getTau(nextDown(1.5), 1);
        final double tau2 = CMSStableSampler.getTau(1.5, 1);
        final double tau3 = CMSStableSampler.getTau(nextUp(1.5), 1);
        Assert.assertTrue(tau1 > tau2);
        Assert.assertTrue(tau2 > tau3);
        // Test symmetry at the transition
        Assert.assertEquals(tau1, CMSStableSampler.getTau(2 - nextDown(1.5), 1), 0.0);
        Assert.assertEquals(tau2, CMSStableSampler.getTau(0.5, 1), 0.0);
        Assert.assertEquals(tau3, CMSStableSampler.getTau(2 - nextUp(1.5), 1), 0.0);
    }

    /**
     * Gets tau using the original method from the CMS algorithm implemented in the
     * program RSTAB. This does not use {@link SpecialMath#tan2(double)} but uses
     * {@link Math#tan(double)} to implement {@code tan(x) / x}.
     *
     * @param alpha alpha
     * @param beta the beta
     * @return tau
     */
    private static double getTauOriginal(double alpha, double beta) {
        final double eps = 1 - alpha;
        // Compute RSTAB prefactor
        double tau;

        // Use the method from Chambers et al (1976).
        // TAN2(x) = tan(x) / x
        // PIBY2 = pi / 2
        // Comments are the FORTRAN code from the RSTAB routine.

        if (eps > -0.99) {
            // TAU = BPRIME / (TAN2(EPS * PIBY2) * PIBY2)
            final double tan2 = eps == 0 ? 1 : Math.tan(eps * PI_2) / (eps * PI_2);
            tau = beta / (tan2 * PI_2);
        } else {
            // TAU = BPRIME * PIBY2 * EPS * (1.-EPS) * TAN2 ((1. -EPS) * PIBY2)
            final double meps1 = 1 - eps;
            final double tan2 = Math.tan(meps1 * PI_2) / (meps1 * PI_2);
            tau = beta * PI_2 * eps * meps1 * tan2;
        }

        return tau;
    }

    /**
     * Assumption test:
     * Test the value {@code a2} is not zero. Knowing {@code a2} is not zero simplifies
     * correction of non-finite results from the CMS algorithm.
     */
    @Test
    public void testA2IsNotZero() {
        // The extreme limit of the angle phiby2. This is ignored by the sampler
        // as it can result in cancellation of terms and invalid results.
        final double p0 = getU(Long.MIN_VALUE);
        Assert.assertEquals(-PI_4, p0, 0.0);

        // These are the limits to generate (-pi/4, pi/4)
        final double p1 = getU(Long.MIN_VALUE + (1 << 10));
        final double p2 = getU(Long.MAX_VALUE);
        Assert.assertNotEquals(-PI_4, p1, 0.0);
        Assert.assertNotEquals(PI_4, p2, 0.0);
        Assert.assertEquals(-PI_4 + PI_4 * DU, p1, 0.0);
        Assert.assertEquals(PI_4 - PI_4 * DU, p2, 0.0);

        for (double phiby2 : new double[] {p1, p2}) {
            // phiby2 in (-pi/4, pi/4)
            // a in (-1, 1)
            final double a = phiby2 * SpecialMath.tan2(phiby2);
            Assert.assertEquals(Math.copySign(nextDown(1.0), phiby2), a, 0.0);
            final double da = a * a;
            final double a2 = 1 - da;
            // The number is close to but not equal to zero
            Assert.assertNotEquals(0.0, a2, 0.0);
            // The minimum value of a2 is 2.220E-16 = 2^-52
            Assert.assertEquals(0x1.0p-52, a2, 0.0);
        }
    }

    /**
     * Assumption test:
     * Test the value of the numerator used to compute z. If this is negative then
     * computation of log(z) creates a NaN. This effect occurs when the uniform
     * random deviate u is either 0 or 1 and beta is -1 or 1. The effect is reduced
     * when u is in the range {@code (0, 1)} but not eliminated. The test
     * demonstrates: (a) the requirement to check z during the sample method when
     * {@code alpha!=1}; and (b) when {@code alpha=1} then z cannot be zero when u
     * is in the open interval {@code (0, 1)}.
     */
    @Test
    public void testZIsNotAlwaysAboveZero() {
        // A long is used to create phi/2:
        // The next to limit values for the phi/2
        final long x00 = Long.MIN_VALUE;
        final long x0 = Long.MIN_VALUE + (1 << 10);
        final long x1 = Long.MAX_VALUE;
        Assert.assertEquals(-PI_4, getU(x00), 0.0);
        Assert.assertEquals(-PI_4 + DU * PI_4, getU(x0), 0.0);
        Assert.assertEquals(PI_4 - DU * PI_4, getU(x1), 0.0);
        // General case numerator:
        // b2 + 2 * phiby2 * bb * tau
        // To generate 0 numerator requires:
        // b2 == -2 * phiby2 * bb * tau
        //
        // The expansion of the terms is:
        // 1 - tan^2(eps * phi/2)
        // == -phi * tan2(eps * phi/2) *        beta
        //                               -----------------------
        //                               tan2(eps * pi/2) * pi/2
        //
        // == -2 * phi * tan2(eps * phi/2) * beta
        //    -----------------------------------
        //          pi * tan2(eps * pi/2)
        //
        // == -2 * phi * tan(eps * phi/2) / (eps * phi/2) * beta
        //    --------------------------------------------------
        //          pi * tan(eps * pi/2) / (eps * pi/2)
        //
        // if phi/2 = pi/4, x = eps * phi/2:
        // == -2 * pi/4 * tan(x) / (x) * beta
        //    ---------------------------------
        //          pi * tan(2x) / (2x)
        //
        // This is a known double-angle identity for tan:
        // 1 - tan^2(x) == -2 tan(x) * beta
        //                 ---------
        //                  tan(2x)
        // Thus if |beta|=1 with opposite sign to phi, and |phi|=pi/2
        // the numerator is zero for all alpha.
        // This is not true due to floating-point
        // error but the following cases are known to exhibit the result.

        Assert.assertEquals(0.0, computeNumerator(0.859375, 1, x00), 0.0);
        // Even worse to have negative as the log(-ve) = nan
        Assert.assertTrue(0.0 > computeNumerator(0.9375, 1, x00));
        Assert.assertTrue(0.0 > computeNumerator(1.90625, 1, x00));

        // As phi reduces in magnitude the equality fails.
        // The numerator=0 can often be corrected
        // with the next random variate from the range limit.
        Assert.assertTrue(0.0 < computeNumerator(0.859375, 1, x0));
        Assert.assertTrue(0.0 < computeNumerator(0.9375, 1, x0));
        Assert.assertTrue(0.0 < computeNumerator(1.90625, 1, x0));

        // WARNING:
        // Even when u is not at the limit floating point error can still create
        // a bad numerator. This is rare but shows we must still detect this edge
        // case.
        Assert.assertTrue(0.0 > computeNumerator(0.828125, 1, x0));
        Assert.assertTrue(0.0 > computeNumerator(1.291015625, -1, x1));

        // beta=0 case the numerator reduces to b2:
        // b2 = 1 - tan^2((1-alpha) * phi/2)
        // requires tan(x)=1; x=pi/4.
        // Note: tan(x) = x * SpecialMath.tan2(x) returns +/-1 for u = +/-pi/4.
        Assert.assertEquals(-1, SpecialMath.tan2(getU(x00)) * getU(x00), 0.0);
        // Using the next value in the range this is not an issue.
        // The beta=0 sampler does not have to check for z=0.
        Assert.assertTrue(-1 < SpecialMath.tan2(getU(x0)) * getU(x0));
        Assert.assertTrue(1 > SpecialMath.tan2(getU(x1)) * getU(x1));
        // Use alpha=2 so 1-alpha (eps) is at the limit
        final double beta = 0;
        Assert.assertEquals(0.0, computeNumerator(2, beta, x00), 0.0);
        Assert.assertTrue(0.0 < computeNumerator(2, beta, x0));
        Assert.assertTrue(0.0 < computeNumerator(2, beta, x1));
        Assert.assertTrue(0.0 < computeNumerator(nextDown(2), beta, x0));
        Assert.assertTrue(0.0 < computeNumerator(nextDown(2), beta, x1));

        // alpha=1 case the numerator reduces to:
        // 1 + 2 * phi/2 * tau
        // A zero numerator requires:
        // 2 * phiby2 * tau = -1
        //
        // tau = 2 * beta / pi
        // phiby2 = -pi / (2 * 2 * beta)
        // beta = 1 => phiby2 = -pi/4
        // beta = -1 => phiby2 = pi/4
        // The alpha=1 sampler does not have to check for z=0 if phiby2 excludes -pi/4.
        final double alpha = 1;
        Assert.assertEquals(0.0, computeNumerator(alpha, 1, x00), 0.0);
        // Next value of u computes above zero
        Assert.assertTrue(0.0 < computeNumerator(alpha, 1, x0));
        Assert.assertTrue(0.0 < computeNumerator(alpha, -1, x1));
        // beta < 1 => u < 0
        // beta > -1 => u > 1
        // z=0 not possible with any other beta
        Assert.assertTrue(0.0 < computeNumerator(alpha, nextUp(-1), x00));
    }

    /**
     * Compute the numerator value for the z coefficient in the CMS algorithm.
     *
     * @param alpha Stability parameter. Must be in range {@code (0, 2]}.
     * @param beta Skewness parameter. Must be in range {@code [-1, 1]}.
     * @param x The random long used to generate the uniform deviate in the range {@code [-pi/4, pi/4)}.
     * @return numerator
     */
    private static double computeNumerator(double alpha, double beta, long x) {
        final double phiby2 = getU(x);
        final double eps = 1 - alpha;
        final double tau = CMSStableSampler.getTau(alpha, beta);

        final double bb = SpecialMath.tan2(eps * phiby2);
        final double b = eps * phiby2 * bb;
        // Compute some necessary subexpressions
        final double db = b * b;
        final double b2 = 1 - db;
        // Compute z coefficient numerator.
        return b2 + 2 * phiby2 * bb * tau;
    }

    /**
     * Assumption test:
     * Test the CMS algorithm can compute the value {@code d} without creating a NaN
     * when {@code z} is any non-zero finite value. When the value {@code z} is zero or infinite
     * the computation may multiply infinity by zero and create NaN.
     */
    @Test
    public void testComputeDWhenZIsFiniteNonZero() {
        final double[] zs = {Double.MIN_VALUE, Double.MAX_VALUE};

        final double[] alphas = {2, 1.5, 1 + 1e-6, 1, 1 - 1e-6, 0.5, 0.01, 1e-10, SMALLEST_ALPHA};
        for (final double alpha : alphas) {
            // Finite z
            for (final double z : zs) {
                // The result may be infinite, but not NaN
                Assert.assertNotEquals(Double.NaN, computeD(alpha, z), 0.0);
            }

            // May be invalid with z=0 or z=inf as some combinations multiply the
            // infinity by 0 to create NaN.

            // When z=0, log(z) = -inf, d = d2(sign(1-alpha) * -inf) * -inf
            final double d0 = computeD(alpha, 0);
            if (alpha < 1) {
                // d2(-inf) * -inf = 0 * -inf = NaN
                Assert.assertEquals(Double.NaN, d0, 0.0);
            } else if (alpha == 1) {
                // d2(0 * -inf) -> NaN
                Assert.assertEquals(Double.NaN, d0, 0.0);
            } else {
                // alpha > 1
                // d2(inf) * -inf = -inf
                Assert.assertEquals(Double.NEGATIVE_INFINITY, d0, 0.0);
            }

            // When z=inf, log(z) = inf, d = d2(sign(1-alpha) * inf) * inf
            final double di = computeD(alpha, Double.POSITIVE_INFINITY);
            if (alpha < 1) {
                // d2(inf) * inf = inf
                Assert.assertEquals(Double.POSITIVE_INFINITY, di, 0.0);
            } else if (alpha == 1) {
                // d2(0 * inf) -> NaN
                Assert.assertEquals(Double.NaN, di, 0.0);
            } else {
                // alpha > 1
                // d2(-inf) * inf = 0 * inf = NaN
                Assert.assertEquals(Double.NaN, di, 0.0);
            }
        }
    }

    /**
     * Compute the {@code d} value in the CMS algorithm.
     *
     * @param alpha alpha
     * @param z z
     * @return d
     */
    private static double computeD(double alpha, double z) {
        final double alogz = Math.log(z);
        final double eps = 1 - alpha;
        final double meps1 = 1 - eps;
        return SpecialMath.d2(eps * alogz / meps1) * (alogz / meps1);
    }

    /**
     * Assumption test:
     * Test the sin(alpha * phi + atan(-zeta)) term can be zero.
     * This applies to the Weron formula.
     */
    @Test
    public void testSinAlphaPhiMinusAtanZeta() {
        // Note sin(alpha * phi + atan(-zeta)) is zero when:
        // alpha * phi = -atan(-zeta)
        // tan(-alpha * phi) = -zeta
        //                   = beta * tan(alpha * pi / 2)
        // beta = tan(-alpha * phi) / tan(alpha * pi / 2)
        // Find a case where the result is zero...
        for (double alpha : new double[] {0.25, 0.125}) {
            for (double phi : new double[] {PI_4, PI_4 / 2}) {
                double beta = Math.tan(-alpha * phi) / Math.tan(alpha * PI_2);
                double zeta = -beta * Math.tan(alpha * PI_2);
                double atanZeta = Math.atan(-zeta);
                Assert.assertEquals(0.0, alpha * phi + atanZeta, 0.0);
            }
        }
    }

    /**
     * Assumption test:
     * Test the cos(phi - alpha * (phi + xi)) term is positive.
     * This applies to the Weron formula.
     */
    @Test
    public void testCosPhiMinusAlphaPhiXi() {
        // This is the extreme of cos(x) that should be used
        final double cosPi2 = Math.cos(PI_2);
        // The function is symmetric
        Assert.assertEquals(cosPi2, Math.cos(-PI_2), 0.0);
        // As pi is an approximation then the cos value is not exactly 0
        Assert.assertTrue(cosPi2 > 0);

        final UniformRandomProvider rng = RandomSource.XO_SHI_RO_256_SS.create();

        // The term is mirrored around 1 so use extremes between 1 and 0
        final double[] alphas = {1, nextDown(1), 0.99, 0.5, 0.1, 0.05, 0.01, DU};
        // Longs to generate extremes for the angle phi. This is mirrored
        // by negation is the assert method so use values to create phi in [0, pi/2).
        final long[] xs = {0, 1 << 10, Long.MIN_VALUE >>> 1, Long.MAX_VALUE};
        for (final double alpha : alphas) {
            for (final long x : xs) {
                assertCosPhiMinusAlphaPhiXi(alpha, x);
                assertCosPhiMinusAlphaPhiXi(2 - alpha, x);
            }
            for (int j = 0; j < 1000; j++) {
                final long x = rng.nextLong();
                assertCosPhiMinusAlphaPhiXi(alpha, x);
                assertCosPhiMinusAlphaPhiXi(2 - alpha, x);
            }
        }
        // Random alpha
        for (int i = 0; i < 1000; i++) {
            final double alpha = rng.nextDouble();
            for (final long x : xs) {
                assertCosPhiMinusAlphaPhiXi(alpha, x);
                assertCosPhiMinusAlphaPhiXi(2 - alpha, x);
            }
            for (int j = 0; j < 1000; j++) {
                final long x = rng.nextLong();
                assertCosPhiMinusAlphaPhiXi(alpha, x);
                assertCosPhiMinusAlphaPhiXi(2 - alpha, x);
            }
        }

        // Enumerate alpha
        for (int i = 0; i <= 1023; i++)  {
            final double alpha = (double) i / 1023;
            for (final long x : xs) {
                assertCosPhiMinusAlphaPhiXi(alpha, x);
                assertCosPhiMinusAlphaPhiXi(2 - alpha, x);
            }
        }
    }

    /**
     * Assert the cos(phi - alpha * (phi + xi)) term is positive.
     * This asserts the term (phi - alpha * (phi + xi)) is in the interval (-pi/2, pi/2) when
     * beta is the extreme of +/-1.
     *
     * @param alpha alpha
     * @param x the long used to create the uniform deviate
     */
    private static void assertCosPhiMinusAlphaPhiXi(double alpha, long x) {
        // Update for symmetry around alpha = 1
        final double eps = 1 - alpha;
        final double meps1 = 1 - eps;

        // zeta = -beta * tan(alpha * pi / 2)
        // xi = atan(-zeta) / alpha
        // Compute phi - alpha * (phi + xi).
        // This value must be in (-pi/2, pi/2).
        // The term expands to:
        // phi - alpha * (phi + xi)
        // = phi - alpha * phi - atan(-zeta)
        // = (1-alpha) * phi - atan(-zeta)
        // When beta = +/-1,
        // atanZeta = +/-alpha * pi/2  if alpha < 1
        // atanZeta = +/-(2-alpha) * pi/2  if alpha > 1
        // alpha=1 => always +/-pi/2
        // alpha=0,2 => always +/-phi
        // Values in between use the addition:
        // (1-alpha) * phi +/- alpha * pi/2
        // Since (1-alpha) is exact and alpha = 1 - (1-alpha) the addition
        // cannot exceed pi/2.

        // Avoid the round trip using tan and arctan when beta is +/- 1
        // zeta = -beta * Math.tan(alpha * pi / 2);
        // atan(-zeta) = alpha * pi / 2

        final double alphaPi2;
        if (meps1 > 1) {
            // Avoid calling tan outside the domain limit [-pi/2, pi/2].
            alphaPi2 = -(2 - meps1) * PI_2;
        } else {
            alphaPi2 = meps1 * PI_2;
        }

        // Compute eps * phi +/- alpha * pi / 2
        // Test it is in the interval (-pi/2, pi/2)
        double phi = getU(x) * 2;
        double value = eps * phi + alphaPi2;
        Assert.assertTrue(value <= PI_2);
        Assert.assertTrue(value >= -PI_2);
        value = eps * phi - alphaPi2;
        Assert.assertTrue(value <= PI_2);
        Assert.assertTrue(value >= -PI_2);

        // Mirror the deviate
        phi = -phi;
        value = eps * phi + alphaPi2;
        Assert.assertTrue(value <= PI_2);
        Assert.assertTrue(value >= -PI_2);
        value = eps * phi - alphaPi2;
        Assert.assertTrue(value <= PI_2);
        Assert.assertTrue(value >= -PI_2);
    }
    /**
     * Assumption test:
     * Test the sin(alpha * phi) term is only zero when phi is zero.
     * This applies to the Weron formula when {@code beta = 0}.
     */
    @Test
    public void testSinAlphaPhi() {
        // Smallest non-zero phi.
        // getU creates in the domain (-pi/4, pi/4) so double the angle.
        for (final double phi : new double[] {getU(-1) * 2, getU(1 << 10) * 2}) {
            final double x = Math.sin(SMALLEST_ALPHA * phi);
            Assert.assertNotEquals(0.0, x, 0.0);
            // Value is actually:
            Assert.assertEquals(1.9361559566769725E-32, Math.abs(x), 0.0);
        }
    }

    /**
     * Assumption test:
     * Test functions to compute {@code (exp(x) - 1) / x}. This tests the use of
     * {@link Math#expm1(double)} and {@link Math#exp(double)} to determine if the switch
     * point to the high precision version is monotonic.
     */
    @Test
    public void testExpM1() {
        // Test monotonic at the switch point
        Assert.assertEquals(d2(0.5), d2b(0.5), 0.0);
        // When positive x -> 0 the value smaller bigger.
        Assert.assertTrue(d2(nextDown(0.5)) <= d2b(0.5));
        Assert.assertEquals(d2(-0.5), d2b(-0.5), 0.0);
        // When negative x -> 0 the value gets bigger.
        Assert.assertTrue(d2(-nextDown(0.5)) >= d2b(-0.5));
        // Potentially the next power of 2 could be used based on ULP errors but
        // the switch is not monotonic.
        Assert.assertFalse(d2(nextDown(0.25)) <= d2b(0.25));
    }

    /**
     * This is not a test.
     *
     * <p>This outputs a report of the mean ULP difference between
     * using {@link Math#expm1(double)} and {@link Math#exp(double)} to evaluate
     * {@code (exp(x) - 1) / x}. This helps choose the switch point to avoid the computationally
     * expensive expm1 function.
     */
    //@Test
    public void expm1ULPReport() {
        // Create random doubles with a given exponent. Compute the mean and max ULP difference.
        final UniformRandomProvider rng = RandomSource.XO_RO_SHI_RO_128_PP.create();
        // For a quicker report set to <= 2^20.
        final int size = 1 << 30;
        // Create random doubles using random bits in the 52-bit mantissa.
        final long mask = (1L << 52) - 1;
        // Note:
        // The point at which there *should* be no difference between the two is when
        // exp(x) - 1 == exp(x). This will occur at exp(x)=2^54, x = ln(2^54) = 37.43.
        Assert.assertEquals(((double) (1L << 54)) - 1, (double) (1L << 54), 0.0);
        // However since expm1 and exp are only within 1 ULP of the exact result differences
        // still occur above this threshold.
        // 2^6 = 64; 2^-4 = 0.0625
        for (int signedExp = 6; signedExp >= -4; signedExp--) {
            // The exponent must be unsigned so + 1023 to the signed exponent
            final long exp = (signedExp + 1023L) << 52;
            // Test we are creating the correct numbers
            Assert.assertEquals(signedExp, Math.getExponent(Double.longBitsToDouble(exp)));
            Assert.assertEquals(signedExp, Math.getExponent(Double.longBitsToDouble((-1 & mask) | exp)));
            // Get the average and max ulp
            long sum1 = 0;
            long sum2 = 0;
            long max1 = 0;
            long max2 = 0;
            for (int i = size; i-- > 0;) {
                final long bits = rng.nextLong() & mask;
                final double x = Double.longBitsToDouble(bits | exp);
                final double x1 = d2(x);
                final double x2 = d2b(x);
                final double x1b = d2(-x);
                final double x2b = d2b(-x);
                final long ulp1 = Math.abs(Double.doubleToRawLongBits(x1) - Double.doubleToRawLongBits(x2));
                final long ulp2 = Math.abs(Double.doubleToRawLongBits(x1b) - Double.doubleToRawLongBits(x2b));
                sum1 += ulp1;
                sum2 += ulp2;
                if (max1 < ulp1) {
                    max1 = ulp1;
                }
                if (max2 < ulp2) {
                    max2 = ulp2;
                }
            }
            // CHECKSTYLE: stop Regexp
            System.out.printf("%-6s   %2d   %-24s (%d)   %-24s (%d)%n",
                Double.longBitsToDouble(exp), signedExp,
                (double) sum1 / size, max1, (double) sum2 / size, max2);
            // CHECKSTYLE: resume Regexp
        }
    }

    /**
     * Evaluate {@code (exp(x) - 1) / x} using {@link Math#expm1(double)}.
     * For {@code x} in the range {@code [-inf, inf]} returns
     * a result in {@code [0, inf]}.
     *
     * <ul>
     * <li>For {@code x=-inf} this returns {@code 0}.
     * <li>For {@code x=0} this returns {@code 1}.
     * <li>For {@code x=inf} this returns {@code inf}.
     * </ul>
     *
     * <p> This corrects {@code 0 / 0} and {@code inf / inf} division from
     * {@code NaN} to either {@code 1} or the upper bound respectively.
     *
     * @param x value to evaluate
     * @return {@code (exp(x) - 1) / x}.
     */
    private static double d2(double x) {
        // Here we use a conditional to detect both edge cases, which are then corrected.
        final double d2 = Math.expm1(x) / x;
        if (Double.isNaN(d2)) {
            // Correct edge cases.
            if (x == 0) {
                return 1.0;
            }
            // x must have been +infinite or NaN
            return x;
        }
        return d2;
    }

    /**
     * Evaluate {@code (exp(x) - 1) / x} using {@link Math#exp(double)}.
     * For {@code x} in the range {@code [-inf, inf]} returns
     * a result in {@code [0, inf]}.
     *
     * <ul>
     * <li>For {@code x=-inf} this returns {@code 0}.
     * <li>For {@code x=0} this returns {@code 1}.
     * <li>For {@code x=inf} this returns {@code inf}.
     * </ul>
     *
     * <p> This corrects {@code 0 / 0} and {@code inf / inf} division from
     * {@code NaN} to either {@code 1} or the upper bound respectively.
     *
     * @param x value to evaluate
     * @return {@code (exp(x) - 1) / x}.
     */
    private static double d2b(double x) {
        // Here we use a conditional to detect both edge cases, which are then corrected.
        final double d2 = (Math.exp(x) - 1) / x;
        if (Double.isNaN(d2)) {
            // Correct edge cases.
            if (x == 0) {
                return 1.0;
            }
            // x must have been +infinite or NaN
            return x;
        }
        return d2;
    }

    /**
     * Test the special d2 function returns {@code (exp(x) - 1) / x}.
     * The limits of the function are {@code [0, inf]} and it should return 1 when x=0.
     */
    @Test
    public void testD2() {
        for (final double x : new double[] {Double.MAX_VALUE, Math.log(Double.MAX_VALUE), 10, 5, 1, 0.5, 0.1, 0.05, 0.01}) {
            Assert.assertEquals(Math.expm1(x) / x, SpecialMath.d2(x), 1e-16);
            Assert.assertEquals(Math.expm1(-x) / -x, SpecialMath.d2(-x), 1e-16);
        }

        // Negative infinity computes without correction
        Assert.assertEquals(0.0, Math.expm1(Double.NEGATIVE_INFINITY) / Double.NEGATIVE_INFINITY, 0.0);
        Assert.assertEquals(0.0, SpecialMath.d2(Double.NEGATIVE_INFINITY), 0.0);

        // NaN is returned (i.e. no correction)
        Assert.assertEquals(Double.NaN, SpecialMath.d2(Double.NaN), 0.0);

        // Edge cases for z=0 or z==inf require correction
        Assert.assertEquals(Double.NaN, Math.expm1(0) / 0.0, 0.0);
        Assert.assertEquals(Double.NaN, Math.expm1(Double.POSITIVE_INFINITY) / Double.POSITIVE_INFINITY, 0.0);
        // Corrected in the special function
        Assert.assertEquals(1.0, SpecialMath.d2(0.0), 0.0);
        Assert.assertEquals(Double.POSITIVE_INFINITY, SpecialMath.d2(Double.POSITIVE_INFINITY), 0.0);
    }

    /**
     * Test the tan2 function returns {@code tan(x) / x}.
     */
    @Test
    public void testTan2() {
        // Test the value of tan(x) when the angle is generated in the open interval (-pi/4, pi/4)
        for (final long x : new long[] {Long.MIN_VALUE + (1 << 10), Long.MAX_VALUE}) {
            final double phiby2 = getU(x);
            Assert.assertEquals(PI_4 - DU * PI_4, Math.abs(phiby2), 0.0);
            final double a = phiby2 * SpecialMath.tan2(phiby2);
            // Check this is not 1
            Assert.assertNotEquals(1, Math.abs(a), 0.0);
            Assert.assertTrue(Math.abs(a) < 1.0);
        }

        // At pi/4 the function reverts to Math.tan(x) / x. Test through the transition.
        final double pi = Math.PI;
        for (final double x : new double[] {pi, pi / 2, pi / 3.99, pi / 4, pi / 4.01, pi / 8, pi / 16}) {
            final double y = Math.tan(x) / x;
            Assert.assertEquals(y, SpecialMath.tan2(x), Math.ulp(y));
        }

        // Test this closely matches the JDK tan function.
        // Test uniformly between 0 and pi / 4.
        // Count the errors with the ULP difference.
        // Get max ULP and mean ULP. Do this for both tan(x) and tan(x)/x functions.
        final UniformRandomProvider rng = RandomSource.XO_RO_SHI_RO_128_PP.create(0x1647816481684L);
        int count = 0;
        long ulp = 0;
        long max = 0;
        long ulp2 = 0;
        long max2 = 0;
        for (int i = 0; i < 1000; i++) {
            final double x = rng.nextDouble() * PI_4;
            count++;
            final double tanx = Math.tan(x);
            final double tan2x = SpecialMath.tan2(x);
            // Test tan(x)
            double y = x * tan2x;
            if (y != tanx) {
                final long u = Math.abs(Double.doubleToRawLongBits(tanx) - Double.doubleToRawLongBits(y));
                if (max < u) {
                    max = u;
                }
                ulp += u;
                // Within 4 ulp. Note tan(x) is within 1 ulp of the result. So this
                // is max 5 ulp from the result.
                Assert.assertEquals(tanx, y, 4 * Math.ulp(tanx));
            }
            // Test tan(x) / x
            y = tanx / x;
            if (y != tan2x) {
                final long u = Math.abs(Double.doubleToRawLongBits(tan2x) - Double.doubleToRawLongBits(y));
                if (max2 < u) {
                    max2 = u;
                }
                ulp2 += u;
                // Within 3 ulp.
                Assert.assertEquals(y, tan2x, 3 * Math.ulp(y));
            }
        }
        // Mean (max) ULP is very low
        // 2^30 random samples in [0, pi / 4)
        //          tan(x)                    tan(x) / x
        // tan4283  93436.25534446817         201185 : 68313.16171793547         128079
        // tan4288c     0.5905972588807344         4 :     0.4047176940366626         3
        Assert.assertTrue("Mean ULP to tan(x) is too high", (double) ulp / count < 0.6);
        Assert.assertTrue("Mean ULP to tan(x) / x is too high", (double) ulp2 / count < 0.45);
        // If the value is under 1 then the sampler will break due to cancellation errors.
        Assert.assertEquals("Must be exact tan(x) / x at x=0", 1.0, SpecialMath.tan2(0.0), 0.0);
        Assert.assertEquals(4 / Math.PI, SpecialMath.tan2(PI_4), Math.ulp(4 / Math.PI));
        Assert.assertEquals(1.0, PI_4 * SpecialMath.tan2(PI_4), Math.ulp(1.0));
        // If this is above 1 then the sampler will break. Test at the switch point pi/4.
        Assert.assertTrue(1.0 >= PI_4 * SpecialMath.tan2(PI_4));
        Assert.assertTrue(1.0 >= PI_4 * SpecialMath.tan2(nextDown(PI_4)));
        // Monotonic function at the transition
        Assert.assertTrue(SpecialMath.tan2(nextUp(PI_4)) >= SpecialMath.tan2(PI_4));
    }

    /**
     * Assumption test:
     * Demonstrate the CMS algorithm matches the Weron formula when {@code alpha != 1}.
     * This shows the two are equivalent; they should match as the formulas are rearrangements.
     */
    @Test
    public void testSamplesWithAlphaNot1() {
        // Use non-extreme parameters. beta and u are negated so use non-redundant values
        final double[] alphas = {0.3, 0.9, 1.1, 1.5};
        final double[] betas = {-1, -0.5, -0.3, 0};
        final double[] ws = {0.1, 1, 3};
        final double[] us = {0.1, 0.25, 0.5, 0.8};

        final double relative = 1e-5;
        final double absolute = 1e-10;
        for (final double alpha : alphas) {
            for (final double beta : betas) {
                for (final double w : ws) {
                    for (final double u : us) {
                        final double x = sampleCMS(alpha, beta, w, u);
                        final double y = sampleWeronAlphaNot1(alpha, beta, w, u);
                        Assert.assertEquals(x, y, Math.max(absolute, Math.abs(x) * relative));
                        // Test symmetry
                        final double z = sampleCMS(alpha, -beta, w, 1 - u);
                        Assert.assertEquals(x, -z, 0);
                    }
                }
            }
        }
    }

    /**
     * Assumption test:
     * Demonstrate the CMS algorithm matches the Weron formula when {@code alpha == 1}.
     * This shows the two are equivalent; they should match as the formulas are rearrangements.
     */
    @Test
    public void testSamplesWithAlpha1() {
        // Use non-extreme parameters. beta and u are negated so use non-redundant values
        final double[] betas = {-1, -0.5, -0.3, 0};
        final double[] ws = {0.1, 1, 3};
        final double[] us = {0.1, 0.25, 0.5, 0.8};

        final double relative = 1e-5;
        final double absolute = 1e-10;
        final double alpha = 1;
        for (final double beta : betas) {
            for (final double w : ws) {
                for (final double u : us) {
                    final double x = sampleCMS(alpha, beta, w, u);
                    final double y = sampleWeronAlpha1(beta, w, u);
                    Assert.assertEquals(x, y, Math.max(absolute, Math.abs(x) * relative));
                    // Test symmetry
                    final double z = sampleCMS(alpha, -beta, w, 1 - u);
                    Assert.assertEquals(x, -z, 0);
                }
            }
        }
    }

    /**
     * Assumption test:
     * Demonstrate the CMS formula is continuous as {@code alpha -> 1}.
     * Demonstrate the Weron formula is not continuous as {@code alpha -> 1}.
     */
    @Test
    public void testConvergenceWithAlphaCloseTo1() {
        final double[] betas = {-1, -0.5, 0, 0.3, 1};
        final double[] ws = {0.1, 1, 10};
        final double[] us = {0.1, 0.25, 0.5, 0.8};
        final int steps = 30;

        // Start with alpha not close to 0. The value 0.0625 is a power of 2 so is scaled
        // exactly by dividing by 2. With 30 steps this ranges from 2^-4 to 2^-34 leaving alpha:
        // 1.0625 -> 1.0000000000582077 or
        // 0.9375 -> 0.9999999999417923.
        for (double deltaStart : new double[] {-0.0625, 0.0625}) {
            // As alpha approaches 1 the value should approach the value when alpha=0.
            // Count the number of times it get further away with a change of alpha.
            int cmsCount = 0;
            int weronCount = 0;

            for (final double beta : betas) {
                for (final double w : ws) {
                    for (final double u : us) {
                        // CMS formulas
                        double x0 = sampleCMS(1, beta, w, u);
                        Assert.assertTrue("Target must be finite", isFinite(x0));

                        // Sample should approach x0 as alpha approaches 1
                        double delta = deltaStart;
                        double dx = Math.abs(x0 - sampleCMS(1 + delta, beta, w, u));
                        for (int i = 0; i < steps; i++) {
                            delta /= 2;
                            final double dx2 = Math.abs(x0 - sampleCMS(1 + delta, beta, w, u));
                            if (dx2 > dx) {
                                cmsCount++;
                            }
                            dx = dx2;
                        }

                        // Weron formulas
                        x0 = sampleWeronAlpha1(beta, w, u);
                        Assert.assertTrue("Target must be finite", isFinite(x0));

                        // Sample should approach x0 as alpha approaches 1
                        delta = deltaStart;
                        dx = Math.abs(x0 - sampleWeronAlphaNot1(1 + delta, beta, w, u));
                        for (int i = 0; i < steps; i++) {
                            delta /= 2;
                            final double dx2 = Math.abs(x0 - sampleWeronAlphaNot1(1 + delta, beta, w, u));
                            if (dx2 > dx) {
                                weronCount++;
                            }
                            dx = dx2;
                        }
                    }
                }
            }

            // The CMS formala monotonically converges
            Assert.assertEquals(0, cmsCount);
            // The weron formula does not monotonically converge
            // (difference to the target can be bigger when alpha moves closer to 1).
            Assert.assertTrue(weronCount > 200);
        }
    }

    /**
     * Test extreme inputs to the CMS algorithm where {@code alpha != 1} and/or
     * {@code beta != 0}. These demonstrate cases where the parameters and the
     * random variates will create non-finite samples. The test checks that the Weron
     * formula can create an appropriate sample for all cases where the CMS formula fails.
     */
    @Test
    public void testExtremeInputsToSample() {
        // Demonstrate instability when w = 0
        Assert.assertEquals(Double.NaN, sampleCMS(1.3, 0.7, 0, 0.25), 0.0);
        Assert.assertTrue(isFinite(sampleCMS(1.3, 0.7, SMALL_W, 0.25)));

        // Demonstrate instability when u -> 0 or 1, and |beta| = 1
        Assert.assertEquals(Double.NaN, sampleCMS(1.1, 1.0, 0.1, 0), 0.0);
        Assert.assertTrue(isFinite(sampleCMS(1.1, 1.0, 0.1, DU)));

        // Demonstrate instability when alpha -> 0

        // Small alpha does not tolerate very small w.
        Assert.assertEquals(Double.NaN, sampleCMS(0.01, 0.7, SMALL_W, 0.5), 0.0);

        // Very small alpha does not tolerate u approaching 0 or 1 (depending on the
        // skew)
        Assert.assertEquals(Double.NaN, sampleCMS(1e-5, 0.7, 1.0, 1e-4), 0.0);
        Assert.assertEquals(Double.NaN, sampleCMS(1e-5, -0.7, 1.0, 1 - 1e-4), 0.0);

        final double[] alphas = {nextDown(2), 1.3, 1.1, nextUp(1), 1, nextDown(1), 0.7, 0.1, 0.05, 0.01, 0x1.0p-16};
        final double[] betas = {1, 0.9, 0.001, 0};
        // Avoid zero for the exponential sample.
        // Test the smallest non-zero sample from the ArhensDieter exponential sampler,
        // and the largest sample.
        final double[] ws = {0, SMALL_W, 0.001, 1, 10, LARGE_W};
        // The algorithm requires a uniform deviate in (0, 1).
        // Use extremes of the 2^53 dyadic rationals in (0, 1) up to the symmetry limit
        // (i.e. 0.5).
        final double[] us = {DU, 2 * DU, 0.0001, 0.5 - DU, 0.5};

        int nan1 = 0;

        for (final double alpha : alphas) {
            for (final double beta : betas) {
                if (alpha == 1 && beta == 0) {
                    // Ignore the Cauchy case
                    continue;
                }
                // Get the support of the distribution. This is not -> +/-infinity
                // when alpha < 1 and beta = +/-1.
                final double[] support = getSupport(alpha, beta);
                final double lower = support[0];
                final double upper = support[1];
                for (final double w : ws) {
                    for (final double u : us) {
                        final double x1 = sampleCMS(alpha, beta, w, u);
                        final double x2 = sampleWeron(alpha, beta, w, u);

                        if (Double.isNaN(x1)) {
                            nan1++;
                        }
                        // The edge-case corrected Weron formula should not fail
                        Assert.assertNotEquals(Double.NaN, x2);

                        // Check symmetry of each formula
                        Assert.assertEquals(x1, -sampleCMS(alpha, -beta, w, 1 - u), 0.0);
                        Assert.assertEquals(x2, -sampleWeron(alpha, -beta, w, 1 - u), 0.0);

                        if (Double.isInfinite(x1) && x1 != x2) {
                            // Check the Weron correction for extreme samples.
                            // The result should be at the correct *finite* support bounds.
                            // Note: This applies when alpha < 1 and beta = +/-1.
                            Assert.assertTrue(lower <= x2 && x2 <= upper);
                        }
                    }
                }
            }
        }

        // The CMS algorithm is expected to fail some cases
        Assert.assertNotEquals(0, nan1);
    }

    /**
     * Create a sample from a stable distribution. This is an implementation of the CMS
     * algorithm to allow exploration of various input values. The algorithm matches that
     * in the {@link CMSStableSampler} with the exception that the uniform variate
     * is provided in {@code (0, 1)}, not{@code (-pi/4, pi/4)}.
     *
     * @param alpha Stability parameter. Must be in the interval {@code (0, 2]}.
     * @param beta Skewness parameter. Must be in the interval {@code [-1, 1]}.
     * @param w Exponential variate
     * @param u Uniform variate
     * @return the sample
     */
    private static double sampleCMS(double alpha, double beta, double w, double u) {
        final double phiby2 = PI_2 * (u - 0.5);
        final double eps = 1 - alpha;
        // Do not use alpha in place of 1 - eps. When alpha < 0.5, 1 - eps == alpha is not
        // always true as the reverse is not exact.
        final double meps1 = 1 - eps;

        // Compute RSTAB prefactor
        final double tau = CMSStableSampler.getTau(alpha, beta);

        // Generic stable distribution that is continuous as alpha -> 1.
        // This is a trigonomic rearrangement of equation 4.1 from Chambers et al (1976)
        // as implemented in the Fortran program RSTAB.
        // Uses the special functions:
        // tan2 = tan(x) / x
        // d2 = (exp(x) - 1) / x
        // Here tan2 is implemented using an high precision approximation.

        // Compute some tangents
        // Limits for |phi/2| < pi/4
        // a in (-1, 1)
        final double a = phiby2 * SpecialMath.tan2(phiby2);
        // bb in [1, 4/pi)
        final double bb = SpecialMath.tan2(eps * phiby2);
        // b in (-1, 1)
        final double b = eps * phiby2 * bb;
        // Compute some necessary subexpressions
        final double da = a * a;
        final double db = b * b;
        // a2 in (0, 1]
        final double a2 = 1 - da;
        // a2p in [1, 2)
        final double a2p = 1 + da;
        // b2 in (0, 1]
        final double b2 = 1 - db;
        // b2p in [1, 2)
        final double b2p = 1 + db;
        // Compute coefficient.
        // Note:
        // Avoid z <= 0 to avoid log(z) as negative infinity or nan.
        // This occurs when |phiby2| -> +/-pi/4 and |beta| -> 1.
        // Problems:
        // numerator=0 => z=0
        // denominator=0 => z=inf
        // numerator=denominator=0 => z=nan
        // 1. w or a2 are zero so the denominator is zero. w can be a rare exponential sample.
        // a2 -> zero if the uniform deviate is 0 or 1 and angle is |pi/4|.
        // If w -> 0 and |u-0.5| -> 0.5 then the product of w * a2 can be zero.
        // 2. |eps|=1, phiby2=|pi/4| => bb=4/pi, b=1, b2=0; if tau=0 then the numerator is zero.
        // This requires beta=0.

        final double z = a2p * (b2 + 2 * phiby2 * bb * tau) / (w * a2 * b2p);
        // Compute the exponential-type expression
        final double alogz = Math.log(z);
        final double d = SpecialMath.d2(eps * alogz / meps1) * (alogz / meps1);

        // Compute stable
        return (1 + eps * d) *
                (2 * ((a - b) * (1 + a * b) - phiby2 * tau * bb * (b * a2 - 2 * a))) /
                (a2 * b2p) + tau * d;
    }

    /**
     * Create a sample from a stable distribution. This is an implementation of the Weron formula.
     * The formula has been modified when alpha != 1 to return the 0-parameterization result and
     * correct extreme samples to +/-infinity.
     *
     * @param alpha Stability parameter. Must be in the interval {@code (0, 2]}.
     * @param beta Skewness parameter. Must be in the interval {@code [-1, 1]}.
     * @param w Exponential variate
     * @param u Uniform variate
     * @return the sample
     * @see <a href="https://doi.org/10.1016%2F0167-7152%2895%2900113-1">Weron, R (1996).
     * "On the Chambers-Mallows-Stuck method for simulating skewed stable random variables".
     * Statistics &amp; Probability Letters. 28 (2): 165–171.</a>
     */
    private static double sampleWeron(double alpha, double beta, double w, double u) {
        return alpha == 1 ? sampleWeronAlpha1(beta, w, u) : sampleWeronAlphaNot1(alpha, beta, w, u);
    }

    /**
     * Create a sample from a stable distribution. This is an implementation of the
     * Weron {@code alpha != 1} formula. The formula has been modified to return the
     * 0-parameterization result and correct extreme samples to +/-infinity. The
     * algorithm matches that in the {@link WeronStableSampler} with the exception that
     * the uniform variate is provided in {@code (0, 1)}, not{@code (-pi/2, pi/2)}.
     *
     * <p>Due to the increasingly large shift (up to 1e16) as {@code alpha -> 1}
     * that is used to move the result to the 0-parameterization the samples around
     * the mode of the distribution have large cancellation and a reduced number of
     * bits in the sample value.
     *
     * @param alpha Stability parameter. Must be in the interval {@code (0, 2]}.
     * @param beta Skewness parameter. Must be in the interval {@code [-1, 1]}.
     * @param w Exponential variate
     * @param u Uniform variate
     * @return the sample
     * @see <a href="https://doi.org/10.1016%2F0167-7152%2895%2900113-1">Weron, R
     * (1996).
     * "On the Chambers-Mallows-Stuck method for simulating skewed stable random variables".
     * Statistics &amp; Probability Letters. 28 (2): 165–171.</a>
     */
    private static double sampleWeronAlphaNot1(double alpha, double beta, double w, double u) {
        // Update for symmetry around alpha = 1
        final double eps = 1 - alpha;
        final double meps1 = 1 - eps;

        double zeta;
        if (meps1 > 1) {
            zeta = beta * Math.tan((2 - meps1) * PI_2);
        } else {
            zeta = -beta * Math.tan(meps1 * PI_2);
        }

        final double scale = Math.pow(1 + zeta * zeta, 0.5 / meps1);
        final double invAlpha = 1.0 / meps1;
        final double invAlphaM1 = invAlpha - 1;

        final double phi = Math.PI * (u - 0.5);

        // Generic stable distribution.

        // Note: Adding back zeta creates the 0-parameterization defined in Nolan (1998):
        // X ~ S0_alpha(s,beta,u0) with s=1, u0=0 for a standard random variable.
        // As alpha -> 1 the translation zeta to create the stable deviate
        // in the 0-parameterization is increasingly large as tan(pi/2) -> infinity.
        // The max translation is approximately 1e16.
        // Without this translation the stable deviate is in the 1-parameterization
        // and the function is not continuous with respect to alpha.
        // Due to the large zeta when alpha -> 1 the number of bits of the output variable
        // are very low due to cancellation.

        // As alpha -> 0 or 2 then zeta -> 0 and cancellation is not relevant.
        // The formula can be modified for infinite terms to compute a result for extreme
        // deviates u and w when the CMS formula fails.

        // Note the following term is subject to floating point error:
        // final double xi = Math.atan(-zeta) / alpha;
        // final double alphaPhiXi = alpha * (phi + xi);
        // This is required: cos(phi - alphaPhiXi) > 0 => phi - alphaPhiXi in (-pi/2, pi/2).
        // Thus we compute atan(-zeta) and use it to compute two terms:
        // [1] alpha * (phi + xi) = alpha * (phi + atan(-zeta) / alpha) = alpha * phi + atan(-zeta)
        // [2] phi - alpha * (phi + xi) = phi - alpha * phi - atan(-zeta) = (1-alpha) * phi - atan(-zeta)
        final double atanZeta = Math.atan(-zeta);

        // Compute terms
        // Either term can be infinite or 0. Certain parameters compute 0 * inf.
        // t1=inf occurs alpha -> 0.
        // t1=0 occurs when beta = tan(-alpha * phi) / tan(alpha * pi / 2).
        // t2=inf occurs when w -> 0 and alpha -> 0.
        // t2=0 occurs when alpha -> 0 and phi -> pi/2.
        // Detect zeros and return as zeta.

        // Note sin(alpha * phi + atanZeta) is zero when:
        // alpha * phi = -atan(-zeta)
        // tan(-alpha * phi) = -zeta
        //                   = beta * tan(alpha * pi / 2)
        // Since |phi| < pi/2 this requires beta to have an opposite sign to phi
        // and a magnitude < 1. This is possible and in this case avoid a possible
        // 0 / 0 by setting the result as if term t1=0 and the result is zeta.
        double t1 = Math.sin(meps1 * phi + atanZeta);
        if (t1 == 0) {
            return zeta;
        }
        // Since cos(phi) is in (0, 1] this term will not create a
        // large magnitude to create t1 = 0.
        t1 /= Math.pow(Math.cos(phi), invAlpha);

        // Iff Math.cos(eps * phi - atanZeta) is zero then 0 / 0 can occur if w=0.
        // Iff Math.cos(eps * phi - atanZeta) is below zero then NaN will occur
        // in the power function. These cases are avoided by u=(0,1) and direct
        // use of arctan(-zeta).
        final double t2 = Math.pow(Math.cos(eps * phi - atanZeta) / w, invAlphaM1);
        if (t2 == 0) {
            return zeta;
        }

        return t1 * t2 * scale + zeta;
    }

    /**
     * Create a sample from a stable distribution. This is an implementation of the Weron
     * {@code alpha == 1} formula. The algorithm matches that
     * in the {@link Alpha1StableSampler} with the exception that
     * the uniform variate is provided in {@code (0, 1)}, not{@code (-pi/2, pi/2)}.
     *
     * @param alpha Stability parameter. Must be in the interval {@code (0, 2]}.
     * @param beta Skewness parameter. Must be in the interval {@code [-1, 1]}.
     * @param w Exponential variate
     * @param u Uniform variate
     * @return the sample
     * @see <a href="https://doi.org/10.1016%2F0167-7152%2895%2900113-1">Weron, R (1996).
     * "On the Chambers-Mallows-Stuck method for simulating skewed stable random variables".
     * Statistics &amp; Probability Letters. 28 (2): 165–171.</a>
     */
    private static double sampleWeronAlpha1(double beta, double w, double u) {
        // phi in (-pi/2, pi/2)
        final double phi = Math.PI * (u - 0.5);

        // Generic stable distribution with alpha = 1
        final double betaPhi = PI_2 + beta * phi;
        return (betaPhi * Math.tan(phi) -
               beta * Math.log(PI_2 * w * Math.cos(phi) / betaPhi)) / PI_2;
    }

    /*******************************/
    /* Tests for the StableSampler */
    /*******************************/

    /**
     * Test the general CMS sampler when the random generator outputs create
     * deviates that cause the value {@code z} to be negative.
     */
    @Test
    public void testSamplesWithZBelow0() {
        // Call the CMS algorithm with u->1; phi/2 -> pi/4.
        // The value with all bits set generates phi/2 -> pi/4.
        // Add a long to create a big value for w of 5.
        // The parameters create cancellation in the numerator of z to create a negative z.
        final long[] longs = {Long.MAX_VALUE, 6092639261718834176L};

        final double phiby2 = PI_4 - PI_4 * DU;
        final double w = 5.0;
        assertUWSequence(new double[] {
            phiby2, w,
        }, longs);

        // The alpha parameter has been identified via a search with beta=-1.
        // See testZIsNotAlwaysAboveZero()
        final double alpha = 1.291015625;
        final double beta = -1;
        Assert.assertTrue(0.0 > computeNumerator(alpha, beta, Long.MAX_VALUE));

        // z will be negative. Repeat computation assumed to be performed by the sampler.
        // This ensures the test should be updated if the sampler implementation changes.
        final double eps = 1 - alpha;
        final double tau = CMSStableSampler.getTau(alpha, beta);
        final double a = phiby2 * SpecialMath.tan2(phiby2);
        final double bb = SpecialMath.tan2(eps * phiby2);
        final double b = eps * phiby2 * bb;
        final double da = a * a;
        final double db = b * b;
        final double a2 = 1 - da;
        final double a2p = 1 + da;
        final double b2 = 1 - db;
        final double b2p = 1 + db;
        final double z = a2p * (b2 + 2 * phiby2 * bb * tau) / (w * a2 * b2p);
        Assert.assertTrue(0.0 > z);

        final StableSampler sampler = StableSampler.of(createRngWithSequence(longs), alpha, beta);
        // It should not be NaN or infinite
        Assert.assertTrue("Sampler did not recover", isFinite(sampler.sample()));
    }

    /**
     * Test the general CMS sampler when the random generator outputs create
     * deviates that cause the value {@code z} to be infinite.
     */
    @Test
    public void testSamplesWithZInfinite() {
        // Call the CMS algorithm with w=0 (and phi/2 is not extreme).
        final long[] longs = {Long.MIN_VALUE >>> 1, 0};

        assertUWSequence(new double[] {
            PI_4 / 2, 0,
        }, longs);

        for (final double alpha : new double[] {0.789, 1, 1.23}) {
            // Test all directions
            for (final double beta : new double[] {-0.56, 0, 0.56}) {
                // Ignore Cauchy case which does not use the exponential deviate
                if (alpha == 1 && beta == 0) {
                    continue;
                }
                final StableSampler sampler = StableSampler.of(createRngWithSequence(longs), alpha, beta);
                final double x = sampler.sample();
                // It should not be NaN
                Assert.assertFalse("Sampler did not recover", Double.isNaN(x));
                if (beta != 0) {
                    // The sample is extreme so should be at a limit of the support
                    if (alpha < 0) {
                        // Effectively +/- infinity
                        Assert.assertEquals(Math.copySign(Double.POSITIVE_INFINITY, beta), x, 0.0);
                    } else if (alpha > 1) {
                        // At the distribution mean
                        final double[] support = getSupport(alpha, beta);
                        final double mu = support[2];
                        Assert.assertEquals(mu, x, 0.0);
                    }
                }
            }
        }
    }

    /**
     * Test the CMS sampler when the random generator outputs create
     * deviates that cause the value {@code d} to be infinite.
     */
    @Test
    public void testSamplesWithDInfinite() {
        // beta != 0 but with low skew to allow the direction switch in
        // phi/2 to create opposite directions.
        testSamplesWithDInfinite(0.01);
        testSamplesWithDInfinite(-0.01);
    }

    /**
     * Test the {@code beta=0} CMS sampler when the random generator outputs create
     * deviates that cause the value {@code d} to be infinite.
     */
    @Test
    public void testBeta0SamplesWithDInfinite() {
        testSamplesWithDInfinite(0.0);
    }

    /**
     * Test the CMS sampler when the random generator outputs create deviates that
     * cause the value {@code d} to be infinite. This applies to the general sampler
     * or the sampler with {@code beta=0}.
     *
     * @param beta beta (should be close or equal to zero to allow direction changes due to the
     * angle phi/2 to be detected)
     */
    private static void testSamplesWithDInfinite(double beta) {
        // Set-up the random deviate u to be close to -pi/4 (low), pi/4 (high) and 0.5.
        // The extreme values for u create terms during error correction that are infinite.
        final long xuLo = Long.MIN_VALUE + (1024 << 10);
        final long xuHi = Long.MAX_VALUE - (1023 << 10);
        // Call sampler with smallest possible w that is not 0. This creates a finite z
        // but an infinite d due to the use of alpha -> 0.
        final long x = 1L;
        final long[] longs = {xuLo, x, xuHi, x, 0, x};

        assertUWSequence(new double[] {
            -PI_4 + 1024 * DU * PI_4, SMALL_W,
            PI_4 - 1024 * DU * PI_4, SMALL_W,
            0.0, SMALL_W
        }, longs);

        // alpha must be small to create infinite d and beta with low skew
        // to allow the direction switch in phi/2 to create opposite directions.
        // If the skew is too large then the skew dominates the direction.
        // When u=0.5 then f=0 and a standard sum of (1 + eps * d) * f + tau * d
        // with d=inf would cause inf * 0 = NaN.
        final double alpha = 0.03;
        final StableSampler sampler = StableSampler.of(createRngWithSequence(longs), alpha, beta);
        final double x1 = sampler.sample();
        final double x2 = sampler.sample();
        final double x3 = sampler.sample();
        // Expect the limit of the support (the direction is controlled by extreme phi)
        final double max = Double.POSITIVE_INFINITY;
        Assert.assertEquals(-max, x1, 0.0);
        Assert.assertEquals(max, x2, 0.0);
        // Expect the sampler to avoid inf * 0
        Assert.assertNotEquals(Double.NaN, x3, 0.0);
        // When f=0 the sample should be in the middle (beta=0) or skewed in the direction of beta
        if (beta == 0) {
            // In the middle
            Assert.assertEquals(0.0, x3, 0.0);
        } else {
            // At the support limit
            Assert.assertEquals(Math.copySign(max, beta), x3, 0.0);
        }
    }

    /**
     * Test the {@code alpha=1} CMS sampler when the random generator outputs create
     * deviates that cause the value {@code phi/2} to be at the extreme limits.
     */
    @Test
    public void testAlpha1SamplesWithExtremePhi() {
        // The numerator is:
        // 1 + 2 * phiby2 * tau
        // tau = beta / pi/2 when alpha=1
        //     = +/-2 / pi when alpha=1, beta = +/-1
        // This should not create zero if phi/2 is not pi/4.
        // Test the limits of phi/2 to check samples are finite.

        // Add a long to create an ordinary value for w of 1.0.
        // u -> -pi/4
        final long[] longs1 = {Long.MIN_VALUE + (1 << 10), 1446480648965498882L};
        assertUWSequence(new double[] {
            -PI_4 + PI_4 * DU, 1.0,
        }, longs1);
        final StableSampler sampler1 = StableSampler.of(createRngWithSequence(longs1), 1.0, 1.0);
        final double x1 = sampler1.sample();
        Assert.assertTrue("Sampler did not recover", isFinite(x1));

        // u -> pi/4
        final long[] longs2 = {Long.MAX_VALUE, 1446480648965498882L};
        assertUWSequence(new double[] {
            PI_4 - PI_4 * DU, 1.0,
        }, longs2);
        final StableSampler sampler2 = StableSampler.of(createRngWithSequence(longs2), 1.0, -1.0);
        final double x2 = sampler2.sample();
        Assert.assertTrue("Sampler did not recover", isFinite(x2));

        // Sample should be a reflection
        Assert.assertEquals(x1, -x2, 0.0);
    }

    /**
     * Test the support of the distribution when {@code gamma = 1} and
     * {@code delta = 0}. A non-infinite support applies when {@code alpha < 0} and
     * {@code |beta| = 1}.
     */
    @Test
    public void testSupport() {
        testSupport(1.0, 0.0);
    }

    /**
     * Test the support of the distribution when {@code gamma != 1} and
     * {@code delta != 0}. A non-infinite support applies when {@code alpha < 0} and
     * {@code |beta| = 1}.
     */
    @Test
    public void testSupportWithTransformation() {
        // This tests extreme values which should not create NaN results
        for (final double gamma : new double[] {0.78, 1.23, Double.MAX_VALUE, Double.MIN_VALUE}) {
            for (final double delta : new double[] {0.43, 12.34, Double.MAX_VALUE}) {
                testSupport(gamma, delta);
                testSupport(gamma, -delta);
            }
        }
    }

    /**
     * Test the support of the distribution. This applies when {@code alpha < 0} and
     * {@code |beta| = 1}.
     *
     * @param alpha Stability parameter. Must be in range {@code (0, 2]}.
     * @param beta Skewness parameter. Must be in range {@code [-1, 1]}.
     * @param gamma Scale parameter. Must be strictly positive and finite.
     * @param delta Location parameter. Must be finite.
     */
    private static void testSupport(double gamma, double delta) {
        // When alpha is small (<=0.1) the computation becomes limited by floating-point precision.
        final double[] alphas = {2.0, 1.5, 1.0, nextDown(1), 0.99, 0.75, 0.5, 0.25, 0.1, 0.01};
        for (final double alpha : alphas) {
            testSupport(alpha, 1, gamma, delta);
            testSupport(alpha, -1, gamma, delta);
        }
    }
    /**
     * Test the support of the distribution. This applies when {@code alpha < 0} and
     * {@code |beta| = 1}.
     *
     * @param alpha Stability parameter. Must be in range {@code (0, 2]}.
     * @param beta Skewness parameter. Must be in range {@code [-1, 1]}.
     * @param gamma Scale parameter. Must be strictly positive and finite.
     * @param delta Location parameter. Must be finite.
     */
    private static void testSupport(double alpha, double beta, double gamma, double delta) {
        // This is the inclusive bounds (no infinite values)
        final double[] support = getSupport(alpha, beta);
        // Do not scale the max value. It acts as an effective infinity.
        double lower;
        if (support[0] == -Double.MAX_VALUE) {
            lower = Double.NEGATIVE_INFINITY;
        } else {
            lower = support[0] * gamma + delta;
        }
        double upper;
        if (support[1] == Double.MAX_VALUE) {
            upper = Double.POSITIVE_INFINITY;
        } else {
            upper = support[1] * gamma + delta;
        }
        // Create an RNG that will generate extreme values:
        // Here we use 4 recursions into the tail of the exponential. The large exponential
        // deviate is approximately 30.3.
        final long[] longs = new long[] {
            // Note: Add a long of Long.MIN_VALUE to test the sampler ignores this value.
            // Hits edge case for generation of phi/4 in (-pi/4, pi/4)
            Long.MIN_VALUE,

            // phi/2 -> -pi/4, w=0
            Long.MIN_VALUE + (1 << 10), 0,
            // phi/2 -> -pi/4, w=large
            Long.MIN_VALUE + (1 << 10), -1, -1, -1, -1, -1, -1, -1, -1, 0,
            // phi/2 -> pi/4, w=0
            Long.MAX_VALUE, 0,
            // phi/2 -> pi/4, w=large
            Long.MAX_VALUE, -1, -1, -1, -1, -1, -1, -1, -1, 0,
            // phi/2=0, w=0
            0, 0,
            // phi/2=0, w=inf
            0, -1, -1, -1, -1, -1, -1, -1, -1, 0,

            // Add non extreme exponential deviate to test only extreme u
            // phi/2 -> -pi/4, w=1
            Long.MIN_VALUE + (1 << 10), 1446480648965498882L,
            // phi/2 -> pi/4, w=1
            Long.MAX_VALUE, 1446480648965498882L,
            // phi/2=0, w=1
            0, 1446480648965498882L,

            // Add non extreme uniform deviate to test only extreme w
            // phi/2=pi/5, w=0
            Long.MIN_VALUE >> 1, 0,
            // phi/2=pi/5, w=large
            Long.MIN_VALUE >> 1, -1, -1, -1, -1, -1, -1, -1, -1, 0,
            // phi/2=pi/5, w=0
            Long.MIN_VALUE >>> 1, 0,
            // phi/2=pi/5, w=large
            Long.MIN_VALUE >>> 1, -1, -1, -1, -1, -1, -1, -1, -1, 0,
        };

        // Validate series
        final double phiby2low = -PI_4 + PI_4 * DU;
        final double phiby2high = PI_4 - PI_4 * DU;
        assertUWSequence(new double[] {
            phiby2low, 0,
            phiby2low, LARGE_W,
            phiby2high, 0,
            phiby2high, LARGE_W,
            0, 0,
            0, LARGE_W,
            phiby2low, 1.0,
            phiby2high, 1.0,
            0, 1.0,
            -PI_4 / 2, 0,
            -PI_4 / 2, LARGE_W,
            PI_4 / 2, 0,
            PI_4 / 2, LARGE_W,
        }, longs);

        final StableSampler sampler = StableSampler.of(
            createRngWithSequence(longs), alpha, beta, gamma, delta);
        for (int i = 0; i < 100; i++) {
            final double x = sampler.sample();
            if (!(lower <= x && x <= upper)) {
                Assert.fail(String.format("Invalid sample. alpha=%s, beta=%s, gamma=%s, delta=%s [%s, %s] x=%s",
                    alpha, beta, gamma, delta, lower, upper, x));
            }
        }
    }

    /**
     * Gets the support of the distribution. This returns the inclusive bounds. So exclusive
     * infinity is computed as the maximum finite value. Compute the value {@code mu} which is the
     * mean of the distribution when {@code alpha > 1}.
     *
     * <pre>
     * x in [mu, +inf)    if alpha < 1, beta = 1
     * x in (-inf, mu]    if alpha < 1, beta = -1
     * x in (-inf, -inf)  otherwise
     * </pre>
     *
     * @param alpha the alpha
     * @param beta the beta
     * @return the support ({lower, upper, mu})
     */
    private static double[] getSupport(double alpha, double beta) {
        // Convert alpha as used by the sampler
        double eps = 1 - alpha;
        double meps1 = 1 - eps;

        // Since pi is approximate the symmetry is lost by wrapping.
        // Keep within the domain using (2-alpha).
        double mu;
        if (alpha > 1) {
            mu = beta * Math.tan((2 - meps1) * PI_2);
        } else {
            // Special case where tan(pi/4) is not 1 (it is nextDown(1.0)).
            // This is needed when testing the Levy case during sampling.
            if (alpha == 0.5) {
                mu = -beta;
            } else {
                mu = -beta * Math.tan(meps1 * PI_2);
            }
        }

        // Standard support
        double lower = -Double.MAX_VALUE;
        double upper = Double.MAX_VALUE;
        if (meps1 < 1) {
            if (beta == 1) {
                // alpha < 0, beta = 1
                lower = mu;
            } else if (beta == -1) {
                // alpha < 0, beta = -1
                upper = mu;
            }
        }
        return new double[] {lower, upper, mu};
    }

    /**
     * Assumption test:
     * Test the random deviates u and w can be generated by manipulating the RNG.
     */
    @Test
    public void testRandomDeviatesUandW() {
        // Extremes of the uniform deviate generated using the same method as the sampler
        final double d = DU * PI_4;
        // Test in (-pi/4, pi/4)
        Assert.assertNotEquals(-PI_4, getU(createRngWithSequence(Long.MIN_VALUE)), 0.0);
        Assert.assertEquals(-PI_4 + d, getU(createRngWithSequence(Long.MIN_VALUE + (1 << 10))), 0.0);
        Assert.assertEquals(-PI_4 / 2, getU(createRngWithSequence(Long.MIN_VALUE >> 1)), 0.0);
        Assert.assertEquals(-d, getU(createRngWithSequence(-1)), 0.0);
        Assert.assertEquals(0.0, getU(createRngWithSequence(0)), 0.0);
        Assert.assertEquals(d, getU(createRngWithSequence(1 << 10)), 0.0);
        Assert.assertEquals(PI_4 / 2, getU(createRngWithSequence(Long.MIN_VALUE >>> 1)), 0.0);
        Assert.assertEquals(PI_4 - d, getU(createRngWithSequence(Long.MAX_VALUE)), 0.0);

        // Extremes of the exponential sampler
        Assert.assertEquals(0, ZigguratSampler.Exponential.of(
                createRngWithSequence(0L)).sample(), 0.0);
        Assert.assertEquals(7.39737323516E-19, ZigguratSampler.Exponential.of(
                createRngWithSequence(1)).sample(), 0.0);
        Assert.assertEquals(1.0, ZigguratSampler.Exponential.of(
                createRngWithSequence(1446480648965498882L)).sample(), 0.0);
        Assert.assertEquals(5.0, ZigguratSampler.Exponential.of(
                createRngWithSequence(6092639261718834176L)).sample(), 0.0);
        Assert.assertEquals(7.56927469415, ZigguratSampler.Exponential.of(
                createRngWithSequence(-1, -1, 0)).sample(), 0.0);
        Assert.assertEquals(3 * 7.56927469415, ZigguratSampler.Exponential.of(
                createRngWithSequence(-1, -1, -1, -1, -1, -1, 0)).sample(), 1e-14);
    }

    /**
     * Gets a uniform random variable in {@code (-pi/4, pi/4)}.
     *
     * <p>Copied from the StableSampler for testing. In the main sampler the variable u
     * is named either {@code phi} in {@code (-pi/2, pi/2)}, or
     * {@code phiby2} in {@code (-pi/4, pi/4)}. Here we test phiby2 for the CMS algorithm.
     *
     * @return u
     */
    private static double getU(UniformRandomProvider rng) {
        final double x = getU(rng.nextLong());
        if (x == -PI_4) {
            return getU(rng);
        }
        return x;
    }

    /**
     * Gets a uniform random variable in {@code [-pi/4, pi/4)} from a long value.
     *
     * <p>Copied from the StableSampler for testing. In the main sampler the variable u
     * is named either {@code phi} in {@code (-pi/2, pi/2)}, or
     * {@code phiby2} in {@code (-pi/4, pi/4)}. Here we test phiby2 for the CMS algorithm.
     *
     * <p>Examples of different output where {@code d} is the gap between values of {@code phi/2}
     * and is equal to {@code pi * 2^-55 = pi/4 * 2^-53}:
     *
     * <pre>
     * Long.MIN_VALUE                  -pi/4
     * Long.MIN_VALUE + (1 << 10)      -pi/4 + d
     * Long.MIN_VALUE >> 1             -pi/5
     * -1                              -d
     * 0                               0.0
     * 1 << 10                         d
     * Long.MIN_VALUE >>> 1            pi/5
     * Long.MAX_VALUE                  pi/4 - d
     * </pre>
     *
     * @return u
     */
    private static double getU(long x) {
        return (x >> 10) * PI_4_SCALED;
    }

    /**
     * Creates a RNG that will return the provided output for the next double and
     * next long functions. When the sequence is complete a valid random output
     * continues.
     *
     * <p>The sampler generates (in order):
     * <ol>
     * <li>{@code phi/2} in {@code (-pi/4, pi/4)} using long values from the RNG.
     * <li>{@code w} using the {@link ZigguratSampler.Exponential}.
     * This uses a long values from the RNG.
     * </ol>
     *
     * <p>Careful control of the the sequence can generate any value for {@code w} and {@code phi/2}.
     * The sampler creates a uniform deviate first, then an exponential deviate second.
     * Examples of different output where {@code d} is the gap between values of {@code phi/2}
     * and is equal to {@code pi * 2^-55 = pi/4 * 2^-53}:
     *
     * <pre>
     * longs                           phi/2               w
     * Long.MIN_VALUE                  try again [1]
     * Long.MIN_VALUE + (1 << 10)      -pi/4 + d
     * Long.MIN_VALUE >> 1             -pi/5
     * -1                              -d
     * 0                               0.0                 0
     * 1 << 10                         d
     * Long.MIN_VALUE >>> 1            pi/5
     * Long.MAX_VALUE                  pi/4 - d
     * 1                                                   7.39737323516E-19
     * 1446480648965498882L                                1.0
     * 6092639261718834176L                                5.0
     * -1, -1, 0                                           7.56927469415
     * -1L * 2n, 0                                         n * 7.56927469415  [2]
     * </pre>
     *
     * <ol>
     * <li>When phi/2=-pi/4 the method will ignore the value and obtain another long value.
     * <li>To create a large value for the exponential sampler requires recursion. Each input
     * of 2 * -1L will add 7.56927469415 to the total. A long of zero will stop recursion.
     * </ol>
     *
     * @param longs the initial sequence of longs
     * @return the uniform random provider
     */
    private static UniformRandomProvider createRngWithSequence(final long... longs) {
        // Note:
        // The StableSampler uniform deviate is generated from a long.
        // It is ignored if zero, a value of 1 << 11 generates the smallest value (2^-53).
        //
        // The ZigguratSampler.Exponential uses a single long value >98% of the time.
        // To create a certain value x the input y can be obtained by reversing the
        // computation of the corresponding precomputed factor X. The lowest 8 bits of y
        // choose the index i into X so must be set as the lowest bits.
        //
        // To find y to obtain the sample x use:
        // double[] X = { /* from ZigguratSampler.Exponential */ }
        // double x = 1.0; // or any other value < 7.5
        // for (int i = 0; i < X.length; i++) {
        //     // Add back the index to the lowest 8 bits.
        //     // This will work if the number is so big that the lower bits
        //     // are zerod when casting the 53-bit mantissa to a long.
        //     long y = (long) (x / X[i]) + i;
        //     if (y * X[i] == x) {
        //         // Found y!
        //     }
        // }

        // Start with a valid RNG.
        // This is required for nextDouble() since invoking super.nextDouble() when
        // the sequence has expired will call nextLong() and may use the intended
        // sequence of longs.
        final UniformRandomProvider rng = RandomSource.JSF_64.create(0x6237846L);

        // A RNG with the provided output
        return new SplitMix64(0L) {
            private int l;

            @Override
            public long nextLong() {
                if (l == longs.length) {
                    return rng.nextLong();
                }
                return longs[l++];
            }
        };
    }

    /**
     * Assert the sequence of output from a uniform deviate and exponential deviate
     * created using the same method as the sampler.
     *
     * <p>The RNG is created using
     * {@link #createRngWithSequence(long[])}. See the method javadoc for
     * examples of how to generate different deviate values.
     *
     * @param expected the expected output (u1, w1, u2, w2, u3, w3, ...)
     * @param longs the initial sequence of longs
     */
    private static void assertUWSequence(double[] expected, long[] longs) {
        final UniformRandomProvider rng = createRngWithSequence(longs);

        // Validate series
        final SharedStateContinuousSampler exp = ZigguratSampler.Exponential.of(rng);
        for (int i = 0; i < expected.length; i += 2) {
            int j = i / 2;
            Assert.assertEquals(j + ": Incorrect u", expected[i], getU(rng), 0.0);
            if (i + 1 < expected.length) {
                Assert.assertEquals(j + ": Incorrect w", expected[i + 1], exp.sample(), 0.0);
            }
        }
    }

    /**
     * Test the sampler output is a continuous function of {@code alpha} and {@code beta}.
     * This test verifies the switch to the dedicated {@code alpha=1} or {@code beta=0}
     * samplers computes a continuous function of the parameters.
     */
    @Test
    public void testSamplerOutputIsContinuousFunction() {
        // Test alpha passing through 1 when beta!=0 (switch to an alpha=1 sampler)
        for (final double beta : new double[] {0.5, 0.2, 0.1, 0.001}) {
            testSamplerOutputIsContinuousFunction(1 + 8096 * DU, beta, 1.0, beta, 1 - 8096 * DU, beta, 0);
            testSamplerOutputIsContinuousFunction(1 + 1024 * DU, beta, 1.0, beta, 1 - 1024 * DU, beta, 0);
            // Not perfect when alpha -> 1
            testSamplerOutputIsContinuousFunction(1 + 128 * DU, beta, 1.0, beta, 1 - 128 * DU, beta, 1);
            testSamplerOutputIsContinuousFunction(1 + 16 * DU, beta, 1.0, beta, 1 - 16 * DU, beta, 4);
            // This works with ulp=0. Either this is a lucky random seed or because the approach
            // to 1 creates equal output.
            testSamplerOutputIsContinuousFunction(1 + DU, beta, 1.0, beta, 1 - DU, beta, 0);
        }
        // Test beta passing through 0 when alpha!=1 (switch to a beta=0 sampler)
        for (final double alpha : new double[] {1.5, 1.2, 1.1, 1.001}) {
            testSamplerOutputIsContinuousFunction(alpha, 8096 * DU, alpha, 0, alpha, -8096 * DU, 0);
            testSamplerOutputIsContinuousFunction(alpha, 1024 * DU, alpha, 0, alpha, -1024 * DU, 0);
            testSamplerOutputIsContinuousFunction(alpha, 128 * DU, alpha, 0, alpha, -128 * DU, 0);
            // Not perfect when beta is very small
            testSamplerOutputIsContinuousFunction(alpha, 16 * DU, alpha, 0, alpha, -16 * DU, 3);
            testSamplerOutputIsContinuousFunction(alpha, DU, alpha, 0, alpha, -DU, 2);
        }

        // Note: No test for transition to the Cauchy case (alpha=1, beta=0).
        // Requires a RNG that discards output that would be used to create a exponential
        // deviate. Just create one each time a request for nextLong is performed and
        // ensure nextLong >>> 11 is not zero.

        // When the parameters create a special case sampler this will not work.
        // alpha passing through 0.5 when beta=1 (Levy sampler)
        // alpha -> 2 as the sampler (Gaussian sampler).
    }

    /**
     * Test sampler output is a continuous function of {@code alpha} and
     * {@code beta}. Create 3 samplers with the same RNG and test the middle sampler
     * computes a value between the upper and lower sampler.
     *
     * @param alpha1 lower sampler alpha
     * @param beta1 lower sampler beta
     * @param alpha2 middle sampler alpha
     * @param beta2 middle sampler beta
     * @param alpha3 upper sampler alpha
     * @param beta3 upper sampler beta
     */
    private static void testSamplerOutputIsContinuousFunction(double alpha1, double beta1,
                                                              double alpha2, double beta2,
                                                              double alpha3, double beta3,
                                                              int ulp) {
        final long seed = 0x62738468L;
        final UniformRandomProvider rng1 = RandomSource.XO_RO_SHI_RO_128_PP.create(seed);
        final UniformRandomProvider rng2 = RandomSource.XO_RO_SHI_RO_128_PP.create(seed);
        final UniformRandomProvider rng3 = RandomSource.XO_RO_SHI_RO_128_PP.create(seed);
        final StableSampler sampler1 = StableSampler.of(rng1, alpha1, beta1);
        final StableSampler sampler2 = StableSampler.of(rng2, alpha2, beta2);
        final StableSampler sampler3 = StableSampler.of(rng3, alpha3, beta3);
        final String msg = String.format("alpha=%s, beta=%s", alpha2, beta2);
        for (int i = 0; i < 1000; i++) {
            final double x1 = sampler1.sample();
            final double x2 = sampler2.sample();
            final double x3 = sampler3.sample();
            // x2 should be in between x1 and x3
            if (x3 > x1) {
                if (x2 > x3) {
                    // Should be the same
                    Assert.assertEquals(msg, x3, x2, ulp * Math.ulp(x3));
                } else if (x2 < x1) {
                    Assert.assertEquals(msg, x1, x2, ulp * Math.ulp(x1));
                }
            } else if (x3 < x1) {
                if (x2 < x3) {
                    // Should be the same
                    Assert.assertEquals(msg, x3, x2, ulp * Math.ulp(x3));
                } else if (x2 > x1) {
                    Assert.assertEquals(msg, x1, x2, ulp * Math.ulp(x1));
                }
            }
        }
    }

    /**
     * Test the SharedStateSampler implementation for each case using a different implementation.
     */
    @Test
    public void testSharedStateSampler() {
        // Gaussian case
        testSharedStateSampler(2.0, 0.0);
        // Cauchy case
        testSharedStateSampler(1.0, 0.0);
        // Levy case
        testSharedStateSampler(0.5, 1.0);
        // Hit code coverage of alpha=0.5 (Levy case) but beta != 1
        testSharedStateSampler(0.5, 0.1);
        // Beta 0 (symmetric) case
        testSharedStateSampler(1.3, 0.0);
        // Alpha 1 case
        testSharedStateSampler(1.0, 0.23);
        // Alpha close to 1
        testSharedStateSampler(nextUp(1.0), 0.23);
        // General case
        testSharedStateSampler(1.3, 0.1);
        // Small alpha cases
        testSharedStateSampler(1e-5, 0.1);
        testSharedStateSampler(1e-5, 0.0);
        // Large alpha case.
        // This hits code coverage for computing tau from (1-alpha) -> -1
        testSharedStateSampler(1.99, 0.1);
    }

    /**
     * Test the SharedStateSampler implementation. This tests with and without the
     * {@code gamma} and {@code delta} parameters.
     *
     * @param alpha Alpha.
     * @param beta Beta.
     */
    private static void testSharedStateSampler(double alpha, double beta) {
        final UniformRandomProvider rng1 = RandomSource.SPLIT_MIX_64.create(0L);
        final UniformRandomProvider rng2 = RandomSource.SPLIT_MIX_64.create(0L);
        StableSampler sampler1 = StableSampler.of(rng1, alpha, beta);
        StableSampler sampler2 = sampler1.withUniformRandomProvider(rng2);
        RandomAssert.assertProduceSameSequence(sampler1, sampler2);
        // Test shifted
        sampler1 = StableSampler.of(rng1, alpha, beta, 1.3, 13.2);
        sampler2 = sampler1.withUniformRandomProvider(rng2);
        RandomAssert.assertProduceSameSequence(sampler1, sampler2);
    }

    /**
     * Test the implementation of the transformed sampler (scaled and translated).
     */
    @Test
    public void testTransformedSampler() {
        // Gaussian case
        // The Gaussian case has its own scaling where the StdDev is gamma * sqrt(2).
        // (N(x) * sqrt(2)) * gamma != N(x) * (sqrt(2) * gamma)
        // Test with a delta
        testTransformedSampler(2.0, 0.0, 1);
        // Cauchy case
        testTransformedSampler(1.0, 0.0);
        // Levy case
        testTransformedSampler(0.5, 1.0);
        // Symmetric case
        testTransformedSampler(1.3, 0.0);
        // Alpha 1 case
        testTransformedSampler(1.0, 0.23);
        // Alpha close to 1
        testTransformedSampler(nextUp(1.0), 0.23);
        // General case
        testTransformedSampler(1.3, 0.1);
        // Small alpha case
        testTransformedSampler(1e-5, 0.1);
        // Large alpha case.
        // This hits the case for computing tau from (1-alpha) -> -1.
        testTransformedSampler(1.99, 0.1);
    }

    /**
     * Test the implementation of the transformed sampler (scaled and translated).
     * The transformed output must match exactly.
     *
     * @param alpha Alpha.
     * @param beta Beta.
     */
    private static void testTransformedSampler(double alpha, double beta) {
        testTransformedSampler(alpha, beta, 0);
    }

    /**
     * Test the implementation of the transformed sampler (scaled and translated).
     * The transformed output must match within the provided ULP.
     *
     * @param alpha Alpha.
     * @param beta Beta.
     * @param ulp Allowed ULP difference.
     */
    private static void testTransformedSampler(double alpha, double beta, int ulp) {
        final UniformRandomProvider rng1 = RandomSource.SPLIT_MIX_64.create(0L);
        final UniformRandomProvider rng2 = RandomSource.SPLIT_MIX_64.create(0L);
        final double gamma = 3.4;
        final double delta = -17.3;
        final StableSampler sampler1 = StableSampler.of(rng1, alpha, beta, gamma, delta);
        final ContinuousSampler sampler2 = createTransformedSampler(rng2, alpha, beta, gamma, delta);
        if (ulp == 0) {
            RandomAssert.assertProduceSameSequence(sampler1, sampler2);
        } else {
            for (int i = 0; i < 10; i++) {
                final double x1 = sampler1.sample();
                final double x2 = sampler2.sample();
                Assert.assertEquals(x1, x2, ulp * Math.ulp(x1));
            }
        }
    }

    /**
     * Create a transformed sampler from a normalized sampler scaled and translated by
     * gamma and delta.
     *
     * @param rng Source of randomness.
     * @param alpha Alpha.
     * @param beta Beta.
     * @param gamma Gamma.
     * @param delta Delta.
     * @return the transformed sampler
     */
    private static ContinuousSampler createTransformedSampler(UniformRandomProvider rng,
                                                              double alpha, double beta,
                                                              final double gamma, final double delta) {
        final StableSampler delegate = StableSampler.of(rng, alpha, beta);
        return new ContinuousSampler() {
            @Override
            public double sample() {
                return gamma * delegate.sample() + delta;
            }
        };
    }

    /**
     * Test symmetry when when u and beta are mirrored around 0.5 and 0 respectively.
     */
    @Test
    public void testSymmetry() {
        final byte[] seed = RandomSource.KISS.createSeed();
        for (final double alpha : new double[] {1e-4, 0.78, 1, 1.23}) {
            for (final double beta : new double[] {-0.43, 0.23}) {
                for (final double gamma : new double[] {0.78, 1, 1.23}) {
                    for (final double delta : new double[] {-0.43, 0, 0.23}) {
                        // The sampler generates u then w.
                        // If u is not -pi/4 then only a single long is used.
                        // This can be reversed around 0 by reversing the upper 54-bits.
                        // w will use 1 long only for fast lookup and then additional longs
                        // for edge of the ziggurat sampling. Fast look-up is always used
                        // when the lowest 8-bits create a value below 252.

                        // Use the same random source for two samplers.
                        final UniformRandomProvider rng1 = RandomSource.KISS.create(seed);
                        final UniformRandomProvider rng2 = RandomSource.KISS.create(seed);

                        // RNG which will not return 0 for every other long.
                        final UniformRandomProvider forward = new SplitMix64(0) {
                            private int i;
                            @Override
                            public long nextLong() {
                                // Manipulate alternate longs
                                if ((i++ & 0x1) == 0) {
                                    // This must not be Long.MIN_VALUE.
                                    // So set the lowest bit of the upper 54-bits.
                                    final long x = rng1.nextLong() >>> 10 | 1L;
                                    // Shift back
                                    return x << 10;
                                }
                                // For the exponential sample ensure the lowest 8-bits are < 252.
                                long x;
                                do {
                                    x = rng1.nextLong();
                                } while ((x & 0xff) >= 252);
                                return x;
                            }
                        };

                        // RNG which will not return 0 for every other long but this long is reversed.
                        final UniformRandomProvider reverse = new SplitMix64(0) {
                            private final long upper = 1L << 54;
                            private int i;
                            @Override
                            public long nextLong() {
                                // Manipulate alternate longs
                                if ((i++ & 0x1) == 0) {
                                    // This must not be Long.MIN_VALUE.
                                    // So set the lowest bit of the upper 54-bits.
                                    final long x = rng2.nextLong() >>> 10 | 1L;
                                    // Reverse then shift back
                                    return (upper - x) << 10;
                                }
                                // For the exponential sample ensure the lowest 8-bits are < 252.
                                long x;
                                do {
                                    x = rng2.nextLong();
                                } while ((x & 0xff) >= 252);
                                return x;
                            }
                        };

                        final StableSampler s1 = StableSampler.of(forward, alpha, beta, gamma, delta);
                        // Since mirroring applies before the shift of delta this must be negated too
                        final StableSampler s2 = StableSampler.of(reverse, alpha, -beta, gamma, -delta);
                        for (int i = 0; i < 100; i++) {
                            Assert.assertEquals(s1.sample(), -s2.sample(), 0.0);
                        }
                    }
                }
            }
        }
    }

    /**
     * Test symmetry for the Levy case ({@code alpha = 0.5} and {@code beta = 1}.
     */
    @Test
    public void testSymmetryLevy() {
        final double alpha = 0.5;
        final double beta = 1.0;
        final byte[] seed = RandomSource.KISS.createSeed();
        final UniformRandomProvider rng1 = RandomSource.KISS.create(seed);
        final UniformRandomProvider rng2 = RandomSource.KISS.create(seed);
        for (final double gamma : new double[] {0.78, 1, 1.23}) {
            for (final double delta : new double[] {-0.43, 0, 0.23}) {
                final StableSampler s1 = StableSampler.of(rng1, alpha, beta, gamma, delta);
                // Since mirroring applies before the shift of delta this must be negated too
                final StableSampler s2 = StableSampler.of(rng2, alpha, -beta, gamma, -delta);
                for (int i = 0; i < 100; i++) {
                    Assert.assertEquals(s1.sample(), -s2.sample(), 0.0);
                }
            }
        }
    }

    /**
     * Test the toString method for cases not hit in the rest of the test suite.
     * This test asserts the toString method always contains the string 'stable'
     * even for parameters that create the Gaussian, Cauchy or Levy cases.
     */
    @Test
    public void testToString() {
        final UniformRandomProvider rng = RandomSource.SPLIT_MIX_64.create(0L);
        for (final double[] p : new double[][] {
            {1.3, 0.1},
            {2.0, 0.0},
            {1.0, 0.0},
            {0.5, 1.0},
            {1e-5, 0},
            {1e-5, 0.1},
            {0.7, 0.1, 3.0, 4.5},
        }) {
            StableSampler sampler;
            if (p.length == 2) {
                sampler = StableSampler.of(rng, p[0], p[1]);
            } else {
                sampler = StableSampler.of(rng, p[0], p[1], p[2], p[3]);
            }
            final String s = sampler.toString().toLowerCase();
            Assert.assertTrue(s.contains("stable"));
        }
    }

    /**
     * Demonstrate the CMS sampler matches the Weron sampler when {@code alpha != 1}.
     * This shows the two are equivalent; they should match as the formulas are rearrangements.
     * Avoid testing as {@code alpha -> 1} as the Weron sampler loses bits of precision in
     * the output sample.
     *
     * <p>Note: Uses direct instantiation via the package-private constructors. This avoids
     * the factory method constructor to directly select the implementation. Constructor
     * parameters are not validated.
     */
    @Test
    public void testImplementationsMatch() {
        // Avoid extreme samples. Do this by manipulating the output of nextLong.
        // Generation of the random deviate u uses the top 54-bits of the long.
        // Unset a high bit to ensure getU cannot approach pi/4.
        // Set a low bit to ensure getU cannot approach -pi/4.
        final long unsetHighBit = ~(1L << 54);
        final long setLowBit = 1L << 53;
        final double hi = getU(Long.MAX_VALUE & unsetHighBit);
        final double lo = getU(Long.MIN_VALUE | setLowBit);
        // The limits are roughly pi/4 and -pi/4
        Assert.assertEquals(PI_4, hi, 2e-3);
        Assert.assertEquals(-PI_4, lo, 2e-3);
        Assert.assertEquals(0.0, lo + hi, 1e-3);

        // Setting a bit ensure the exponential sampler cannot be zero
        final UniformRandomProvider rng = createRngWithSequence(setLowBit);
        final double w = ZigguratSampler.Exponential.of(rng).sample();
        Assert.assertNotEquals(0.0, w, 0.0);
        // This is the actual value; it is small but not extreme.
        Assert.assertEquals(0.007391869818499571, w, 0.0);

        final RandomSource source = RandomSource.XO_RO_SHI_RO_128_SS;
        final long seed = 0x83762b3daf1c43L;
        final UniformRandomProvider rng1 = new SplitMix64(0L) {
            private UniformRandomProvider delegate = source.create(seed);
            @Override
            public long next() {
                final long x = delegate.nextLong();
                return (x & unsetHighBit) | setLowBit;
            }
        };
        final UniformRandomProvider rng2 = new SplitMix64(0L) {
            private UniformRandomProvider delegate = source.create(seed);
            @Override
            public long next() {
                final long x = delegate.nextLong();
                return (x & unsetHighBit) | setLowBit;
            }
        };

        // Not too close to alpha=1
        final double[] alphas = {0.3, 0.5, 1.2, 1.5};
        final double[] betas = {-0.5, -0.3, -0.1, 0};

        final double relative = 1e-5;
        final double absolute = 1e-10;

        for (final double alpha : alphas) {
            for (final double beta : betas) {
                final String msg = String.format("alpha=%s, beta=%s", alpha, beta);
                // WARNING:
                // Created by direct access to package-private constructor.
                // This is for testing only as these do not validate the parameters.
                StableSampler s1;
                StableSampler s2;
                if (beta == 0) {
                    s1 = new Beta0CMSStableSampler(rng1, alpha);
                    s2 = new Beta0WeronStableSampler(rng2, alpha);
                } else {
                    s1 = new CMSStableSampler(rng1, alpha, beta);
                    s2 = new WeronStableSampler(rng2, alpha, beta);
                }
                for (int i = 0; i < 1000; i++) {
                    final double x = s1.sample();
                    final double y = s2.sample();
                    Assert.assertEquals(msg, x, y, Math.max(absolute, Math.abs(x) * relative));
                }
            }
        }
    }

    /**
     * Demonstrate the general CMS sampler matches the {@code beta = 0} sampler.
     * The {@code beta = 0} sampler implements the same algorithm with cancelled terms removed.
     *
     * <p>Note: Uses direct instantiation via the package-private constructors. This avoids
     * the factory method constructor to directly select the implementation. Constructor
     * parameters are not validated.
     */
    @Test
    public void testSpecializedBeta0CMSImplementation() {
        final RandomSource source = RandomSource.XO_RO_SHI_RO_128_SS;
        // Should be robust to any seed
        final byte[] seed = source.createSeed();
        final UniformRandomProvider rng1 = source.create(seed);
        final UniformRandomProvider rng2 = source.create(seed);

        final double[] alphas = {0.3, 0.5, 1.2, 1.5};
        for (final double alpha : alphas) {
            // WARNING:
            // Created by direct access to package-private constructor.
            // This is for testing only as these do not validate the parameters.
            final StableSampler sampler1 = new CMSStableSampler(rng1, alpha, 0.0);
            final StableSampler sampler2 = new Beta0CMSStableSampler(rng2, alpha);
            RandomAssert.assertProduceSameSequence(sampler1, sampler2);
        }
    }

    /**
     * Demonstrate the general Weron sampler matches the {@code beta = 0} sampler.
     * The {@code beta = 0} sampler implements the same algorithm with cancelled terms removed.
     *
     * <p>Note: Uses direct instantiation via the package-private constructors. This avoids
     * the factory method constructor to directly select the implementation. Constructor
     * parameters are not validated.
     */
    @Test
    public void testSpecializedBeta0WeronImplementation() {
        final RandomSource source = RandomSource.XO_RO_SHI_RO_128_SS;
        // Should be robust to any seed
        final byte[] seed = source.createSeed();
        final UniformRandomProvider rng1 = source.create(seed);
        final UniformRandomProvider rng2 = source.create(seed);

        final double[] alphas = {0.3, 0.5, 1.2, 1.5};
        for (final double alpha : alphas) {
            // WARNING:
            // Created by direct access to package-private constructor.
            // This is for testing only as these do not validate the parameters.
            final StableSampler sampler1 = new WeronStableSampler(rng1, alpha, 0.0);
            final StableSampler sampler2 = new Beta0WeronStableSampler(rng2, alpha);
            RandomAssert.assertProduceSameSequence(sampler1, sampler2);
        }
    }

    /**
     * Test the Weron sampler when the term t1 is zero in the numerator.
     * This hits an edge case where sin(alpha * phi + atan(-zeta)) is zero.
     *
     * @see #testSinAlphaPhiMinusAtanZeta()
     */
    @Test
    public void testWeronImplementationEdgeCase() {
        double alpha = 0.25;
        // Solved in testSinAlphaPhiMinusAtanZeta()
        double beta = -0.48021693505171;
        // Require phi = PI_4.
        // This is the equivalent of phi/2 = pi/5
        final long x = Long.MIN_VALUE >>> 1;
        final long[] longs = new long[] {
            // phi/2=pi/5, w=0
            x, 0,
            // phi/2=pi/5, w=large
            x, -1, -1, -1, -1, -1, -1, -1, -1, 0,
            // phi/2=pi/5, w=1
            x, 1446480648965498882L,
        };

        // Validate series
        assertUWSequence(new double[] {
            PI_4 / 2, 0,
            PI_4 / 2, LARGE_W,
            PI_4 / 2, 1.0,
        }, longs);

        final double zeta = -beta * Math.tan(alpha * PI_2);
        Assert.assertEquals(0.0, alpha * PI_4 + Math.atan(-zeta), 0.0);

        final UniformRandomProvider rng = createRngWithSequence(longs);
        final StableSampler sampler = new WeronStableSampler(rng, alpha, beta);
        // zeta is the offset used to shift the 1-parameterization to the
        // 0-parameterization. This is returned when other terms multiply to zero.
        Assert.assertEquals(zeta, sampler.sample(), 0.0);
        Assert.assertEquals(zeta, sampler.sample(), 0.0);
        Assert.assertEquals(zeta, sampler.sample(), 0.0);
    }

    /**
     * Checks if the value is finite.
     *
     * <p>To be replaced by {@code Double.isFinite(double)} from JDK 1.8.
     *
     * @param value the value
     * @return true if finite
     */
    private static boolean isFinite(double value) {
        return Math.abs(value) <= Double.MAX_VALUE;
    }

    /**
     * Return the next value after {@code x} in the direction of positive infinity.
     *
     * <p>To be replaced by Math.nextUp(double) from JDK 1.8.
     *
     * @param x the x
     * @return the next value up
     */
    private static double nextUp(double x) {
        return Math.nextAfter(x, Double.POSITIVE_INFINITY);
    }

    /**
     * Return the next value after {@code x} in the direction of negative infinity.
     *
     * <p>To be replaced by Math.nextDown(double) from JDK 1.8.
     *
     * @param x the x
     * @return the next value down
     */
    private static double nextDown(double x) {
        return Math.nextAfter(x, Double.NEGATIVE_INFINITY);
    }
}
