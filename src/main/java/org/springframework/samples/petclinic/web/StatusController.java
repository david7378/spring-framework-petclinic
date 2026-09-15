package org.springframework.samples.petclinic.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.ServletContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Operational status used to validate the migration: which host answered, which database it talks to,
 * row counts and whether the shared directories are usable. Returns 503 when something is not OK.
 */
@Controller
public class StatusController {

    private final DataSource dataSource;
    private final ServletContext servletContext;
    private final String appVersion;
    private final Path uploadsDir;
    private final Path reportsDir;

    public StatusController(DataSource dataSource, ServletContext servletContext,
                            @Value("${app.version}") String appVersion,
                            @Value("${app.uploads.dir}") String uploadsDir,
                            @Value("${app.reports.dir}") String reportsDir) {
        this.dataSource = dataSource;
        this.servletContext = servletContext;
        this.appVersion = appVersion;
        this.uploadsDir = Paths.get(uploadsDir);
        this.reportsDir = Paths.get(reportsDir);
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hostname", hostname());
        body.put("appVersion", appVersion);
        body.put("javaVersion", System.getProperty("java.version"));
        body.put("server", servletContext.getServerInfo());
        body.put("timestamp", Instant.now().toString());

        Map<String, Object> db = database();
        body.put("database", db);

        boolean uploadsWritable = Files.isDirectory(uploadsDir) && Files.isWritable(uploadsDir);
        body.put("uploads", Map.of("path", uploadsDir.toString(), "writable", uploadsWritable));

        Map<String, Object> reports = new LinkedHashMap<>();
        reports.put("path", reportsDir.toString());
        reports.put("lastReport", lastReport());
        body.put("reports", reports);

        boolean ok = "UP".equals(db.get("status")) && uploadsWritable;
        body.put("status", ok ? "UP" : "DOWN");
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Liveness: only proves the JVM and the web layer answer. Deliberately does not touch the database,
     * so a database outage does not make Kubernetes restart the application pods.
     */
    @GetMapping(value = "/livez", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String livez() {
        return "OK";
    }

    private Map<String, Object> database() {
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // jdbc:postgresql://host:port/db -> host:port
            URI uri = URI.create(connection.getMetaData().getURL().substring("jdbc:".length()));
            db.put("host", uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost());
            db.put("product", connection.getMetaData().getDatabaseProductName() + " "
                + connection.getMetaData().getDatabaseProductVersion());
            statement.setQueryTimeout(5);
            try (ResultSet rs = statement.executeQuery(
                "SELECT (SELECT count(*) FROM owners), (SELECT count(*) FROM pets), (SELECT count(*) FROM visits)")) {
                rs.next();
                db.put("counts", Map.of("owners", rs.getLong(1), "pets", rs.getLong(2), "visits", rs.getLong(3)));
            }
            db.put("status", "UP");
        } catch (Exception e) {
            db.put("status", "DOWN");
            db.put("error", e.getMessage());
        }
        return db;
    }

    private Map<String, Object> lastReport() {
        if (!Files.isDirectory(reportsDir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(reportsDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".csv"))
                .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified()))
                .map(p -> Map.<String, Object>of(
                    "name", p.getFileName().toString(),
                    "modified", Instant.ofEpochMilli(p.toFile().lastModified()).toString()))
                .orElse(null);
        } catch (IOException e) {
            return Map.of("error", e.getMessage());
        }
    }

    private static String hostname() {
        String env = System.getenv("HOSTNAME");
        if (env != null && !env.isBlank()) {
            return env;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "unknown";
        }
    }
}
