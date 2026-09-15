package org.springframework.samples.petclinic.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Nightly report: one CSV row per owner with pet/visit totals.
 * <p>
 * Standalone entry point (no Spring context), run by cron from the exploded webapp:
 * {@code java -cp 'WEB-INF/classes:WEB-INF/lib/*' org.springframework.samples.petclinic.report.ReportGenerator [outputDir]}.
 * Reads the same embedded properties as the web application; JVM system properties override them.
 */
public class ReportGenerator {

    private static final String QUERY = """
        SELECT o.id, o.first_name, o.last_name, o.city,
               COUNT(DISTINCT p.id) AS pets, COUNT(v.id) AS visits, MAX(v.visit_date) AS last_visit
        FROM owners o
        LEFT JOIN pets p ON p.owner_id = o.id
        LEFT JOIN visits v ON v.pet_id = p.id
        GROUP BY o.id, o.first_name, o.last_name, o.city
        ORDER BY o.id""";

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        load(props, "spring/data-access.properties");
        load(props, "app.properties");
        props.putAll(System.getProperties());

        Path outputDir = Paths.get(args.length > 0 ? args[0] : props.getProperty("app.reports.dir"));
        Files.createDirectories(outputDir);
        String name = "owners-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        Path tmp = Files.createTempFile(outputDir, ".report-", ".tmp");

        Class.forName(props.getProperty("jdbc.driverClassName"));
        int rows = 0;
        try (Connection connection = DriverManager.getConnection(props.getProperty("jdbc.url"),
                 props.getProperty("jdbc.username"), props.getProperty("jdbc.password"));
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(QUERY);
             Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            out.write("owner_id,first_name,last_name,city,pets,visits,last_visit\n");
            while (rs.next()) {
                out.write(String.join(",",
                    rs.getString("id"), csv(rs.getString("first_name")), csv(rs.getString("last_name")),
                    csv(rs.getString("city")), rs.getString("pets"), rs.getString("visits"),
                    rs.getString("last_visit") == null ? "" : rs.getString("last_visit")));
                out.write("\n");
                rows++;
            }
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        Path target = outputDir.resolve(name);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        System.out.println(LocalDateTime.now() + " wrote " + rows + " rows to " + target);
    }

    private static void load(Properties props, String resource) throws IOException {
        try (InputStream in = ReportGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource);
            }
            props.load(in);
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
