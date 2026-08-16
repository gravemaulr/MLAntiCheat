package com.wnteam.mlanticheat.data;

import com.wnteam.mlanticheat.ml.MLScores;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerData {
    public static final int PRECISION = 0, DYNAMICS = 1, PATTERN = 2, TRACKING = 3, COMBINED = 4, SCORE_COUNT = 5;
    public static final String[] SCORE_NAMES = {"PREC", "DYN", "PAT", "TRK", "ML"};
    private static final int ROTATION_CAPACITY = 160, ATTACK_CAPACITY = 40, HISTORY_CAPACITY = 30;

    public record RotationSample(float deltaYaw, float deltaPitch, long time, long tick) {}
    public record AttackSample(long time, double angle, double reach, boolean blocked, String target) {}
    public record Detection(long time, String rule, double rawScore, double score, int ping, double tps) {}
    private record Reaction(long time, long value) {}

    private final UUID uuid;
    private final double[] scores = new double[SCORE_COUNT];
    private final Deque<RotationSample> rotations = new ArrayDeque<>();
    private final Deque<AttackSample> attackLog = new ArrayDeque<>();
    private final Deque<Long> attacks = new ArrayDeque<>();
    private final Deque<Double> targetAngles = new ArrayDeque<>();
    private final Deque<Double> postHitYaw = new ArrayDeque<>();
    private final Deque<Reaction> reactionTimes = new ArrayDeque<>();
    private final Deque<Detection> detections = new ArrayDeque<>();
    private final Map<String, Integer> ruleConfirmations = new ConcurrentHashMap<>();
    private final Map<String, Long> ruleCooldowns = new ConcurrentHashMap<>();
    private long lastAttack, lastEvaluation, lastEnemyVisible, syntheticTick, analyses, alerts, lastSeen;
    private boolean packetRotations;
    private double combinedSum, combinedMax, rawScore, lastTps = 20.0, lastReach = -1.0;
    private int lastPing;

    public PlayerData(UUID uuid) { this.uuid = uuid; }
    public UUID getUuid() { return uuid; }
    public synchronized void setPacketRotations(boolean value) { packetRotations = value; }
    public synchronized boolean isPacketRotations() { return packetRotations; }
    public synchronized void addRotation(float yaw, float pitch) { addRotation(yaw, pitch, ++syntheticTick); }
    public synchronized void addRotation(float yaw, float pitch, long tick) {
        long now = System.currentTimeMillis(); lastSeen = now;
        rotations.addLast(new RotationSample(yaw, pitch, now, tick));
        while (rotations.size() > ROTATION_CAPACITY) rotations.pollFirst();
        if (lastAttack > 0 && now - lastAttack <= 160) {
            postHitYaw.addLast((double) Math.abs(yaw));
            while (postHitYaw.size() > ATTACK_CAPACITY) postHitYaw.pollFirst();
        }
    }
    public synchronized List<RotationSample> rotationSnapshot() { return new ArrayList<>(rotations); }
    public synchronized List<AttackSample> attackSnapshot() { return new ArrayList<>(attackLog); }
    public synchronized List<Detection> detectionSnapshot() { return new ArrayList<>(detections); }
    public synchronized int rotationCount() { return rotations.size(); }
    public synchronized void recordAttack(double angle, UUID target, double reach, boolean blocked, String targetName) {
        long now = System.currentTimeMillis(); lastSeen = now;
        if (lastEnemyVisible > 0) { reactionTimes.addLast(new Reaction(now, now - lastEnemyVisible)); while (reactionTimes.size() > ATTACK_CAPACITY) reactionTimes.pollFirst(); }
        lastAttack = now; lastReach = reach; attacks.addLast(now); targetAngles.addLast(angle);
        attackLog.addLast(new AttackSample(now, angle, reach, blocked, targetName == null ? "unknown" : targetName));
        while (attacks.size() > ATTACK_CAPACITY) attacks.pollFirst();
        while (targetAngles.size() > ATTACK_CAPACITY) targetAngles.pollFirst();
        while (attackLog.size() > ATTACK_CAPACITY) attackLog.pollFirst();
    }
    public synchronized void recordAttack(double angle, UUID target) { recordAttack(angle, target, -1, false, null); }
    public synchronized void markEnemyVisible() { lastEnemyVisible = System.currentTimeMillis(); }
    public synchronized boolean inCombat(long window) { return lastAttack > 0 && System.currentTimeMillis() - lastAttack <= window; }
    public synchronized long getLastAttack() { return lastAttack; }
    public synchronized double getLastReach() { return lastReach; }
    public synchronized double attacksPerSecond() { if (attacks.size() < 3) return 0; long span = attacks.peekLast() - attacks.peekFirst(); return span <= 0 ? 0 : (attacks.size() - 1) * 1000.0 / span; }
    public synchronized double[] attackIntervalStats() {
        if (attacks.size() < 4) return new double[]{0, 0, 0}; Long[] copy = attacks.toArray(new Long[0]); double[] delta = new double[copy.length - 1]; double sum = 0;
        for (int i = 1; i < copy.length; i++) { delta[i - 1] = copy[i] - copy[i - 1]; sum += delta[i - 1]; }
        double mean = sum / delta.length, variance = 0; for (double value : delta) variance += (value - mean) * (value - mean);
        return new double[]{mean, Math.sqrt(variance / delta.length), delta.length};
    }
    public synchronized double[] angleStats() { return meanStd(targetAngles); }
    public synchronized double[] postHitYawStats() { return meanStd(postHitYaw); }
    private double[] meanStd(Deque<Double> values) { if (values.isEmpty()) return new double[]{0, 0, 0}; double sum = 0; for (double value : values) sum += value; double mean = sum / values.size(), variance = 0; for (double value : values) variance += (value - mean) * (value - mean); return new double[]{mean, Math.sqrt(variance / values.size()), values.size()}; }
    public synchronized double minReactionTime() { if (reactionTimes.size() < 4) return -1; return reactionTimes.stream().mapToLong(Reaction::value).min().orElse(-1); }
    public synchronized boolean shouldEvaluate(long interval) { long now = System.currentTimeMillis(); if (now - lastEvaluation < interval) return false; lastEvaluation = now; return true; }
    public synchronized MLScores mlScores() { return new MLScores(scores[0], scores[1], scores[2], scores[3], scores[4]); }
    public synchronized void updateScores(MLScores next, double raw, int ping, double tps) { double[] values = next.values(); System.arraycopy(values, 0, scores, 0, SCORE_COUNT); rawScore = raw; lastPing = ping; lastTps = tps; lastSeen = System.currentTimeMillis(); analyses++; combinedSum += scores[COMBINED]; combinedMax = Math.max(combinedMax, scores[COMBINED]); }
    public synchronized double[] snapshotScores() { return scores.clone(); }
    public synchronized double getScore(int index) { return scores[index]; }
    public synchronized double getLastPrediction() { return scores[COMBINED]; }
    public synchronized double getRawScore() { return rawScore; }
    public synchronized int getLastPing() { return lastPing; }
    public synchronized double getLastTps() { return lastTps; }
    public synchronized long getLastSeen() { return lastSeen; }
    public synchronized long getAnalyses() { return analyses; }
    public synchronized long getAlerts() { return alerts; }
    public synchronized void recordAlert() { alerts++; }
    public synchronized void recordDetection(String rule) { detections.addFirst(new Detection(System.currentTimeMillis(), rule, rawScore, scores[COMBINED], lastPing, lastTps)); while (detections.size() > HISTORY_CAPACITY) detections.pollLast(); }
    public synchronized double getCombinedAverage() { return analyses == 0 ? 0 : combinedSum / analyses; }
    public synchronized double getCombinedMax() { return combinedMax; }
    public int updateRule(String rule, boolean suspicious) { return ruleConfirmations.compute(rule, (key, value) -> suspicious ? (value == null ? 1 : value + 1) : Math.max(0, value == null ? 0 : value - 1)); }
    public int getConfirmations() { return ruleConfirmations.values().stream().mapToInt(Integer::intValue).max().orElse(0); }
    public boolean canRunRule(String rule, long cooldown) { long now = System.currentTimeMillis(); Long previous = ruleCooldowns.get(rule); if (previous != null && now - previous < cooldown) return false; ruleCooldowns.put(rule, now); return true; }
    public synchronized void clearScores() { for (int i = 0; i < scores.length; i++) scores[i] = 0; ruleConfirmations.clear(); }
    public synchronized void reset() { rotations.clear(); attacks.clear(); attackLog.clear(); targetAngles.clear(); postHitYaw.clear(); reactionTimes.clear(); clearScores(); }
    public synchronized void decay(double factor) { if (!inCombat(4000)) for (int i = 0; i < scores.length; i++) scores[i] *= factor; }
}
