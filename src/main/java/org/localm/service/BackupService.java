package org.localm.service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupService {
    public Path backup(String name, Path serverDir, boolean full) throws IOException {
        Path backups = serverDir.resolve("backups");
        Files.createDirectories(backups);
        String suffix = full ? "-full-" : "-world-";
        Path out = backups.resolve(name + suffix + DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-") + ".zip");
        
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            if (full) {
                zipPath(serverDir, serverDir, zip, false); // Never include 'backups' folder
            } else {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(serverDir, "world*")) {
                    for (Path world : stream) {
                        if (Files.isDirectory(world)) {
                            zipPath(world, serverDir, zip, true);
                        }
                    }
                }
            }
        }
        return out;
    }

    private void zipPath(Path path, Path root, ZipOutputStream zip, boolean isInsideWorld) throws IOException {
        String fileName = path.getFileName().toString();
        
        if (Files.isDirectory(path)) {
            // Exclude backups folder and potentially huge log files or other things if needed
            if (!isInsideWorld && fileName.equals("backups")) return;
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) zipPath(child, root, zip, isInsideWorld || fileName.startsWith("world"));
            }
            return;
        }

        String entryName = root.relativize(path).toString().replace("\\", "/");
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        try {
            Files.copy(path, zip);
        } catch (IOException e) {
            // If a file is locked (like a running server log), skip it
        }
        zip.closeEntry();
    }
}
