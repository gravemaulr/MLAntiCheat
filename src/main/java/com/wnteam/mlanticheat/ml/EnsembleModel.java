package com.wnteam.mlanticheat.ml;

import java.util.Random;

public final class EnsembleModel {

    private final int dimension;
    private final Standardizer standardizer;
    private final LogisticModel logistic;
    private final NeuralModel neural;
    private final Random random = new Random();

    private long positiveSamples;
    private long negativeSamples;
    private long minPositive = 400;
    private long minNegative = 400;
    private double logisticLoss = 0.693;
    private double neuralLoss = 0.693;
    private double holdoutRatio = 0.15;
    private long truePositives;
    private long falsePositives;
    private long trueNegatives;
    private long falseNegatives;
    private double decisionThreshold = 0.5;

    public EnsembleModel(int dimension, int hidden, double learningRate, double l2) {
        this.dimension = dimension;
        this.standardizer = new Standardizer(dimension);
        this.logistic = new LogisticModel(dimension, learningRate, l2);
        this.neural = new NeuralModel(dimension, hidden, learningRate, l2);
    }

    public void setThresholds(long minPositive, long minNegative) {
        this.minPositive = Math.max(1, minPositive);
        this.minNegative = Math.max(1, minNegative);
    }

    public void setHyperParameters(double learningRate, double l2) {
        logistic.setLearningRate(learningRate);
        logistic.setL2(l2);
        neural.setLearningRate(learningRate);
        neural.setL2(l2);
    }

    public void setHoldoutRatio(double value) {
        this.holdoutRatio = Math.max(0.0, Math.min(0.4, value));
    }

    public void setDecisionThreshold(double value) {
        this.decisionThreshold = Math.max(0.05, Math.min(0.95, value));
    }

    public boolean isReady() {
        return positiveSamples >= minPositive && negativeSamples >= minNegative;
    }

    public double predict(double[] features) {
        if (!isReady() || features.length != dimension) {
            return 0.0;
        }
        double[] z = standardizer.transform(features);
        double a = logistic.predict(z);
        double b = neural.predict(z);
        double weightA = 1.0 / Math.max(1.0E-3, logisticLoss);
        double weightB = 1.0 / Math.max(1.0E-3, neuralLoss);
        return (a * weightA + b * weightB) / (weightA + weightB);
    }

    public double rawPredict(double[] features) {
        if (features.length != dimension) {
            return 0.0;
        }
        double[] z = standardizer.transform(features);
        return (logistic.predict(z) + neural.predict(z)) * 0.5;
    }

    public void train(double[] features, double label) {
        train(features, label, 1.0);
    }

    public synchronized void train(double[] features, double label, double extraWeight) {
        if (features.length != dimension) {
            return;
        }
        if (isReady() && holdoutRatio > 0.0 && random.nextDouble() < holdoutRatio) {
            evaluateHoldout(features, label);
            return;
        }
        apply(features, label, extraWeight);
    }

    public synchronized void trainPair(double[] positive, double positiveWeight,
                                       double[] negative, double negativeWeight) {
        if (positive.length != dimension || negative.length != dimension) {
            return;
        }
        if (isReady() && holdoutRatio > 0.0 && random.nextDouble() < holdoutRatio) {
            evaluateHoldout(positive, 1.0);
            evaluateHoldout(negative, 0.0);
            return;
        }
        apply(positive, 1.0, positiveWeight);
        apply(negative, 0.0, negativeWeight);
    }

    private void apply(double[] features, double label, double extraWeight) {
        standardizer.update(features);
        double[] z = standardizer.transform(features);
        if (label >= 0.5) {
            positiveSamples++;
        } else {
            negativeSamples++;
        }
        double weight = Math.max(0.1, Math.min(8.0, extraWeight));
        double logisticPrediction = logistic.predict(z);
        double neuralPrediction = neural.predict(z);
        logisticLoss = logisticLoss * 0.999 + crossEntropy(logisticPrediction, label) * 0.001;
        neuralLoss = neuralLoss * 0.999 + crossEntropy(neuralPrediction, label) * 0.001;
        logistic.train(z, label, weight);
        neural.train(z, label, weight);
    }

    private void evaluateHoldout(double[] features, double label) {
        double prediction = predict(features);
        boolean positive = prediction >= decisionThreshold;
        if (label >= 0.5) {
            if (positive) {
                truePositives++;
            } else {
                falseNegatives++;
            }
        } else {
            if (positive) {
                falsePositives++;
            } else {
                trueNegatives++;
            }
        }
    }

    private double crossEntropy(double prediction, double label) {
        double p = Math.max(1.0E-6, Math.min(1.0 - 1.0E-6, prediction));
        return -(label * Math.log(p) + (1.0 - label) * Math.log(1.0 - p));
    }

    public synchronized double precision() {
        long denominator = truePositives + falsePositives;
        return denominator == 0 ? 0.0 : (double) truePositives / denominator;
    }

    public synchronized double recall() {
        long denominator = truePositives + falseNegatives;
        return denominator == 0 ? 0.0 : (double) truePositives / denominator;
    }

    public synchronized double falsePositiveRate() {
        long denominator = falsePositives + trueNegatives;
        return denominator == 0 ? 0.0 : (double) falsePositives / denominator;
    }

    public synchronized long holdoutSamples() {
        return truePositives + falsePositives + trueNegatives + falseNegatives;
    }

    public synchronized void resetMetrics() {
        truePositives = 0;
        falsePositives = 0;
        trueNegatives = 0;
        falseNegatives = 0;
    }

    public synchronized void reset() {
        standardizer.reset();
        logistic.reset();
        neural.reset();
        positiveSamples = 0;
        negativeSamples = 0;
        logisticLoss = 0.693;
        neuralLoss = 0.693;
        truePositives = 0;
        falsePositives = 0;
        trueNegatives = 0;
        falseNegatives = 0;
    }

    public synchronized long balancedSamples() {
        return Math.min(positiveSamples, negativeSamples) * 2L;
    }

    public synchronized double balanceRatio() {
        long total = positiveSamples + negativeSamples;
        return total == 0 ? 0.0 : (double) positiveSamples / total;
    }

    public long getTrainedSamples() {
        return positiveSamples + negativeSamples;
    }

    public long getPositiveSamples() {
        return positiveSamples;
    }

    public long getNegativeSamples() {
        return negativeSamples;
    }

    public double getLogisticLoss() {
        return logisticLoss;
    }

    public double getNeuralLoss() {
        return neuralLoss;
    }

    public int dimension() {
        return dimension;
    }

    public Standardizer getStandardizer() {
        return standardizer;
    }

    public LogisticModel getLogistic() {
        return logistic;
    }

    public NeuralModel getNeural() {
        return neural;
    }

    public synchronized void restoreCounters(long positive, long negative, double logisticLoss, double neuralLoss) {
        this.positiveSamples = positive;
        this.negativeSamples = negative;
        this.logisticLoss = logisticLoss;
        this.neuralLoss = neuralLoss;
    }

    public synchronized void restoreMetrics(long truePositives, long falsePositives, long trueNegatives, long falseNegatives) {
        this.truePositives = truePositives;
        this.falsePositives = falsePositives;
        this.trueNegatives = trueNegatives;
        this.falseNegatives = falseNegatives;
    }

    public synchronized long getTruePositives() {
        return truePositives;
    }

    public synchronized long getFalsePositives() {
        return falsePositives;
    }

    public synchronized long getTrueNegatives() {
        return trueNegatives;
    }

    public synchronized long getFalseNegatives() {
        return falseNegatives;
    }
}
