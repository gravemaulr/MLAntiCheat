package com.wnteam.mlanticheat.ml;

import java.util.Arrays;

public record MLScores(double precision, double dynamics, double pattern, double tracking, double combined) {

    private static final int[][] GROUPS = {
            {26, 27, 29, 36, 0, 2},
            {4, 5, 6, 7, 8, 9, 21, 22},
            {10, 11, 12, 18, 32, 33, 34},
            {13, 14, 15, 16, 23, 24, 25, 28, 30}
    };

    public static MLScores evaluate(EnsembleModel model, double[] features) {
        double shared = clamp(model.predict(features));
        double[] heads = new double[4];
        for (int i = 0; i < heads.length; i++) {
            double context = groupMean(features, GROUPS[i]);
            double crossContext = groupMean(features, GROUPS[(i + 1) % GROUPS.length]);
            heads[i] = clamp(shared * 0.72 + context * 0.20 + crossContext * 0.08);
        }
        double combined = Arrays.stream(heads).average().orElse(0.0);
        return new MLScores(heads[0], heads[1], heads[2], heads[3], combined);
    }

    public MLScores smooth(MLScores previous, double factor) {
        double keep = clamp(factor);
        double next = 1.0 - keep;
        double p = previous.precision * keep + precision * next;
        double d = previous.dynamics * keep + dynamics * next;
        double a = previous.pattern * keep + pattern * next;
        double t = previous.tracking * keep + tracking * next;
        return new MLScores(p, d, a, t, (p + d + a + t) / 4.0);
    }

    public double[] values() {
        return new double[]{precision, dynamics, pattern, tracking, combined};
    }

    private static double groupMean(double[] features, int[] indexes) {
        double sum = 0.0;
        int used = 0;
        for (int index : indexes) {
            if (index >= 0 && index < features.length && Double.isFinite(features[index])) {
                sum += clamp(features[index]);
                used++;
            }
        }
        return used == 0 ? 0.0 : sum / used;
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
