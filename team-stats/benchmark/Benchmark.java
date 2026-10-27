import com.example.csvwatcher.watcher.FileEventKey;
import com.example.csvwatcher.watcher.FileEventValue;
import com.fernandez.basketball.persistence.PersistedEventValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Opt-in benchmark; never uses the development database or development Kafka. */
public class Benchmark {
    static final String BOOTSTRAP = "localhost:19092";
    static final String REGISTRY = "http://localhost:18081";
    static final Path PROJECT = Path.of("").toAbsolutePath();
    static final Path WORKSPACE = PROJECT.getParent();
    static final Path OUT = PROJECT.resolve("benchmark/results");
    static final Path DATA = PROJECT.resolve("target/benchmark-data");
    static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    static final ObjectMapper JSON = new ObjectMapper();
    static final Map<Integer, String> EXPECTED = new HashMap<>();
    static final List<Process> CHILDREN = new ArrayList<>();
    static final Map<Integer, Path> INPUTS = new LinkedHashMap<>();
    static final String RUN = Long.toString(System.currentTimeMillis());
    static int trial = 1;
    static Admin admin;
    static KafkaProducer<Object, Object> producer;
    static KafkaConsumer<Object, Object> outcomes;

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT); Files.createDirectories(DATA);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> CHILDREN.forEach(Process::destroy)));
        prepareInputs(Path.of(args[0]));
        boolean resumeKafka = args.length > 1 && args[1].equals("kafka-only");
        if (!resumeKafka) {
            try (Connection db = connect("postgres"); var statement = db.createStatement()) {
                for (String name : List.of("direct", "batch", "spring", "quarkus")) {
                    statement.execute("CREATE DATABASE bench_" + name);
                }
            }
        } else {
            for (String line : Files.readAllLines(OUT.resolve("samples.jsonl"))) {
                var sample = JSON.readTree(line);
                EXPECTED.put(sample.get("rows").asInt(), sample.get("fingerprint").asText());
            }
        }
        try {
            // One non-measured warm-up establishes schemas. Every measured load starts with an empty business table.
            if (!resumeKafka) {
                direct("setup", 75, 0);
                for (int rows : INPUTS.keySet()) for (int n = 1; n <= 3; n++) direct("fresh-process", rows, n);
            }
            kafkaSetup();
            for (String mode : List.of("quarkus", "batch", "spring")) {
                for (int n = 1; n <= 3; n++) {
                    trial = n;
                    var groups = groups(mode);
                    truncate(mode);
                    long start = System.nanoTime();
                    var workers = launch(mode, n);
                    try {
                        awaitReady(groups, workers);
                        double startup = elapsed(start);
                        var first = load(mode, "fresh-workers", 75, n, groups, workers);
                        first.put("workersStartupMs", startup);
                        first.put("startupPlusLoadMs", startup + ((Number)first.get("elapsedMs")).doubleValue());
                        save(first);
                        if (n == 3) {
                            // Exercise all paths with the same largest input before warm measurements.
                            load(mode, "warmup", 75000, 0, groups, workers);
                            for (int rows : INPUTS.keySet()) for (int repeat = 1; repeat <= 3; repeat++) {
                                save(load(mode, "warm-workers", rows, repeat, groups, workers));
                            }
                        }
                    } finally { for (var worker : workers) stop(worker); }
                }
            }
        } finally {
            CHILDREN.forEach(Benchmark::stop);
            if (outcomes != null) outcomes.close(Duration.ofSeconds(5));
            if (producer != null) producer.close(Duration.ofSeconds(5));
            if (admin != null) admin.close(Duration.ofSeconds(5));
        }
        System.out.println("BENCHMARK COMPLETE: " + OUT.resolve("samples.jsonl"));
    }

    static void prepareInputs(Path original) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        List<String> header;
        try (var reader = Files.newBufferedReader(original);
             var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            header = parser.getHeaderNames();
            for (var row : parser) rows.add(row.toList());
        }
        if (rows.size() != 75) throw new IllegalStateException("Expected 75 real source rows, got " + rows.size());
        for (int copies : List.of(1, 100, 1000)) {
            int count = rows.size() * copies;
            var dir = Files.createDirectories(DATA.resolve(Integer.toString(count)));
            var path = dir.resolve("stats_all_periods.csv");
            try (var writer = Files.newBufferedWriter(path); var csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                csv.printRecord(header);
                for (int copy = 0; copy < copies; copy++) for (var row : rows) {
                    var values = new ArrayList<>(row);
                    if (copies > 1) values.set(0, values.getFirst() + "-" + copy);
                    csv.printRecord(values);
                }
            }
            INPUTS.put(count, path);
        }
    }

    static void kafkaSetup() throws Exception {
        admin = Admin.create(Map.of("bootstrap.servers", BOOTSTRAP));
        var topics = new ArrayList<NewTopic>();
        for (trial = 1; trial <= 3; trial++)
            for (String mode : List.of("batch", "spring", "quarkus"))
                for (String suffix : List.of("files", "parsed", "success", "rejected", "dlt"))
                    topics.add(new NewTopic(topic(mode, suffix), 1, (short)1));
        admin.createTopics(topics).all().get(30, TimeUnit.SECONDS);
        var pp = new Properties();
        pp.put("bootstrap.servers", BOOTSTRAP); pp.put("schema.registry.url", REGISTRY);
        pp.put("key.serializer", KafkaAvroSerializer.class); pp.put("value.serializer", KafkaAvroSerializer.class);
        pp.put("acks", "all"); pp.put("client.id", "benchmark-driver");
        producer = new KafkaProducer<>(pp);
        var cp = new Properties();
        cp.put("bootstrap.servers", BOOTSTRAP); cp.put("schema.registry.url", REGISTRY);
        cp.put("key.deserializer", KafkaAvroDeserializer.class); cp.put("value.deserializer", KafkaAvroDeserializer.class);
        cp.put("specific.avro.reader", true); cp.put("group.id", "benchmark-observer-" + RUN);
        cp.put("auto.offset.reset", "earliest"); cp.put("enable.auto.commit", false);
        outcomes = new KafkaConsumer<>(cp);
        var partitions = new ArrayList<TopicPartition>();
        for (trial = 1; trial <= 3; trial++)
            for (String mode : List.of("batch", "spring", "quarkus")) partitions.add(new TopicPartition(topic(mode, "success"), 0));
        outcomes.assign(partitions); outcomes.seekToBeginning(partitions);
        // Establish driver connection outside measured app latency.
        outcomes.poll(Duration.ofMillis(100));
        for (trial = 1; trial <= 3; trial++)
            for (String mode : List.of("batch", "spring", "quarkus")) producer.partitionsFor(topic(mode, "files"));
        trial = 1;
    }

    static List<String> groups(String mode) {
        return mode.equals("batch") ? List.of("bench-batch-" + RUN + "-" + trial)
                : List.of("bench-parser-" + mode + "-" + RUN + "-" + trial, "bench-" + mode + "-" + RUN + "-" + trial);
    }

    static List<Process> launch(String mode, int n) throws Exception {
        var env = environment(mode);
        var workers = new ArrayList<Process>();
        if (mode.equals("batch")) {
            env.put("SPRING_PROFILES_ACTIVE", "kafka");
            env.put("KAFKA_CONSUMER_GROUP_ID", groups(mode).getFirst());
            workers.add(start(PROJECT.resolve("target/app.jar"), env, OUT.resolve(mode + "-" + n + ".log"), List.of()));
        } else {
            env.put("KAFKA_CONSUMER_GROUP_ID", groups(mode).get(1));
            Path jar = mode.equals("quarkus")
                    ? WORKSPACE.resolve("csv-team-stats-persistence-quarkus/target/quarkus-app/quarkus-run.jar")
                    : WORKSPACE.resolve("csv-team-stats-persistence/target/app.jar");
            workers.add(start(jar, env, OUT.resolve(mode + "-" + n + ".log"), List.of()));
            var parserEnv = new HashMap<>(env);
            parserEnv.put("KAFKA_CONSUMER_GROUP_ID", groups(mode).getFirst());
            workers.add(start(WORKSPACE.resolve("csv-team-stats-parser/target/app.jar"), parserEnv,
                    OUT.resolve(mode + "-parser-" + n + ".log"), List.of()));
        }
        return workers;
    }

    static Map<String, String> environment(String mode) {
        var env = new HashMap<String, String>();
        env.put("DB_URL", url("bench_" + mode)); env.put("DB_USER", "benchmark"); env.put("DB_PASS", "benchmark");
        env.put("CSV_ALLOWED_ROOT", DATA.toString()); env.put("SPRING_PROFILES_ACTIVE", "default");
        env.put("KAFKA_BOOTSTRAP_SERVERS", BOOTSTRAP); env.put("KAFKA_SCHEMA_REGISTRY_URL", REGISTRY);
        env.put("KAFKA_FILE_READY_TOPIC", topic(mode, "files")); env.put("KAFKA_PARSED_TEAM_STATS_TOPIC", topic(mode, "parsed"));
        env.put("KAFKA_TEAM_STATS_PERSISTED_SUCCESS_TOPIC", topic(mode, "success"));
        env.put("KAFKA_TEAM_STATS_REJECTED_TOPIC", topic(mode, "rejected"));
        env.put("KAFKA_TEAM_STATS_PERSISTENCE_DLT_TOPIC", topic(mode, "dlt"));
        env.put("KAFKA_TEAM_STATS_PARSER_DLT_TOPIC", topic(mode, "dlt"));
        env.put("KAFKA_TEAM_STATS_BATCH_DLT_TOPIC", topic(mode, "dlt"));
        env.put("KAFKA_MAX_POLL_RECORDS", "500"); env.put("BATCH_CHUNK_SIZE", "500");
        env.put("DB_POOL_MAX_SIZE", "4"); env.put("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "4");
        return env;
    }

    static Process start(Path jar, Map<String, String> env, Path log, List<String> args) throws IOException {
        var command = new ArrayList<>(List.of(JAVA, "-Xms64m", "-Xmx512m", "-jar", jar.toString()));
        command.addAll(args);
        var builder = new ProcessBuilder(command).directory(PROJECT.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().putAll(env);
        // Do not inherit framework or JVM overrides from the shell.
        for (String key : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "SPRING_APPLICATION_JSON")) builder.environment().remove(key);
        var process = builder.start(); CHILDREN.add(process); return process;
    }

    static void direct(String phase, int rows, int n) throws Exception {
        truncate("direct");
        String event = "direct-" + phase + "-" + rows + "-" + n + "-" + RUN;
        var log = OUT.resolve(event + ".log");
        long start = System.nanoTime();
        var process = start(PROJECT.resolve("target/app.jar"), environment("direct"), log,
                List.of("--file=" + INPUTS.get(rows), "--source-event-id=" + event));
        if (!process.waitFor(180, TimeUnit.SECONDS)) { stop(process); throw new IllegalStateException("CSV timeout: " + log); }
        double elapsed = elapsed(start);
        if (process.exitValue() != 0) throw new IllegalStateException("CSV failed: " + log);
        var sample = sample("direct", phase, rows, n, elapsed);
        String text = Files.readString(log);
        sample.put("importStepMs", duration(text, "Step: \\[importTeamStats\\] executed in ([0-9a-z.]+)"));
        sample.put("jobMs", duration(text, "status: \\[COMPLETED\\] in ([0-9a-z.]+)"));
        sample.put("fingerprint", verify("direct", event, rows));
        if (!phase.equals("setup")) save(sample);
    }

    static Map<String, Object> load(String mode, String phase, int rows, int n, List<String> groups, List<Process> workers) throws Exception {
        truncate(mode);
        String event = mode + "-" + phase + "-" + rows + "-" + n + "-" + RUN;
        var key = FileEventKey.newBuilder().setUniqueId(event).build();
        var value = FileEventValue.newBuilder().setFileType("TEAM_STATS").setFilePath(INPUTS.get(rows).toString()).build();
        long count = 0; long start = System.nanoTime();
        producer.send(new ProducerRecord<>(topic(mode, "files"), key, value)).get(30, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        while (count < rows && System.nanoTime() < deadline) {
            checkAlive(workers);
            for (var record : outcomes.poll(Duration.ofMillis(20))) {
                if (record.value() instanceof PersistedEventValue result && event.equals(result.getSourceEventId())) count += result.getPersistedRecords();
            }
        }
        double elapsed = elapsed(start);
        if (count != rows) throw new IllegalStateException(mode + " expected " + rows + " confirmations, got " + count);
        // Offset commits and DB correctness checked outside elapsed notification latency.
        awaitDrained(groups, workers);
        var sample = sample(mode, phase, rows, n, elapsed);
        sample.put("confirmedRows", count); sample.put("fingerprint", verify(mode, event, rows));
        return sample;
    }

    static void awaitReady(List<String> groups, List<Process> workers) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            checkAlive(workers);
            try {
                var descriptions = admin.describeConsumerGroups(groups).all().get(2, TimeUnit.SECONDS);
                if (descriptions.values().stream().allMatch(g -> !g.members().isEmpty()
                        && g.members().stream().anyMatch(m -> !m.assignment().topicPartitions().isEmpty()))) return;
            } catch (Exception ignored) { }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Consumers did not become ready: " + groups);
    }

    static void awaitDrained(List<String> groups, List<Process> workers) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            checkAlive(workers);
            boolean drained = true;
            for (String group : groups) {
                var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                if (offsets.isEmpty()) { drained = false; continue; }
                var request = new HashMap<TopicPartition, OffsetSpec>();
                offsets.keySet().forEach(p -> request.put(p, OffsetSpec.latest()));
                var ends = admin.listOffsets(request).all().get(5, TimeUnit.SECONDS);
                for (var entry : offsets.entrySet()) if (entry.getValue().offset() != ends.get(entry.getKey()).offset()) drained = false;
            }
            if (drained) return;
            Thread.sleep(20);
        }
        throw new IllegalStateException("Consumer lag did not drain: " + groups);
    }

    static void checkAlive(List<Process> workers) {
        for (var worker : workers) if (!worker.isAlive()) throw new IllegalStateException("Worker exited " + worker.exitValue() + "; inspect logs");
    }

    static void truncate(String mode) throws Exception {
        try (Connection db = connect("bench_" + mode); var statement = db.createStatement()) {
            var exists = statement.executeQuery("SELECT to_regclass('public.team_stats') IS NOT NULL");
            exists.next(); boolean present = exists.getBoolean(1); exists.close();
            if (present) statement.execute("TRUNCATE public.team_stats RESTART IDENTITY");
        }
    }

    static String verify(String mode, String event, int expectedRows) throws Exception {
        try (Connection db = connect("bench_" + mode); var statement = db.prepareStatement("""
                SELECT count(*), count(*) FILTER (WHERE source_event_id = ?),
                  md5(string_agg(jsonb_build_array(match_id,period,category,metric,home_team,home_value,
                    away_team,away_value,source_url)::text, E'\n' ORDER BY match_id,period,category,metric))
                FROM team_stats
                """)) {
            statement.setString(1, event);
            try (var rs = statement.executeQuery()) {
                rs.next();
                if (rs.getLong(1) != expectedRows || rs.getLong(2) != expectedRows) throw new IllegalStateException("Wrong rows for " + event);
                String fingerprint = rs.getString(3);
                String previous = EXPECTED.putIfAbsent(expectedRows, fingerprint);
                if (previous != null && !previous.equals(fingerprint)) throw new IllegalStateException("DATA MISMATCH for " + event);
                return fingerprint;
            }
        }
    }

    static Map<String, Object> sample(String mode, String phase, int rows, int n, double elapsed) {
        var result = new LinkedHashMap<String, Object>();
        result.put("mode", mode); result.put("phase", phase); result.put("rows", rows); result.put("repeat", n);
        result.put("elapsedMs", elapsed); result.put("timestamp", Instant.now().toString());
        return result;
    }

    static void save(Map<String, Object> sample) throws IOException {
        String json = JSON.writeValueAsString(sample);
        Files.writeString(OUT.resolve("samples.jsonl"), json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(json); System.out.flush();
    }

    static double duration(String text, String expression) {
        var match = Pattern.compile(expression).matcher(text);
        if (!match.find()) return -1;
        var parts = Pattern.compile("([0-9.]+)(ms|s|m|h)").matcher(match.group(1));
        double ms = 0;
        while (parts.find()) ms += Double.parseDouble(parts.group(1)) * switch(parts.group(2)) { case "h" -> 3600000; case "m" -> 60000; case "s" -> 1000; default -> 1; };
        return ms;
    }
    static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
    static String topic(String mode, String suffix) { return "bench." + RUN + "." + trial + "." + mode + "." + suffix; }
    static String url(String database) { return "jdbc:postgresql://localhost:55440/" + database; }
    static Connection connect(String database) throws SQLException { return DriverManager.getConnection(url(database), "benchmark", "benchmark"); }
    static void stop(Process process) {
        if (!process.isAlive()) return;
        process.destroy();
        try { if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
    }
}
