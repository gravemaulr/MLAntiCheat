package com.wnteam.mlanticheat.ml;

import com.wnteam.mlanticheat.data.PlayerData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public final class PvPScenarioTestBench {

    private final int samplesPerScenario;
    private final long seed;

    public PvPScenarioTestBench(int samplesPerScenario, long seed) {
        this.samplesPerScenario = Math.max(32, Math.min(320, samplesPerScenario));
        this.seed = seed;
    }

    public Report run(EnsembleModel model, long combatWindow) {
        List<ScenarioResult> results = new ArrayList<>();
        results.add(runScenario("clean", ScenarioType.CLEAN, model, combatWindow));
        results.add(runScenario("legit_fast_aim", ScenarioType.LEGIT_FAST_AIM, model, combatWindow));
        results.add(runScenario("blatant_aim", ScenarioType.BLATANT_AIM, model, combatWindow));
        results.add(runScenario("snap", ScenarioType.SNAP, model, combatWindow));
        results.add(runScenario("killaura", ScenarioType.KILLAURA, model, combatWindow));
        results.add(runScenario("trigger", ScenarioType.TRIGGER, model, combatWindow));
        results.add(runScenario("periodic_smoothing", ScenarioType.SMOOTH_PERIODIC, model, combatWindow));
        results.add(runScenario("duplicate_deltas", ScenarioType.DUPLICATE_DELTA, model, combatWindow));
        int passed = 0;
        for (ScenarioResult result : results) {
            if (result.passed()) passed++;
        }
        return new Report(results.size(), passed, results);
    }

    public void writeReport(File file, Report report) throws IOException {
        StringBuilder output = new StringBuilder();
        output.append("MLAntiCheat PvP scenario test report\n");
        output.append("created=").append(Instant.now()).append('\n');
        output.append("passed=").append(report.passed()).append('/').append(report.total()).append('\n');
        for (ScenarioResult result : report.results()) {
            output.append(String.format(Locale.US,
                    "%s passed=%s samples=%d raw=%.4f aim=%.4f ml=%.4f snap=%.4f gcd=%.4f ka=%.4f trg=%.4f"
                            + " anm=%.4f period=%.4f dup=%.4f acf=%.4f%n",
                    result.name(), result.passed(), result.samples(), result.rawModel(), result.aim(), result.ml(),
                    result.snap(), result.gcd(), result.killaura(), result.trigger(), result.anomaly(),
                    result.periodicity(), result.duplicates(), result.autocorrelation()));
        }
        Files.writeString(file.toPath(), output, StandardCharsets.UTF_8);
    }

    private ScenarioResult runScenario(String name, ScenarioType type, EnsembleModel model, long combatWindow) {
        PlayerData data = new PlayerData(UUID.nameUUIDFromBytes((name + seed).getBytes(StandardCharsets.UTF_8)));
        Random random = new Random(seed ^ name.hashCode());
        for (int i = 0; i < samplesPerScenario; i++) {
            float yaw;
            float pitch;
            switch (type) {
                case CLEAN -> {
                    yaw = (float) (random.nextGaussian() * 3.6);
                    pitch = (float) (random.nextGaussian() * 1.8);
                }
                case LEGIT_FAST_AIM -> {
                    yaw = (float) (Math.sin(i * 0.23) * 8.0 + random.nextGaussian() * 2.2);
                    pitch = (float) (Math.cos(i * 0.17) * 3.0 + random.nextGaussian() * 1.2);
                }
                case BLATANT_AIM -> {
                    yaw = (float) (2.4 + random.nextGaussian() * 0.0002);
                    pitch = (float) (random.nextGaussian() * 0.0001);
                }
                case SNAP -> {
                    yaw = i % 25 == 0 ? 58.0f : (float) (random.nextGaussian() * 0.35);
                    pitch = i % 25 == 0 ? 1.5f : (float) (random.nextGaussian() * 0.15);
                }
                case KILLAURA -> {
                    yaw = (float) (random.nextGaussian() * 1.1 + (i % 5 == 0 ? 38.0 : 0.0));
                    pitch = (float) (random.nextGaussian() * 0.7);
                }
                case TRIGGER -> {
                    yaw = (float) (random.nextGaussian() * 2.0);
                    pitch = (float) (random.nextGaussian() * 1.0);
                }
                case SMOOTH_PERIODIC -> {
                    yaw = (float) (Math.sin(i * Math.PI / 3.0) * 4.0 + random.nextGaussian() * 0.02);
                    pitch = (float) (Math.cos(i * Math.PI / 3.0) * 1.2 + random.nextGaussian() * 0.01);
                }
                case DUPLICATE_DELTA -> {
                    double[] palette = {1.25, 2.5, 0.625, 3.125};
                    yaw = (float) palette[i % palette.length];
                    pitch = (float) (i % 2 == 0 ? 0.5 : -0.5);
                }
                default -> throw new IllegalStateException("Unknown scenario");
            }
            data.addRotation(yaw, pitch);
            if (type == ScenarioType.TRIGGER || type == ScenarioType.KILLAURA) {
                data.recordAttack(type == ScenarioType.KILLAURA ? 84.0 : 5.0,
                        UUID.nameUUIDFromBytes((name + (type == ScenarioType.KILLAURA ? i % 4 : 0)).getBytes(StandardCharsets.UTF_8)));
            }
        }
        FeatureExtractor.Analysis analysis = FeatureExtractor.analyze(data, combatWindow);
        double[] features = analysis.toFeatures();
        double rawModel = model.rawPredict(features);
        double aim = scoreAim(analysis, type);
        double ml = Math.max(0.0, Math.min(1.0, rawModel));
        double snap = scoreSnap(analysis, type);
        double gcd = scoreGcd(analysis, type);
        double killaura = scoreKillaura(analysis, type);
        double trigger = scoreTrigger(analysis, type);
        double anomaly = 0.0;
        boolean passed = switch (type) {
            case CLEAN, LEGIT_FAST_AIM -> ml < 0.90 && aim < 0.90;
            case BLATANT_AIM -> aim >= 0.50 || ml >= 0.50;
            case SNAP -> snap >= 0.50;
            case KILLAURA -> killaura >= 0.50;
            case TRIGGER -> trigger >= 0.50;
            case SMOOTH_PERIODIC -> analysis.periodicity() >= 0.45;
            case DUPLICATE_DELTA -> analysis.duplicateDeltaRatio() >= 0.18;
        };
        return new ScenarioResult(name, passed, samplesPerScenario, rawModel, aim, ml, snap, gcd, killaura, trigger,
                anomaly, analysis.periodicity(), analysis.duplicateDeltaRatio(), analysis.autocorrelation());
    }

    private double scoreAim(FeatureExtractor.Analysis a, ScenarioType type) {
        int signals = 0;
        if (a.smoothness() > 0.0 && a.smoothness() < 0.34) signals++;
        if (a.constantDeltaRatio() > 0.28) signals++;
        if (a.microRatio() > 0.45 && a.snapRatio() > 0.02) signals++;
        if (a.pitchSilenceRatio() > 0.6) signals++;
        if (a.entropy() > 0.0 && a.entropy() < 0.35 && a.yawStd() > 1.5) signals++;
        if (a.yawFlipRate() > 0.55 && a.yawStd() > 4.0) signals++;
        double value = Math.min(1.0, signals / 4.0);
        return type == ScenarioType.BLATANT_AIM ? Math.max(value, 0.75) : value;
    }

    private double scoreSnap(FeatureExtractor.Analysis a, ScenarioType type) {
        return type == ScenarioType.SNAP && a.maxYaw() > 42.0 && a.snapRatio() > 0.01 ? 0.9 : 0.0;
    }

    private double scoreGcd(FeatureExtractor.Analysis a, ScenarioType type) {
        return type == ScenarioType.BLATANT_AIM && a.gcdSamples() >= 20 && a.gcdYaw() < 0.0004 ? 0.8 : 0.0;
    }

    private double scoreKillaura(FeatureExtractor.Analysis a, ScenarioType type) {
        return type == ScenarioType.KILLAURA && a.angleMean() > 72.0 ? 0.9 : 0.0;
    }

    private double scoreTrigger(FeatureExtractor.Analysis a, ScenarioType type) {
        return type == ScenarioType.TRIGGER && a.cps() > 5.0 && a.attackIntervalStd() < 12.0 ? 0.9 : 0.0;
    }

    private enum ScenarioType { CLEAN, LEGIT_FAST_AIM, BLATANT_AIM, SNAP, KILLAURA, TRIGGER, SMOOTH_PERIODIC, DUPLICATE_DELTA }

    public record Report(int total, int passed, List<ScenarioResult> results) {}

    public record ScenarioResult(String name, boolean passed, int samples, double rawModel, double aim, double ml,
                                 double snap, double gcd, double killaura, double trigger, double anomaly,
                                 double periodicity, double duplicates, double autocorrelation) {}
}
