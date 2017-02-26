package remote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@SpringBootApplication
@RestController
@CrossOrigin(origins = "*")
public class RemoteApplication {

    private static Path rootPath;
    private static String executable;

    private static List<String> SUPPORTED_EXTENSIONS = Arrays.asList("mp3", "m4a", "flac", "cue");

    private static Map<String, String> commandOptions = new HashMap<>();

    static {
        commandOptions.put("play", "-t");
        commandOptions.put("stop", "-s");
        commandOptions.put("next", "-f");
        commandOptions.put("prev", "-r");
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(args));
        String rootDir = null;
        for (int i = 0; i < args.length - 1; i += 2) {
            if (args[i].equals("-d")) {
                rootDir = args[i + 1];
            } else if (args[i].equals("-e")) {
                executable = args[i + 1];
            }
        }
        if (rootDir == null || rootDir.equals("")) {
            System.out.println("Root dir is not specified");
            printUsage();
            System.exit(-1);
        }
        rootPath = Paths.get(rootDir);
        if (!Files.exists(rootPath)) {
            System.out.println(rootDir + " does not exist");
            System.exit(-1);
        }
        System.out.println("Root dir: " + rootPath);

        if (executable == null) {
            System.out.println("Executable is not specified");
            printUsage();
            System.exit(-1);
        }

        SpringApplication.run(RemoteApplication.class, args);
    }

    private static void printUsage() {
        System.out.println("Usage:\n" +
                "-d <root directory>\n" +
                "-e <path to executable>\n");
    }

    @RequestMapping("/list")
    public List<Map<String, Object>> list(
            @RequestParam(value = "dir", required = false) String currentDir) {

        currentDir = toSystemPath(currentDir);

        Path currentPath = currentDir != null ? rootPath.resolve(currentDir) : rootPath;

        List<Map<String, Object>> list = new ArrayList<>();
        try {
            System.out.println("list " + currentPath);
            Files.list(currentPath)
                    .sorted()
                    .forEach(path -> {
                if (Files.isDirectory(path) || extIsSupported(path.toString())) {
                    long size = -1;
                    if (!Files.isDirectory(path)) {
                        try {
                            size = Files.size(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("path", toUnixPath(rootPath.relativize(path).toString()));
                    item.put("size", formatSize(size));
                    list.add(item);
                }
            });
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String formatSize(long size) {
        if (size >= 1000000000)
            return size / 1000000000 + "G";
        if (size >= 1000000)
            return size / 1000000 + "M";
        if (size >= 1000)
            return size / 1000 + "K";
        return String.valueOf(size);
    }

    private String toUnixPath(String path) {
        return path == null ? null : path.replace('\\', '/');
    }

    private String toSystemPath(String path) {
        return path == null ? null : path.replace('/', File.separatorChar);
    }

    @RequestMapping("/control")
    public String control(@RequestParam(value = "cmd") String command) {
        String option = commandOptions.get(command);
        if (option != null) {
            System.out.println("control " + option);
            ProcessBuilder pb = new ProcessBuilder(executable, option);
            File log = new File("process.log");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            try {
                pb.start();
            } catch (IOException e) {
                e.printStackTrace();
                return "Error starting process: " + e.toString();
            }
            return "ok";
        } else {
            return "unsupported command";
        }
    }

    @RequestMapping("/add")
    public String add(@RequestParam(value = "path") String currentPath) {
        Path path = rootPath.resolve(toSystemPath(currentPath));
        System.out.println("add " + path);
        ProcessBuilder pb = new ProcessBuilder(executable, "-E", path.toString());
        File log = new File("process.log");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
        try {
            pb.start();
        } catch (IOException e) {
            e.printStackTrace();
            return "Error starting process: " + e.toString();
        }
        return "ok";
    }

    private boolean extIsSupported(String name) {
        int pos = name.lastIndexOf(".");
        if (pos == -1 || pos == name.length() - 1)
            return false;
        String ext = name.substring(pos + 1).toLowerCase();
        return SUPPORTED_EXTENSIONS.indexOf(ext) > -1;
    }

}
