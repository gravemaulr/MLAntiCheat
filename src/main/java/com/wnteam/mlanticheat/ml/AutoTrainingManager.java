package com.wnteam.mlanticheat.ml;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoTrainingManager {

    private static final int FEATURE_COUNT = FeatureExtractor.FEATURE_COUNT;
    private static final int ROW_SIZE = FEATURE_COUNT + 3;
    private static final int CONFIRMED_CLEAN = -1;
    private static final int UNKNOWN = 0;
    private static final int CONFIRMED_CHEATER = 1;

    private final JavaPlugin plugin;
    private final TrainingManager trainingManager;
    private final File stateFile;
    private final Map<UUID, Subject> subjects = new ConcurrentHashMap<>();
    private long lastRunDay = -1L;

    public AutoTrainingManager(JavaPlugin plugin, EnsembleModel model, TrainingManager trainingManager) {
        this.plugin = plugin;
        this.trainingManager = trainingManager;
        this.stateFile = new File(plugin.getDataFolder(), "auto-training.yml");
        load();
    }

    public void observe(UUID uuid, double[] features, double heuristicScore, int violations, boolean combat) {
        if (!plugin.getConfig().getBoolean("auto-training.enabled", true) || features.length != FEATURE_COUNT) {
            return;
        }
        Subject subject = subjects.computeIfAbsent(uuid, ignored -> new Subject(System.currentTimeMillis()));
        synchronized (subject) {
            subject.lastSeen = System.currentTimeMillis();
            subject.samples.add(new Sample(features.clone(), Math.max(0.0, Math.min(1.0, heuristicScore)), violations, combat));
            int capacity = Math.max(32, plugin.getConfig().getInt("auto-training.samples-per-player", 160));
            while (subject.samples.size() > capacity) {
                subject.samples.remove(0);
            }
            subject.maxHeuristic = Math.max(subject.maxHeuristic, heuristicScore);
            subject.maxViolations = Math.max(subject.maxViolations, violations);
        }
    }

    public int applyVerdict(UUID uuid, boolean cheater, double weight) {
        Subject subject = subjects.get(uuid);
        if (subject == null) {
            return 0;
        }
        boolean combatOnly = cheater
                && plugin.getConfig().getBoolean("auto-training.combat-only-positives", true);
        List<double[]> batch = new ArrayList<>();
        synchronized (subject) {
            subject.confirmed = cheater ? CONFIRMED_CHEATER : CONFIRMED_CLEAN;
            for (Sample sample : subject.samples) {
                if (combatOnly && !sample.combat()) {
                    continue;
                }
                batch.add(sample.features());
            }
            subject.samples.clear();
            subject.maxHeuristic = 0.0;
            subject.maxViolations = 0;
            subject.firstSeen = System.currentTimeMillis();
        }
        double label = cheater ? 1.0 : 0.0;
        for (double[] features : batch) {
            trainingManager.trainAutomatic(features, label, weight);
        }
        return batch.size();
    }

    public boolean isTracked(UUID uuid) {
        return subjects.containsKey(uuid);
    }

    public int storedSamples(UUID uuid) {
        Subject subject = subjects.get(uuid);
        if (subject == null) {
            return 0;
        }
        synchronized (subject) {
            return subject.samples.size();
        }
    }

    public record Profile(int samples, int combatSamples, double maxHeuristic, int maxViolations,
                          long firstSeen, long lastSeen, String verdict) {
    }

    public Profile profileOf(UUID uuid) {
        Subject subject = subjects.get(uuid);
        if (subject == null) {
            return null;
        }
        synchronized (subject) {
            int combat = 0;
            for (Sample sample : subject.samples) {
                if (sample.combat()) {
                    combat++;
                }
            }
            String verdict = switch (subject.confirmed) {
                case CONFIRMED_CHEATER -> "cheater";
                case CONFIRMED_CLEAN -> "clean";
                default -> "unlabeled";
            };
            return new Profile(subject.samples.size(), combat, subject.maxHeuristic, subject.maxViolations,
                    subject.firstSeen, subject.lastSeen, verdict);
        }
    }

    public void runDaily() {
        if (!plugin.getConfig().getBoolean("auto-training.enabled", true)) {
            return;
        }
        long day = System.currentTimeMillis() / 86400000L;
        if (day == lastRunDay) {
            return;
        }
        lastRunDay = day;
        long now = System.currentTimeMillis();
        long quarantine = Math.max(1L, plugin.getConfig().getLong("auto-training.quarantine-days", 7L)) * 86400000L;
        long retention = Math.max(7L, plugin.getConfig().getLong("auto-training.retention-days", 30L)) * 86400000L;
        int minimumSamples = Math.max(16, plugin.getConfig().getInt("auto-training.minimum-samples", 48));
        double cleanLimit = plugin.getConfig().getDouble("auto-training.clean-max-heuristic", 0.20);
        int cleanViolations = plugin.getConfig().getInt("auto-training.clean-max-violations", 0);
        double banWeight = plugin.getConfig().getDouble("auto-training.ban-reason-weight", 0.7);
        double confirmedWeight = plugin.getConfig().getDouble("auto-training.confirmed-weight", 1.8);
        boolean combatOnlyPositives = plugin.getConfig().getBoolean("auto-training.combat-only-positives", true);

        List<Candidate> positives = new ArrayList<>();
        List<Candidate> negatives = new ArrayList<>();
        List<Subject> processed = new ArrayList<>();

        for (Map.Entry<UUID, Subject> entry : subjects.entrySet()) {
            Subject subject = entry.getValue();
            if (now - subject.lastSeen > retention) {
                subjects.remove(entry.getKey(), subject);
                continue;
            }
            synchronized (subject) {
                if (subject.samples.size() < minimumSamples) {
                    continue;
                }
                boolean confirmedCheater = subject.confirmed == CONFIRMED_CHEATER;
                boolean confirmedClean = subject.confirmed == CONFIRMED_CLEAN;
                if (subject.confirmed == UNKNOWN && now - subject.firstSeen < quarantine) {
                    continue;
                }
                if (confirmedCheater || (subject.confirmed == UNKNOWN && isCheatBan(entry.getKey()))) {
                    double weight = confirmedCheater ? confirmedWeight : banWeight;
                    for (Sample sample : subject.samples) {
                        if (combatOnlyPositives && !sample.combat()) {
                            continue;
                        }
                        positives.add(new Candidate(sample.features(), weight));
                    }
                    processed.add(subject);
                } else if (confirmedClean
                        || (subject.maxHeuristic <= cleanLimit && subject.maxViolations <= cleanViolations)) {
                    double weight = confirmedClean ? confirmedWeight : 1.0;
                    for (Sample sample : subject.samples) {
                        negatives.add(new Candidate(sample.features(), weight));
                    }
                    processed.add(subject);
                }
            }
        }

        int limit = Math.max(32, plugin.getConfig().getInt("auto-training.max-samples-per-class", 1200));
        Collections.shuffle(positives);
        Collections.shuffle(negatives);
        positives = new ArrayList<>(positives.subList(0, Math.min(limit, positives.size())));
        negatives = new ArrayList<>(negatives.subList(0, Math.min(limit, negatives.size())));
        int balanced = Math.min(positives.size(), negatives.size());
        int minimumClass = Math.max(16, plugin.getConfig().getInt("auto-training.minimum-class-size", 64));
        if (balanced >= minimumClass) {
            int epochs = Math.max(1, Math.min(4, plugin.getConfig().getInt("auto-training.epochs", 2)));
            for (int epoch = 0; epoch < epochs; epoch++) {
                Collections.shuffle(positives);
                Collections.shuffle(negatives);
                for (int i = 0; i < balanced; i++) {
                    Candidate positive = positives.get(i);
                    Candidate negative = negatives.get(i);
                    trainingManager.trainAutomatic(positive.features(), 1.0, positive.weight());
                    trainingManager.trainAutomatic(negative.features(), 0.0, negative.weight());
                }
            }
            for (Subject subject : processed) {
                synchronized (subject) {
                    subject.samples.clear();
                    subject.firstSeen = now;
                    subject.maxHeuristic = 0.0;
                    subject.maxViolations = 0;
                }
            }
        }
        save();
        plugin.getLogger().info("Automatic training cycle completed: candidates " + positives.size() + "/"
                + negatives.size() + ", balanced samples " + balanced + ".");
    }

    public void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("last-run-day", lastRunDay);
        for (Map.Entry<UUID, Subject> entry : subjects.entrySet()) {
            Subject subject = entry.getValue();
            synchronized (subject) {
                String path = "subjects." + entry.getKey();
                configuration.set(path + ".first-seen", subject.firstSeen);
                configuration.set(path + ".last-seen", subject.lastSeen);
                configuration.set(path + ".max-heuristic", subject.maxHeuristic);
                configuration.set(path + ".max-violations", subject.maxViolations);
                configuration.set(path + ".confirmed", subject.confirmed);
                List<List<Double>> samples = new ArrayList<>();
                for (Sample sample : subject.samples) {
                    List<Double> row = new ArrayList<>(ROW_SIZE);
                    for (double value : sample.features()) {
                        row.add(value);
                    }
                    row.add(sample.heuristic());
                    row.add((double) sample.violations());
                    row.add(sample.combat() ? 1.0 : 0.0);
                    samples.add(row);
                }
                configuration.set(path + ".samples", samples);
            }
        }
        try {
            if (!stateFile.getParentFile().exists() && !stateFile.getParentFile().mkdirs()) {
                plugin.getLogger().warning("Unable to create automatic training directory");
            }
            File temporaryFile = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
            configuration.save(temporaryFile);
            if (stateFile.exists() && !stateFile.delete()) {
                plugin.getLogger().warning("Unable to replace automatic training state file");
                return;
            }
            if (!temporaryFile.renameTo(stateFile)) {
                plugin.getLogger().warning("Unable to commit automatic training state file");
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save automatic training state: " + exception.getMessage());
        }
    }

    private void load() {
        if (!stateFile.exists()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(stateFile);
        lastRunDay = configuration.getLong("last-run-day", -1L);
        ConfigurationSection section = configuration.getConfigurationSection("subjects");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "subjects." + key;
                Subject subject = new Subject(configuration.getLong(path + ".first-seen", System.currentTimeMillis()));
                subject.lastSeen = configuration.getLong(path + ".last-seen", subject.firstSeen);
                subject.maxHeuristic = configuration.getDouble(path + ".max-heuristic", 0.0);
                subject.maxViolations = configuration.getInt(path + ".max-violations", 0);
                subject.confirmed = configuration.getInt(path + ".confirmed", UNKNOWN);
                for (Object element : configuration.getList(path + ".samples", List.of())) {
                    if (!(element instanceof List<?> row) || row.size() != ROW_SIZE) {
                        continue;
                    }
                    double[] features = new double[FEATURE_COUNT];
                    boolean valid = true;
                    for (int i = 0; i < FEATURE_COUNT; i++) {
                        if (!(row.get(i) instanceof Number number)) {
                            valid = false;
                            break;
                        }
                        features[i] = number.doubleValue();
                    }
                    if (!valid
                            || !(row.get(FEATURE_COUNT) instanceof Number heuristic)
                            || !(row.get(FEATURE_COUNT + 1) instanceof Number violations)
                            || !(row.get(FEATURE_COUNT + 2) instanceof Number combat)) {
                        continue;
                    }
                    subject.samples.add(new Sample(features, heuristic.doubleValue(), violations.intValue(),
                            combat.doubleValue() >= 0.5));
                }
                subjects.put(uuid, subject);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public int trackedSubjects() {
        return subjects.size();
    }

    private boolean isCheatBan(UUID uuid) {
        ProfileBanList banList = Bukkit.getBanList(BanList.Type.PROFILE);
        var entry = banList.getBanEntry(Bukkit.createPlayerProfile(uuid));
        if (entry == null || entry.getReason() == null) {
            return false;
        }
        String reason = entry.getReason().toLowerCase(Locale.ROOT);
        return reason.contains("cheat") || reason.contains("killaura") || reason.contains("aim")
                || reason.contains("anticheat") || reason.contains("anti-cheat") || reason.contains("hack");
    }

    private static final class Subject {
        private long firstSeen;
        private long lastSeen;
        private double maxHeuristic;
        private int maxViolations;
        private int confirmed = UNKNOWN;
        private final List<Sample> samples = new ArrayList<>();

        private Subject(long firstSeen) {
            this.firstSeen = firstSeen;
            this.lastSeen = firstSeen;
        }
    }

    private record Sample(double[] features, double heuristic, int violations, boolean combat) {
    }

    private record Candidate(double[] features, double weight) {
    }
}
