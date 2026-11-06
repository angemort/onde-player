package org.gradle.wrapper;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tiny, auditable Gradle bootstrap for the ZIP version of Onde.
 * Not Gradle Inc.'s official wrapper; deliberately uses only standard Java APIs.
 * Downloads an official Gradle distribution and validates its published SHA-256.
 */
public final class GradleWrapperMain {
    private static final String ALLOWED_PREFIX = "https://services.gradle.org/distributions/";
    public static void main(String[] args) {
        try {
            Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(root.resolve("gradle/wrapper/gradle-wrapper.properties"))) {
                properties.load(input);
            }
            String address = properties.getProperty("distributionUrl");
            String expected = properties.getProperty("distributionSha256Sum", "").toLowerCase();
            if (address == null || !address.startsWith(ALLOWED_PREFIX) || !address.endsWith("-bin.zip")) {
                throw new IllegalArgumentException("Invalid official Gradle distribution URL");
            }
            if (!expected.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Missing expected SHA-256");
            String filename = address.substring(address.lastIndexOf('/') + 1);
            String version = filename.substring(0, filename.length() - "-bin.zip".length());
            Path directory = Paths.get(System.getProperty("user.home"), ".gradle", "onde-wrapper", version);
            Path launcher = directory.resolve(version).resolve("bin/gradle");
            if (!Files.isRegularFile(launcher)) {
                Files.createDirectories(directory);
                Path temp = Files.createTempFile(directory, "gradle-dist-", ".zip");
                try {
                    System.out.println("Onde: downloading Gradle " + version + " (first build only)...");
                    URLConnection connection = new URL(address).openConnection();
                    connection.setConnectTimeout(30_000);
                    connection.setReadTimeout(120_000);
                    try (InputStream in = connection.getInputStream()) {
                        Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                    try (InputStream in = Files.newInputStream(temp)) {
                        byte[] buffer = new byte[32768];
                        int read;
                        while ((read = in.read(buffer)) != -1) sha.update(buffer, 0, read);
                    }
                    StringBuilder hash = new StringBuilder();
                    for (byte b : sha.digest()) hash.append(String.format("%02x", b & 0xff));
                    if (!hash.toString().equals(expected)) throw new SecurityException("Gradle ZIP checksum mismatch");
                    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(temp))) {
                        ZipEntry entry;
                        while ((entry = zip.getNextEntry()) != null) {
                            Path target = directory.resolve(entry.getName()).normalize();
                            if (!target.startsWith(directory)) throw new SecurityException("Invalid ZIP path");
                            if (entry.isDirectory()) Files.createDirectories(target);
                            else {
                                Files.createDirectories(target.getParent());
                                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                            zip.closeEntry();
                        }
                    }
                } finally { Files.deleteIfExists(temp); }
            }
            if (!Files.isRegularFile(launcher)) throw new IllegalStateException("Gradle launcher missing after extraction");
            launcher.toFile().setExecutable(true);
            List<String> command = new ArrayList<>();
            command.add(launcher.toAbsolutePath().toString());
            command.addAll(Arrays.asList(args));
            Process process = new ProcessBuilder(command).directory(root.toFile()).inheritIO().start();
            System.exit(process.waitFor());
        } catch (Exception e) {
            System.err.println("Onde Gradle bootstrap: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
