package ru.hh.school.homework;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import static java.util.Collections.reverseOrder;
import static java.util.Map.Entry.comparingByValue;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

public class Launcher {

    private static final Path rootPath = Path.of("/home/hlem/hh-school/hh-school/parallelism/");
    private static final ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(getFilesByFolder(rootPath).size() * 10, 100),
            Thread::new
    );

    public static void main(String[] args) throws IOException {
        // Написать код, который, как можно более параллельно:
        // - по заданному пути найдет все "*.java" файлы
        // - для каждого файла вычислит 10 самых популярных слов (см. #naiveCount())
        // - соберет top 10 для каждой папки в которой есть хотя-бы один java файл
        // - для каждого слова сходит в гугл и вернет количество результатов по нему (см. #naiveSearch())
        // - распечатает в консоль результаты в виде:
        // <папка1> - <слово #1> - <кол-во результатов в гугле>
        // <папка1> - <слово #2> - <кол-во результатов в гугле>
        // ...
        // <папка1> - <слово #10> - <кол-во результатов в гугле>
        // <папка2> - <слово #1> - <кол-во результатов в гугле>
        // <папка2> - <слово #2> - <кол-во результатов в гугле>
        // ...
        // <папка2> - <слово #10> - <кол-во результатов в гугле>
        // ...
        //
        // Порядок результатов в консоли не обязательный.
        // При желании naiveSearch и naiveCount можно оптимизировать.

        long start = System.currentTimeMillis();
        Map<String, Set<File>> filesByFolder = getFilesByFolder(rootPath);
        System.out.println("Всего " + filesByFolder.size() + " папок, соответственно " + Math.min(filesByFolder.size() * 10, 100) + " потоков для обработки запросов.");
        Map<String, List<String>> wordsByFolder = getPopularWordsByFolder(filesByFolder);

        long end1 = System.currentTimeMillis();
        System.out.println("Время выполнения поиска встречаемых слов в файлах: " + (end1 - start) + " мс");
        wordsByFolder.entrySet().stream()
                .flatMap(
                        entry -> entry.getValue().stream().map(word -> CompletableFuture.supplyAsync(
                                () -> {
                                    try {
                                        return testSearch(entry.getKey(), word);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                },
                                executor
                        ))
                )
                .collect(Collectors.toList())
                .stream()
                .map(CompletableFuture::join)
                .forEach(System.out::println);

        System.out.println("Время выполнения поиска встречаемых слов в гугле: " + (System.currentTimeMillis() - end1) + " мс");

        executor.shutdown();
    }

    private static Map<String, List<String>> getPopularWordsByFolder(Map<String, Set<File>> filesByFolder) {
        Map<String, List<String>> wordsByFolder = new HashMap<>();
        for (Map.Entry<String, Set<File>> entry : filesByFolder.entrySet()) {
            List<String> wordsInFolder = entry.getValue().stream()
                    .flatMap(file -> naiveCount(file.toPath()).entrySet().stream())
                    .collect(groupingBy(Map.Entry::getKey, Collectors.summingLong(Map.Entry::getValue)))
                    .entrySet().stream()
                    .sorted(comparingByValue(reverseOrder()))
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .toList();
            wordsByFolder.put(entry.getKey(), wordsInFolder);
        }
        return wordsByFolder;
    }

    private static Map<String, Set<File>> getFilesByFolder(Path path) {
        List<File> result = new ArrayList<>();
        Queue<File> fileTree = new PriorityQueue<>();
        Collections.addAll(fileTree, Objects.requireNonNull(path.toFile().listFiles()));
        while (!fileTree.isEmpty()) {
            File currentFile = fileTree.remove();
            if (currentFile.isDirectory()) {
                Collections.addAll(fileTree, Objects.requireNonNull(currentFile.listFiles()));
                result.add(currentFile);
            }
        }

        return result.stream()
                .collect(groupingBy(File::getAbsolutePath,
                        Collectors.flatMapping(
                                file -> Arrays.stream(Objects.requireNonNull(file.listFiles()))
                                        .filter(file1 -> file1.getAbsolutePath().endsWith(".java")),
                                Collectors.toSet()
                        ))).entrySet().stream().filter(entry -> entry.getValue().size() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static void testCount() {
//    Path path = Path.of("~\\hh-school\\hh-school\\parallelism\\src\\main\\java\\ru\\hh\\school\\parallelism\\Runner.java");
        Path path = Path.of("/home/hlem/hh-school/hh-school/parallelism/src/main/java/ru/hh/school/parallelism/Runner.java");
        System.out.println(naiveCount(path));
    }

    private static Map<String, Long> naiveCount(Path path) {
        try {
            return Files.lines(path)
                    .flatMap(line -> Stream.of(line.split("[^a-zA-Z0-9]")))
                    .filter(word -> word.length() > 3)
                    .collect(groupingBy(identity(), counting()))
                    .entrySet()
                    .stream()
                    .sorted(comparingByValue(reverseOrder()))
                    .limit(10)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String testSearch(String folder, String word) throws IOException {
        return folder + " - " + word + " - " + naiveSearch(word);
    }

    private static long naiveSearch(String query) throws IOException {
        Document document = Jsoup //
                .connect("https://www.google.com/search?q=" + query) //
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.116 Safari/537.36") //
                .get();

        Element divResultStats = document.select("div#slim_appbar").first();
        String text = divResultStats.text();
        String resultsPart = text.substring(0, text.indexOf('('));
        return Long.parseLong(resultsPart.replaceAll("[^0-9]", ""));
    }

}
