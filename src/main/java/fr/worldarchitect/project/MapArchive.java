package fr.worldarchitect.project;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class MapArchive {
    private static final long MAX_BYTES = 8L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 100000;
    private MapArchive() {}

    public static Path importTerrain(Path archive, Path destination) throws IOException {
        Path staging = Files.createTempDirectory("worldarchitect-import-");
        try {
            long total = 0;
            int count = 0;
            byte[] buffer = new byte[65536];
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    if (++count > MAX_ENTRIES) throw new IOException("Too many ZIP entries");
                    String name = entry.getName();
                    if (name.contains("\\") || name.contains(":") || name.startsWith("/"))
                        throw new IOException("Invalid ZIP path");
                    Path target = staging.resolve(name).normalize();
                    if (!target.startsWith(staging) || target.equals(staging))
                        throw new IOException("Invalid ZIP path");
                    if (entry.isDirectory()) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                            for (int n; (n = zip.read(buffer)) != -1;) {
                                total += n;
                                if (total > MAX_BYTES) throw new IOException("ZIP exceeds import limit");
                                out.write(buffer, 0, n);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
            List<Path> roots;
            try (var paths = Files.walk(staging)) {
                roots = paths.filter(p -> Files.isDirectory(p.resolve("region"))).toList();
            }
            if (roots.size() != 1) throw new IOException("ZIP must contain exactly one terrain root");
            return MapTerrain.snapshot(roots.getFirst(), destination);
        } finally {
            try (var paths = Files.walk(staging)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
}
