package org.springframework.samples.petclinic.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Pet photos stored as plain files on disk ({@code <uploads>/pets/pet-<id>.<ext>}), not in the database.
 * On the VM this is a local directory; after the migration it is a shared (RWX) volume.
 */
@Controller
public class PetPhotoController {

    private static final Map<String, String> EXTENSIONS = Map.of(
        MediaType.IMAGE_JPEG_VALUE, "jpg",
        MediaType.IMAGE_PNG_VALUE, "png",
        MediaType.IMAGE_GIF_VALUE, "gif",
        "image/webp", "webp");

    private final ClinicService clinicService;
    private final Path petsDir;

    public PetPhotoController(ClinicService clinicService, @Value("${app.uploads.dir}") String uploadsDir) {
        this.clinicService = clinicService;
        this.petsDir = Paths.get(uploadsDir, "pets");
    }

    @PostMapping("/owners/{ownerId}/pets/{petId}/photo")
    public String upload(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
                         @RequestParam("photo") MultipartFile photo) throws IOException {
        Pet pet = clinicService.findPetById(petId);
        if (pet == null || pet.getOwner() == null || pet.getOwner().getId() != ownerId) {
            throw new ResponseStatusException(NOT_FOUND, "Pet not found for this owner");
        }
        String extension = EXTENSIONS.get(photo.getContentType());
        if (photo.isEmpty() || extension == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Expected a JPEG, PNG, GIF or WebP image");
        }

        Files.createDirectories(petsDir);
        // Write to a temp file and move it into place, so a concurrent reader never sees a partial image.
        Path tmp = Files.createTempFile(petsDir, ".upload-", ".tmp");
        try (InputStream in = photo.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Path target = petsDir.resolve("pet-" + petId + "." + extension);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        for (String other : EXTENSIONS.values()) {
            if (!other.equals(extension)) {
                Files.deleteIfExists(petsDir.resolve("pet-" + petId + "." + other));
            }
        }
        return "redirect:/owners/{ownerId}";
    }

    @GetMapping("/pets/{petId}/photo")
    public ResponseEntity<Resource> photo(@PathVariable("petId") int petId) {
        for (Map.Entry<String, String> type : EXTENSIONS.entrySet()) {
            Path file = petsDir.resolve("pet-" + petId + "." + type.getValue());
            if (Files.isRegularFile(file)) {
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(type.getKey()))
                    .cacheControl(CacheControl.noCache())
                    .body(new FileSystemResource(file));
            }
        }
        return ResponseEntity.notFound().build();
    }
}
