package org.springframework.samples.petclinic.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lists and serves the CSV files written by the nightly report job.
 */
@Controller
public class ReportController {

    private static final Pattern REPORT_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.csv");
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private final Path reportsDir;

    public ReportController(@Value("${app.reports.dir}") String reportsDir) {
        this.reportsDir = Paths.get(reportsDir);
    }

    @GetMapping("/reports")
    public String list(Model model) throws IOException {
        List<ReportFile> reports = Collections.emptyList();
        if (Files.isDirectory(reportsDir)) {
            try (Stream<Path> files = Files.list(reportsDir)) {
                reports = files.filter(p -> REPORT_NAME.matcher(p.getFileName().toString()).matches())
                    .map(p -> p.toFile())
                    .sorted(Comparator.comparingLong(java.io.File::lastModified).reversed())
                    .map(f -> new ReportFile(f.getName(), f.length(),
                        DATE_FORMAT.format(Instant.ofEpochMilli(f.lastModified()))))
                    .toList();
            }
        }
        model.addAttribute("reports", reports);
        model.addAttribute("reportsDir", reportsDir.toString());
        return "reports/reportList";
    }

    @GetMapping("/reports/{name:.+}")
    public ResponseEntity<Resource> download(@PathVariable("name") String name) {
        if (!REPORT_NAME.matcher(name).matches()) {
            return ResponseEntity.badRequest().build();
        }
        Path file = reportsDir.resolve(name).normalize();
        if (!file.startsWith(reportsDir) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
            .body(new FileSystemResource(file));
    }

    /** JavaBean (not a record) so JSP EL can read it through getters. */
    public static class ReportFile {
        private final String name;
        private final long size;
        private final String modified;

        ReportFile(String name, long size, String modified) {
            this.name = name;
            this.size = size;
            this.modified = modified;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }

        public String getModified() {
            return modified;
        }
    }
}
