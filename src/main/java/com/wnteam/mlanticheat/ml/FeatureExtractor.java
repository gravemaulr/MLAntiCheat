package com.wnteam.mlanticheat.ml;

import com.wnteam.mlanticheat.data.PlayerData;

import java.util.List;

public final class FeatureExtractor {

    public static final int FEATURE_COUNT = 39;

    private static final double EPSILON = 1.0E-9;
    private static final double GCD_RESOLUTION = 1.0E-6;
    private static final double DUPLICATE_RESOLUTION = 1.0E-5;
    private static final int ENTROPY_BINS = 8;
    private static final int MAX_PERIOD_LAG = 12;

    private FeatureExtractor() {
    }

    public record Analysis(int samples,
                           double yawMean,
                           double yawStd,
                           double pitchMean,
                           double pitchStd,
                           double yawAccelMean,
                           double yawAccelStd,
                           double pitchAccelMean,
                           double pitchAccelStd,
                           double yawJerkMean,
                           double pitchJerkMean,
                           double gcdYaw,
                           double gcdPitch,
                           int gcdSamples,
                           double constantDeltaRatio,
                           double microRatio,
                           double pitchSilenceRatio,
                           double yawFlipRate,
                           double pitchFlipRate,
                           double correlation,
                           double entropy,
                           double smoothness,
                           double intervalJitter,
                           double maxYaw,
                           double snapRatio,
                           double cps,
                           double attackIntervalStd,
                           double attackIntervalCv,
                           double angleMean,
                           double angleStd,
                           double postHitYawMean,
                           double reactionMin,
                           boolean combat,
                           double autocorrelation,
                           double periodicity,
                           double duplicateDeltaRatio,
                           double packetsPerTick,
                           double reachMean) {

        public double[] toFeatures() {
            double[] out = new double[FEATURE_COUNT];
            out[0] = squash(yawMean, 12.0);
            out[1] = squash(yawStd, 12.0);
            out[2] = squash(pitchMean, 6.0);
            out[3] = squash(pitchStd, 6.0);
            out[4] = squash(yawAccelMean, 12.0);
            out[5] = squash(yawAccelStd, 12.0);
            out[6] = squash(pitchAccelMean, 6.0);
            out[7] = squash(pitchAccelStd, 6.0);
            out[8] = squash(yawJerkMean, 12.0);
            out[9] = squash(pitchJerkMean, 6.0);
            out[10] = gcdSamples <= 0 ? 0.0 : clamp(1.0 - Math.min(1.0, gcdYaw / 0.0016));
            out[11] = gcdSamples <= 0 ? 0.0 : clamp(1.0 - Math.min(1.0, gcdPitch / 0.0016));
            out[12] = clamp(constantDeltaRatio);
            out[13] = clamp(microRatio);
            out[14] = clamp(pitchSilenceRatio);
            out[15] = clamp(yawFlipRate);
            out[16] = clamp(pitchFlipRate);
            out[17] = clamp(Math.abs(correlation));
            out[18] = clamp(entropy);
            out[19] = squash(smoothness, 3.0);
            out[20] = squash(intervalJitter, 40.0);
            out[21] = squash(maxYaw, 90.0);
            out[22] = clamp(snapRatio);
            out[23] = squash(cps, 20.0);
            out[24] = squash(attackIntervalStd, 90.0);
            out[25] = clamp(attackIntervalCv);
            out[26] = squash(angleMean, 90.0);
            out[27] = squash(angleStd, 45.0);
            out[28] = squash(postHitYawMean, 30.0);
            out[29] = reactionMin < 0 ? 0.0 : clamp(1.0 - Math.min(1.0, reactionMin / 220.0));
            out[30] = combat ? 1.0 : 0.0;
            out[31] = squash(samples, 160.0);
            out[32] = clamp(Math.abs(autocorrelation));
            out[33] = clamp(Math.abs(periodicity));
            out[34] = clamp(duplicateDeltaRatio);
            out[35] = squash(Math.max(0.0, packetsPerTick - 1.0), 3.0);
            out[36] = reachMean <= 0.0 ? 0.0 : squash(reachMean, 4.0);
            for (int i = 0; i < out.length; i++) {
                if (!Double.isFinite(out[i])) {
                    out[i] = 0.0;
                }
            }
            return out;
        }
    }

    public static Analysis analyze(PlayerData data, long combatWindow) {
        List<PlayerData.RotationSample> samples = data.rotationSnapshot();
        int n = samples.size();
        if (n < 4) {
            return empty(data, combatWindow, n);
        }

        double[] yaw = new double[n];
        double[] pitch = new double[n];
        double[] dt = new double[n - 1];
        long[] ticks = new long[n];
        for (int i = 0; i < n; i++) {
            yaw[i] = samples.get(i).deltaYaw();
            pitch[i] = samples.get(i).deltaPitch();
            ticks[i] = samples.get(i).tick();
            if (i > 0) {
                dt[i - 1] = Math.max(1.0, samples.get(i).time() - samples.get(i - 1).time());
            }
        }

        double[] absYaw = abs(yaw);
        double[] absPitch = abs(pitch);
        double[] yawAccel = diff(yaw);
        double[] pitchAccel = diff(pitch);
        double[] yawJerk = diff(yawAccel);
        double[] pitchJerk = diff(pitchAccel);

        double yawMean = mean(absYaw);
        double pitchMean = mean(absPitch);

        int constant = 0;
        int constantBase = 0;
        int micro = 0;
        int pitchSilent = 0;
        int pitchBase = 0;
        int snaps = 0;
        for (int i = 0; i < n; i++) {
            if (absYaw[i] > 0.05) {
                constantBase++;
                if (i > 0 && Math.abs(absYaw[i] - absYaw[i - 1]) < 5.0E-4) {
                    constant++;
                }
            }
            if (absYaw[i] > EPSILON && absYaw[i] < 0.08) {
                micro++;
            }
            if (absYaw[i] > 0.5) {
                pitchBase++;
                if (absPitch[i] < 1.0E-4) {
                    pitchSilent++;
                }
            }
            if (absYaw[i] > 15.0) {
                snaps++;
            }
        }

        double gcdYaw = gcd(absYaw, 0.05);
        double gcdPitch = gcd(absPitch, 0.05);
        int gcdSamples = countAbove(absYaw, 0.05);

        double[] intervalStats = data.attackIntervalStats();
        double[] angleStats = data.angleStats();
        double[] postHit = data.postHitYawStats();

        double intervalMean = intervalStats[0];
        double intervalStd = intervalStats[1];

        return new Analysis(
                n,
                yawMean,
                std(absYaw, yawMean),
                pitchMean,
                std(absPitch, pitchMean),
                mean(abs(yawAccel)),
                std(abs(yawAccel), mean(abs(yawAccel))),
                mean(abs(pitchAccel)),
                std(abs(pitchAccel), mean(abs(pitchAccel))),
                mean(abs(yawJerk)),
                mean(abs(pitchJerk)),
                gcdYaw,
                gcdPitch,
                gcdSamples,
                constantBase == 0 ? 0.0 : (double) constant / constantBase,
                (double) micro / n,
                pitchBase == 0 ? 0.0 : (double) pitchSilent / pitchBase,
                flipRate(yaw),
                flipRate(pitch),
                correlation(absYaw, absPitch),
                entropy(absYaw),
                yawMean < 1.0E-4 ? 0.0 : mean(abs(yawAccel)) / (yawMean + EPSILON),
                std(dt, mean(dt)),
                max(absYaw),
                (double) snaps / n,
                data.attacksPerSecond(),
                intervalStd,
                intervalMean < EPSILON ? 0.0 : Math.min(1.0, intervalStd / intervalMean),
                angleStats[0],
                angleStats[1],
                postHit[0],
                data.minReactionTime(),
                data.inCombat(combatWindow),
                autocorrelation(yaw, 1),
                periodicity(yaw),
                duplicateRatio(absYaw),
                packetsPerTick(ticks),
                reachMean(data));
    }

    private static Analysis empty(PlayerData data, long combatWindow, int samples) {
        return new Analysis(samples, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                data.attacksPerSecond(), 0, 0, 0, 0, 0, -1, data.inCombat(combatWindow), 0, 0, 0, 1.0, 0);
    }

    public static double[] extract(PlayerData data, long combatWindow) {
        return analyze(data, combatWindow).toFeatures();
    }

    private static double reachMean(PlayerData data) {
        List<PlayerData.AttackSample> attacks = data.attackSnapshot();
        double sum = 0.0;
        int used = 0;
        for (PlayerData.AttackSample attack : attacks) {
            if (attack.reach() > 0.0) {
                sum += attack.reach();
                used++;
            }
        }
        return used == 0 ? 0.0 : sum / used;
    }

    private static double packetsPerTick(long[] ticks) {
        int distinct = 0;
        long previous = Long.MIN_VALUE;
        for (long tick : ticks) {
            if (tick < 0) {
                return 1.0;
            }
            if (tick != previous) {
                distinct++;
                previous = tick;
            }
        }
        return distinct == 0 ? 1.0 : (double) ticks.length / distinct;
    }

    private static double duplicateRatio(double[] values) {
        int base = 0;
        int duplicates = 0;
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (double value : values) {
            if (value < 0.01) {
                continue;
            }
            base++;
            long key = Math.round(value / DUPLICATE_RESOLUTION);
            if (!seen.add(key)) {
                duplicates++;
            }
        }
        return base < 12 ? 0.0 : (double) duplicates / base;
    }

    private static double autocorrelation(double[] values, int lag) {
        if (values.length <= lag + 6) {
            return 0.0;
        }
        double average = mean(values);
        double denominator = 0.0;
        for (double value : values) {
            double centered = value - average;
            denominator += centered * centered;
        }
        if (denominator < EPSILON) {
            return 0.0;
        }
        double numerator = 0.0;
        for (int i = lag; i < values.length; i++) {
            numerator += (values[i] - average) * (values[i - lag] - average);
        }
        return numerator / denominator;
    }

    private static double periodicity(double[] values) {
        double best = 0.0;
        for (int lag = 2; lag <= MAX_PERIOD_LAG; lag++) {
            best = Math.max(best, Math.abs(autocorrelation(values, lag)));
        }
        return best;
    }

    private static double gcd(double[] values, double minMagnitude) {
        double current = 0.0;
        int used = 0;
        for (double value : values) {
            if (value < minMagnitude) {
                continue;
            }
            double quantized = Math.round(value / GCD_RESOLUTION) * GCD_RESOLUTION;
            current = gcd(current, quantized);
            used++;
            if (current > 0.0 && current < GCD_RESOLUTION * 2.0) {
                break;
            }
        }
        return used < 6 ? 1.0 : current;
    }

    private static double gcd(double a, double b) {
        double x = Math.max(a, b);
        double y = Math.min(a, b);
        while (y > GCD_RESOLUTION) {
            double remainder = x % y;
            x = y;
            y = remainder;
        }
        return x;
    }

    private static int countAbove(double[] values, double threshold) {
        int count = 0;
        for (double value : values) {
            if (value >= threshold) {
                count++;
            }
        }
        return count < 6 ? 0 : count;
    }

    private static double[] abs(double[] values) {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = Math.abs(values[i]);
        }
        return out;
    }

    private static double[] diff(double[] values) {
        if (values.length < 2) {
            return new double[]{0.0};
        }
        double[] out = new double[values.length - 1];
        for (int i = 1; i < values.length; i++) {
            out[i - 1] = values[i] - values[i - 1];
        }
        return out;
    }

    private static double mean(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double std(double[] values, double mean) {
        if (values.length < 2) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }
        return Math.sqrt(sum / values.length);
    }

    private static double max(double[] values) {
        double best = 0.0;
        for (double value : values) {
            best = Math.max(best, value);
        }
        return best;
    }

    private static double flipRate(double[] values) {
        int flips = 0;
        int base = 0;
        for (int i = 1; i < values.length; i++) {
            if (Math.abs(values[i]) < 0.01 || Math.abs(values[i - 1]) < 0.01) {
                continue;
            }
            base++;
            if (Math.signum(values[i]) != Math.signum(values[i - 1])) {
                flips++;
            }
        }
        return base == 0 ? 0.0 : (double) flips / base;
    }

    private static double correlation(double[] a, double[] b) {
        double meanA = mean(a);
        double meanB = mean(b);
        double cov = 0.0;
        double varA = 0.0;
        double varB = 0.0;
        for (int i = 0; i < a.length; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            cov += da * db;
            varA += da * da;
            varB += db * db;
        }
        double denominator = Math.sqrt(varA * varB);
        return denominator < EPSILON ? 0.0 : cov / denominator;
    }

    private static double entropy(double[] values) {
        int[] histogram = new int[ENTROPY_BINS];
        int total = 0;
        for (double value : values) {
            if (value < 1.0E-4) {
                continue;
            }
            int bin = (int) Math.min(ENTROPY_BINS - 1, Math.max(0, Math.log10(value / 0.001) * 1.6));
            histogram[bin]++;
            total++;
        }
        if (total < 8) {
            return 0.0;
        }
        double result = 0.0;
        for (int count : histogram) {
            if (count == 0) {
                continue;
            }
            double p = (double) count / total;
            result -= p * Math.log(p);
        }
        return result / Math.log(ENTROPY_BINS);
    }

    private static double squash(double value, double scale) {
        double normalized = Math.abs(value) / scale;
        return normalized / (1.0 + normalized);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
