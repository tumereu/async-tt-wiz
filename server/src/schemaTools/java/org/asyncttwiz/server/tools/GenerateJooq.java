package org.asyncttwiz.server.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.asyncttwiz.server.database.DatabaseSettings;
import org.asyncttwiz.server.database.FoundationMigrations;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Build tooling only: no external database URL is accepted. */
public final class GenerateJooq {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        generate(args[0], output);
        if (args.length == 3 && args[2].equals("verify")) {
            Map<String, String> first = hashes(output);
            generate(args[0], output);
            Map<String, String> second = hashes(output);
            if (first.isEmpty() || !first.equals(second)) throw new IllegalStateException("Non-deterministic jOOQ generation");
            System.out.println("PASS byte-identical independent generation: " + first);
        }
    }

    private static void generate(String image, Path output) throws Exception {
        if (!image.matches("postgres@sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("A digest-pinned PostgreSQL17 image is required");
        }
        try (PostgreSQLContainer db = new PostgreSQLContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("schema_generation").withUsername("schema_generator")
                .withPassword(UUID.randomUUID().toString())
                .withReuse(false)
                .withLabel("org.asyncttwiz.owner", "schema-generation")) {
            db.start();
            String id = db.getContainerId();
            System.out.println("OWNED codegen PostgreSQL container=" + id + " image=" + image);
            try {
                int migrated = FoundationMigrations.migrate(new DatabaseSettings(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
                if (migrated != 9) throw new IllegalStateException("Expected clean V1-V9 migrations");
                GenerationTool.generate(new Configuration()
                        .withJdbc(new Jdbc().withDriver("org.postgresql.Driver").withUrl(db.getJdbcUrl())
                                .withUser(db.getUsername()).withPassword(db.getPassword()))
                        .withGenerator(new Generator()
                                .withDatabase(new Database().withName("org.jooq.meta.postgres.PostgresDatabase")
                                        .withInputSchema("public").withIncludes("service_writer|account|account_session|password_reset_token|reset_mail|request_identity|account_request|definition|account_definition|saved_deck|deck_entry|contact|game|invitation|game_stream|game_inbox|game_revision|game_outbox|player_preparation|game_definition|game_play"))
                                .withGenerate(new Generate().withGeneratedAnnotation(false).withComments(false)
                                        .withJavadoc(false).withPojos(false).withDaos(false))
                                .withTarget(new Target().withPackageName("org.asyncttwiz.server.generated")
                                        .withDirectory(output.toAbsolutePath().toString()).withClean(true))));
                System.out.println("PASS generated real PostgreSQL fields from validated Flyway V1-V9");
            } finally {
                var client = db.getDockerClient();
                var volumes = client.inspectContainerCmd(id).exec().getMounts().stream()
                        .map(mount -> mount.getName()).filter(name -> name != null).toList();
                db.stop();
                if (!client.listContainersCmd().withShowAll(true).withIdFilter(List.of(id)).exec().isEmpty()) {
                    throw new IllegalStateException("Owned container still present: " + id);
                }
                var remainingVolumes = client.listVolumesCmd().exec().getVolumes();
                if (remainingVolumes != null && remainingVolumes.stream().anyMatch(volume -> volumes.contains(volume.getName()))) {
                    throw new IllegalStateException("Owned codegen database volume leaked");
                }
                System.out.println("CLEANUP removed owned codegen container=" + id + " volumes=" + volumes);
            }
        }
    }

    private static Map<String, String> hashes(Path directory) throws Exception {
        Map<String, String> hashes = new TreeMap<>();
        try (var files = Files.walk(directory)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                hashes.put(directory.relativize(path).toString(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
            }
        }
        return hashes;
    }
}
