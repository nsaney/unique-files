package chairosoft.util.app;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UniqueFiles {

    ////// Constants //////
    public static final String DIGEST_ALGORITHM_SHA_256 = "sha-256";
    public static final String COMMENT_START = "#";
    public static final String ESCAPER = "\\";
    public static final String DELIMITER = "|";


    ////// Main Method //////
    public static void main(String... args) throws Exception {
        Map<String, List<File>> fileListsByHash = new HashMap<>();
        for (String arg : args) {
            printlnComment("Reading arg: " + arg);
            File fsEntry = new File(arg);
            List<File> recursiveFiles = getAllSubFiles(fsEntry);
            printlnComment("- Files found for arg: " + recursiveFiles.size());
            printlnComment("- Calculating hashes.");
            for (File file : recursiveFiles) {
                String hash = getFileDigestHex(file, DIGEST_ALGORITHM_SHA_256);
                List<File> matchingFiles = fileListsByHash.computeIfAbsent(
                    hash,
                    h -> new ArrayList<>()
                );
                matchingFiles.add(file);
            }
        }
        write(System.out, ESCAPER, DELIMITER, fileListsByHash);
    }


    ////// Static Methods //////
    public static void printlnComment(String comment) {
        System.out.print(COMMENT_START);
        System.out.println(comment);
    }

    public static List<File> getAllSubFiles(File baseFsEntry) {
        List<File> files = new ArrayList<>();
        Queue<File> fileSystemEntries = new ArrayDeque<>();
        fileSystemEntries.add(baseFsEntry);
        while (!fileSystemEntries.isEmpty()) {
            File fsEntry = fileSystemEntries.poll();
            if (fsEntry.isFile()) {
                files.add(fsEntry);
            }
            if (fsEntry.isDirectory()) {
                printlnComment("- Found directory: " + fsEntry);
                File[] fsEntries = fsEntry.listFiles();
                if (fsEntries != null) {
                    Collections.addAll(fileSystemEntries, fsEntries);
                }
            }
        }
        return files;
    }

    public static String getAsHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] getFileDigestBytes(File file, String algorithm) throws Exception {
        MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
        try (
            OutputStream nullOut = new OutputStream() {@Override public void write(int b) { }};
            DigestOutputStream dos = new DigestOutputStream(nullOut, messageDigest)
        ) {
            Path path = file.toPath();
            Files.copy(path, dos);
        }
        return messageDigest.digest();
    }

    public static String getFileDigestHex(File file, String algorithm) throws Exception {
        byte[] digestBytes = getFileDigestBytes(file, algorithm);
        return getAsHex(digestBytes);
    }

    public static String escape(String original, String escaper, String delimiter) {
        String patternEscaper = Pattern.quote(escaper);
        String patternDelimiter = Pattern.quote(delimiter);
        return original
            .replaceAll(patternEscaper, escaper + escaper)
            .replaceAll(patternDelimiter, escaper + delimiter);
    }

    public static void write(
        PrintStream out,
        String escaper,
        String delimiter,
        Map<String, List<File>> multimap
    ) {
        List<Map.Entry<String, List<File>>> entriesSorted = new ArrayList<>(multimap.size());
        for (Map.Entry<String, List<File>> entry : multimap.entrySet()) {
            List<File> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }
            values.sort(Comparator.comparing(File::toString, String::compareTo));
            entriesSorted.add(entry);
        }
        // sort entries by first value text
        entriesSorted.sort(Comparator.comparing(
            e -> e.getValue().get(0).toString(),
            String::compareTo)
        );
        for (Map.Entry<String, List<File>> entry : entriesSorted) {
            String key = entry.getKey();
            List<File> values = entry.getValue();
            String valueText = values
                .stream()
                .filter(Objects::nonNull)
                .map(f -> escape(f.toString(), escaper, delimiter))
                .collect(Collectors.joining(delimiter));
            out.print(escape(key, escaper, delimiter));
            out.print(delimiter);
            out.println(valueText);
        }
    }
}
