/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.rng.examples.stress;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.core.source32.IntProvider;
import org.apache.commons.rng.simple.RandomSource;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.RunLast;
import picocli.CommandLine.Spec;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Class that can be used for testing a generator by piping the values
 * returned by its {@link UniformRandomProvider#nextInt()} method to a
 * program that reads {@code int} values from its standard input and
 * writes an analysis report to standard output.
 *
 * The <a href="http://www.phy.duke.edu/~rgb/General/dieharder.php">
 * "dieharder"</a> test suite is such a software.
 *
 * Example of command line, assuming that "rng-stress-tester.jar" specifies this
 * class as the "main" class (see {@link #main(String[]) main} method):
 * <pre><code>
 *  $ java -jar rng-stress-tester.jar run /usr/bin/dieharder -a -g 200 -Y 1 -k 2
 * </code></pre>
 */
@Command(name = "rng-stress-tester",
         description = "Test random data generators.",
         version = "rng-stress-tester 1.3")
public class RandomStressTester implements Callable<Void> {
    /** The command specification. */
    @Spec
    private CommandSpec spec;

    /** The reusable options. */
    @Mixin
    private ReusableOptions reusableOptions;

    // Set of commands:
    // - run
    // - list
    // They have a reference to their parent command (set by picocli).
    // Their callable just calls the parent passing themself to provide parameters.

    /**
     * Re-usable options for all commands.
     */
    @Command(sortOptions = false,
             mixinStandardHelpOptions = true,
             synopsisHeading      = "%n",
             descriptionHeading   = "%n",
             parameterListHeading = "%nParameters:%n%n",
             optionListHeading    = "%nOptions:%n",
             commandListHeading   = "%nCommands:%n%n")
    public static class ReusableOptions {
        /** The verbosity. */
        @Option(names = { "-v", "--verbose" },
                description = {
                        "Specify multiple -v options to increase verbosity.",
                        "For example, `-v -v -v` or `-vvv`"
                })
        protected boolean[] verbosity = new boolean[0];
    }

    /**
     * Specification for the "run" command.
     */
    @Command(name = "run",
             description = "Run repeat trials of random data generators using a provided test application.",
             version = "rng-stress-tester 1.3")
    private static class RunCommand implements Callable<Void> {
        /**
         * The output mode for exitsing files.
         */
        enum OutputMode {
            /** Skip existing files. */
            SKIP,
            /** Append to existing files. */
            APPEND,
            /** Overwrite existing files. */
            OVERWRITE
        }

        /** The parent. */
        @ParentCommand
        private RandomStressTester parent;

        /** The reusable options. */
        @Mixin
        private ReusableOptions reusableOptions;

        /** The executable. */
        @Parameters(index = "0",
                    description = "The stress test executable.")
        private File executable;

        /** The executable arguments. */
        @Parameters(index = "1..*",
                    description = "The arguments to pass to the executable.",
                    paramLabel = "<argument>")
        private List<String> executableArguments;

        /** The file output prefix. */
        @Option(names = {"--prefix"},
                description = "Results file prefix (default: ${DEFAULT-VALUE}).")
        private File fileOutputPrefix = new File("stress_");

        /** The overwrite flag. */
        @Option(names = {"-o", "--output-mode"},
                description = { "Output mode for existing files (default: ${DEFAULT-VALUE}).",
                                "Valid values: ${COMPLETION-CANDIDATES}"})
        private OutputMode outputMode = OutputMode.SKIP;

        /** The list of random generators. */
        @Option(names = {"-l", "--list"},
                description = { "List of random generators.",
                               "The default list is all known generators." })
        private File generatorsList;

        /** The number of concurrent tasks. */
        @Option(names = {"-n", "--tasks"},
                description = { "Number of concurrent tasks (default: ${DEFAULT-VALUE}).",
                                "Two threads are required per task." })
        private int taskCount = 4;

        /** The output byte order of the binary data. */
        @Option(names = {"-b", "--byte-order"},
                description = { "Byte-order of the data (default: ${DEFAULT-VALUE}).",
                                "Valid values: BIG_ENDIAN, LITTLE_ENDIAN." })
        private ByteOrder byteOrder = ByteOrder.nativeOrder();

        /** The flag to indicate a dry run. */
        @Option(names = {"--dry-run"},
                description = "Perform a dry run where the generators and output files are created " +
                              "but the stress test is not executed.")
        private boolean dryRun = false;

        @Override
        public Void call() throws Exception {
            parent.runCommand(this);
            return null;
        }
    }

    /**
     * Specification for the "list" command.
     */
    @Command(name = "list",
             description = "List random generators.",
             version = "rng-stress-tester 1.3")
    private static class ListCommand implements Callable<Void> {
        /** The parent. */
        @ParentCommand
        private RandomStressTester parent;

        /** The reusable options. */
        @Mixin
        private ReusableOptions reusableOptions;

        /** The number of trials to put in the template list of random generators. */
        @Option(names = {"-t", "--trials"},
                description = "The number of trials for each random generator.")
        private int trials = 1;

        @Override
        public Void call() throws Exception {
            parent.runCommand(this);
            return null;
        }
    }

    /**
     * Thrown by the stress test application.
     */
    private static class StressTestException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Create a new instance.
         *
         * @param message the message
         */
        StressTestException(String message) {
            super(message);
        }

        /**
         * Create a new instance.
         *
         * @param message the message
         * @param cause the cause
         */
        StressTestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Encapsulate the data needed to create and test a random generator. This includes:
     *
     * <ul>
     *   <li>The random source
     *   <li>The constructor arguments
     *   <li>The number of trials for each random source
     * </ul>
     */
    private static class StressTestData {
        /** The random source. */
        private final RandomSource randomSource;
        /** The arguments used to construct the random source. */
        private final Object[] args;
        /** The number of trials. */
        private final int trials;

        /**
         * Creates a instance.
         *
         * @param randomSource The random source.
         * @param args The arguments used to construct the random source (can be {@code null}).
         */
        StressTestData(RandomSource randomSource,
                       Object[] args) {
            // Ignore by default (trials = 0) any source that has arguments
            this(randomSource, args, args == null ? 1 : 0);
        }

        /**
         * Creates a instance.
         *
         * @param randomSource The random source.
         * @param args The arguments used to construct the random source (can be {@code null}).
         * @param trials The number of trials.
         */
        StressTestData(RandomSource randomSource,
                         Object[] args,
                         int trials) {
            this.randomSource = randomSource;
            this.args = args;
            this.trials = trials;
        }

        /**
         * Creates the random generator.
         *
         * @return the uniform random provider
         */
        UniformRandomProvider createRNG() {
            // Use a null seed to force seeding
            return RandomSource.create(randomSource, null, args);
        }
    }

    /**
     * Default list of generators defined by the {@link RandomSource}.
     */
    private static class DefaultRandomSourceList implements Iterable<StressTestData> {
        /**
         * The example arguments for RandomSource values that require them.
         */
        private static final EnumMap<RandomSource, Object[]> EXAMPLE_ARGUMENTS =
                new EnumMap <>(RandomSource.class);

        static {
            // Currently we cannot detect if the source requires arguments,
            // e.g. RandomSource.TWO_CMRES_SELECT. So example arguments must
            // be manually added here.
            EXAMPLE_ARGUMENTS.put(RandomSource.TWO_CMRES_SELECT, new Object[] { 1, 2 });
        }

        /** List of generators. */
        private final List<StressTestData> list = new ArrayList<>();

        /**
         * Creates the list.
         */
        DefaultRandomSourceList() {
            // Auto-generate using the order of the RandomSource enum
            for (final RandomSource source : RandomSource.values()) {
                list.add(new StressTestData(source, EXAMPLE_ARGUMENTS.get(source)));
            }
        }

        /** {@inheritDoc} */
        @Override
        public Iterator<StressTestData> iterator() {
            return list.iterator();
        }
    }

    /**
     * Program's entry point.
     *
     * @param args Application's arguments.
     * The order is as follows:
     * <ol>
     *  <li>Path to the executable: this is the analyzer software that reads 32-bits
     *   integers from stdin.</li>
     *  <li>All remaining arguments are passed to the executable.</li>
     * </ol>
     */
    public static void main(String[] args) {
        // Implements Callable, so parsing, error handling and handling user
        // requests for usage help or version help can be done with one line of code.
        final RandomStressTester tester = new RandomStressTester();

        // Do this manually so we can configure options.
        final CommandLine cmd = new CommandLine(tester)
                .addSubcommand("run",  new CommandLine(new RunCommand())
                                                       .setStopAtPositional(true)
                                                       .setCaseInsensitiveEnumValuesAllowed(true))
                .addSubcommand("list", new ListCommand());

        try {
            cmd.parseWithHandler(new RunLast(), args);
        } catch (final StressTestException ex) {
            // This is an error we know about
            logError(ex.getMessage());
            if (tester.reusableOptions.verbosity.length > 0) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Log an error message to {@link System#err}.
     *
     * @param format the format
     * @param args the arguments
     */
    private static void logError(String format, Object... args) {
        log(System.err, "[ERROR] " + format, args);
    }

    /**
     * Log a message error to the provided {@link PrintStream}.
     *
     * @param out the output
     * @param format the format
     * @param args the arguments
     */
    private static void log(PrintStream out, String format, Object... args) {
        out.printf(format, args);
        out.println();
    }

    @Override
    public Void call() {
        // All work is done in sub-commands so just print the usage
        spec.commandLine().usage(System.out);
        return null;
    }

    /**
     * Prints a template generators list to stdout.
     *
     * @param cmd The command.
     */
    void runCommand(ListCommand cmd) {
        printTemplateList(System.out, new DefaultRandomSourceList(), cmd.trials);
    }

    /**
     * Validates the run command arguments, creates the list of generators and runs the
     * stress test tasks.
     *
     * @param cmd The command.
     */
    void runCommand(RunCommand cmd) {
        checkExecutable(cmd.executable);
        checkOutputDirectory(cmd.fileOutputPrefix);
        runStressTest(createTestData(cmd.generatorsList), cmd);
    }

    /**
     * Prints the template list to the output.
     *
     * @param out The output.
     * @param testData The test data.
     * @param numberOfTrials The number of trials.
     */
    private static void printTemplateList(PrintStream out,
                                          Iterable<StressTestData> testData,
                                          int numberOfTrials) {
        out.println("# Example generators list.");
        out.println("# Generators are allocated an identifier using the list order.");
        out.println("# Any generator with no trials is ignored.");
        out.println("#");
        out.printf("# %-18s %-7s   %s%n", "RandomSource", "trials", "[constructor arguments ...]");
        for (StressTestData data : testData) {
            final int trials = (data.trials == 0) ? 0 : numberOfTrials;
            out.printf("%-20s %-7d", data.randomSource.name(), trials);
            if (data.args != null) {
                out.print("   ");
                out.print(Arrays.toString(data.args));
            }
            out.println();
        }
    }

    /**
     * Check the executable exists and has execute permissions.
     *
     * @param executable The executable.
     * @throws StressTestException If the executable is invalid.
     */
    private static void checkExecutable(File executable) {
        if (!executable.exists() ||
            !executable.canExecute()) {
            throw new StressTestException("Program is not executable: " + executable);
        }
    }

    /**
     * Check the output directory exists and has write permissions.
     *
     * @param fileOutputPrefix The file output prefix.
     * @throws StressTestException If the output directory is invalid.
     */
    private static void checkOutputDirectory(File fileOutputPrefix) {
        final File reportDir = fileOutputPrefix.getAbsoluteFile().getParentFile();
        if (!reportDir.exists() ||
            !reportDir.isDirectory() ||
            !reportDir.canWrite()) {
            throw new StressTestException("Invalid output directory: " + reportDir);
        }
    }

    /**
     * Creates the test data.
     *
     * <p>If the input file is null then a default list is created.
     *
     * @param generatorsList The generators list file.
     * @return the iterable test data
     */
    private static Iterable<StressTestData> createTestData(File generatorsList) {
        if (generatorsList == null) {
            return new DefaultRandomSourceList();
        }

        final List<StressTestData> list = new ArrayList<>();

        // Read data into a list
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(generatorsList), StandardCharsets.UTF_8))) {

            // TODO


        } catch (IOException ex) {
            throw new StressTestException("Failed to read generators list: " + ex.getMessage(), ex);
        }

        return list;
    }

    /**
     * Creates the tasks and starts the processes.
     *
     * @param stressTestData List of generators to be tested.
     * @param cmd the run command
     */
    private static void runStressTest(Iterable<StressTestData> stressTestData,
                                      RunCommand cmd) {
        // Parallel execution.
        final ExecutorService service = Executors.newFixedThreadPool(cmd.taskCount);

        // Placeholder (output will be "null").
        final List<Future<?>> execOutput = new ArrayList<>();

        final ArrayList<String> command = buildSubProcessCommand(cmd.executable, cmd.executableArguments);
        final String basePath = cmd.fileOutputPrefix.getAbsolutePath();

        // Run tasks.
        // TODO: Should the id be in the input list?
        // This allows re-running some generators and overwriting their results.
        int id = 0;
        for (StressTestData testData : stressTestData) {
            if (testData.trials < 1) {
                // Skip this
                continue;
            }
            id++;
            for (int trial = 1; trial <= testData.trials; trial++) {
                // Create the output file
                final File output = new File(String.format("%s%d_%d", basePath, id, trial));
                if (cmd.outputMode == RunCommand.OutputMode.SKIP && output.exists()) {
                    logError("Skipping existing output file: " + output);
                    continue;
                }
                // Create the generator
                UniformRandomProvider rng = testData.createRNG();
                if (cmd.byteOrder.equals(ByteOrder.LITTLE_ENDIAN)) {
                    rng = createReverseIntProvider(rng);
                }
                // Run the test
                final Runnable r = new StressTestTask(rng, output, command, cmd);
                execOutput.add(service.submit(r));
            }
        }

        // Wait for completion (ignoring return value).
        try {
            for (Future<?> f : execOutput) {
                try {
                    f.get();
                } catch (ExecutionException ex) {
                    logError(ex.getCause().getMessage());
                }
            }
        } catch (InterruptedException ignored) {}

        // Terminate all threads.
        service.shutdown();
    }

    /**
     * Builds the command for the sub-process.
     *
     * @param executable The executable file.
     * @param executableArguments The executable arguments.
     * @return the command
     */
    private static ArrayList<String> buildSubProcessCommand(File executable,
                                                            List<String> executableArguments) {
        final ArrayList<String> command = new ArrayList<>();
        try {
            command.add(executable.getCanonicalPath());
        } catch (IOException ex) {
            // Not expected to happen as the file has been tested to exist
           throw new StressTestException("Cannot resolve executable path: "+ex.getMessage(), ex);
        }
        command.addAll(executableArguments);
        return command;
    }

    /**
     * Wrap the random generator with an {@link IntProvider} that will reverse the byte order
     * of the {@code int}.
     *
     * @param rng the random generator.
     * @return the uniform random provider.
     * @see Integer#reverseBytes(int)
     */
    private static UniformRandomProvider createReverseIntProvider(final UniformRandomProvider rng) {
        // Note:
        // This always uses an IntProvider even if the underlying RNG is a LongProvider.
        // A LongProvider will produce 2 ints from 8 bytes of a long: 76543210 -> 7654 3210.
        // This will be reversed to output 2 ints as: 4567 0123.
        // This is a different output order than if reversing the entire long: 0123 4567.
        // The effect is to output the most significant bits from the long first, and
        // the least significant bits second. Thus the output of ints will be the same
        // on big-endian and little-endian platforms.
        return new IntProvider() {
            @Override
            public int next() {
                return Integer.reverseBytes(rng.nextInt());
            }

            @Override
            public String toString() {
                return rng.toString();
            }
        };
    }

    /**
     * Pipes random numbers to the standard input of an analyzer.
     */
    private static class StressTestTask implements Runnable {
        /** Comment prefix. */
        private static final String C = "# ";
        /** New line. */
        private static final String N = System.lineSeparator();
        /** The date format. */
        private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
        /** The SI units for bytes in increments of 10^3. */
        private static final String[] SI_UNITS = {"B", "kB", "MB", "GB", "TB", "PB", "EB"};
        /** The SI unit base for bytes (10^3). */
        private static final long SI_UNIT_BASE = 1000;
        /** The timeout to wait for the process exit value in milliseconds. */
        private static final long TIMEOUT = 1000L;
        /** The default exit value to use when the process has not terminated. */
        private static final int DEFAULT_EXIT_VALUE = -404;

        /** RNG to be tested. */
        private final UniformRandomProvider rng;
        /** Output report file of the sub-process. */
        private final File output;
        /** The sub-process command to run. */
        private final List<String> command;
        /** The run command. */
        private final RunCommand cmd;

        /** The count of numbers used by the sub-process. */
        private long numbersUsed;

        /**
         * Creates the task.
         *
         * @param rng RNG to be tested.
         * @param output Output report file.
         * @param command The sub-process command to run.
         * @param cmd The run command.
         */
        StressTestTask(UniformRandomProvider rng,
             File output,
             List<String> command,
             RunCommand cmd) {
            this.rng = rng;
            this.output = output;
            this.command = command;
            this.cmd = cmd;
        }

        /** {@inheritDoc} */
        @Override
        public void run() {
            try {
                printHeader(output, rng, command, cmd.outputMode == RunCommand.OutputMode.APPEND);

                int exitValue;
                long nanoTime;
                if (cmd.dryRun) {
                    // Do not do anything
                    exitValue = 0;
                    nanoTime = 0;
                } else {
                    // Run the sub-process
                    final long startTime = System.nanoTime();
                    exitValue = runSubProcess();
                    nanoTime = System.nanoTime() - startTime;
                }

                printFooter(output, nanoTime, exitValue, numbersUsed);

            } catch (IOException ex) {
                throw new StressTestException("Failed to start task: " + ex.getMessage(), ex);
            }
        }

        /**
         * Run the analyzer sub-process command.
         *
         * @return The exit value.
         * @throws IOException Signals that an I/O exception has occurred.
         */
        private int runSubProcess() throws IOException {
            // Start test suite.
            final ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(output));
            builder.redirectErrorStream(true);
            final Process testingProcess = builder.start();
            final DataOutputStream sink = new DataOutputStream(
                new BufferedOutputStream(testingProcess.getOutputStream()));


            try {
                while (true) {
                    sink.writeInt(rng.nextInt());
                    numbersUsed++;
                }
            } catch (IOException e) {
                // Hopefully getting here when the analyzing software terminates.
            }

            // Get the exit value
            return getExitValue(testingProcess);
        }

        /**
         * Get the exit value from the process, waiting at most for 1 second, otherwise kill the process
         * and return a dummy value.
         *
         * @param process the process.
         * @return the exit value.
         * @see Process#destroy()
         */
        private static int getExitValue(Process process) {
            long startTime = System.currentTimeMillis();
            long remaining = TIMEOUT;

            while (remaining > 0) {
                try {
                    return process.exitValue();
                } catch (IllegalThreadStateException ex) {
                    try {
                        Thread.sleep(Math.min(remaining + 1, 100));
                    } catch (InterruptedException e) {
                        // Reset interrupted status and stop waiting
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                remaining = TIMEOUT - (System.currentTimeMillis() - startTime);
            }

            // Not finished so kill it
            process.destroy();

            return DEFAULT_EXIT_VALUE;
        }

        /**
         * Prints the header.
         *
         * @param output File.
         * @param rng Generator being tested.
         * @param command The analyzer command.
         * @param append Set to true to append to the output file.
         * @throws IOException if there was a problem opening or writing to the
         * {@code output} file.
         */
        private static void printHeader(File output,
                                        UniformRandomProvider rng,
                                        List<String> command,
                                        boolean append)
            throws IOException {
            final StringBuilder sb = new StringBuilder();
            sb.append(C).append(N);
            sb.append(C).append("RNG: ").append(rng.toString()).append(N);
            sb.append(C).append(N);
            sb.append(C).append("Java: ").append(System.getProperty("java.version")).append(N);
            sb.append(C).append("Runtime: ").append(System.getProperty("java.runtime.version", "?")).append(N);
            sb.append(C).append("JVM: ").append(System.getProperty("java.vm.name"))
                .append(' ').append(System.getProperty("java.vm.version")).append(N);
            sb.append(C).append("OS: ").append(System.getProperty("os.name"))
                .append(' ').append(System.getProperty("os.version"))
                .append(' ').append(System.getProperty("os.arch")).append(N);
            sb.append(C).append(N);

            sb.append(C).append("Analyzer: ");
            for (String s : command) {
                sb.append(s).append(' ');
            }
            sb.append(N);
            sb.append(C).append(N);

            appendDate(sb, "Start");

            try (BufferedWriter w = new BufferedWriter(new FileWriter(output, append))) {
                w.write(sb.toString());
            }
        }

        /**
         * Prints the footer.
         *
         * @param output File.
         * @param nanoTime Duration of the run.
         * @param exitValue The process exit value.
         * @param numbersUsed The count of numbers piped to the executable.
         * @throws IOException if there was a problem opening or writing to the
         * {@code output} file.
         */
        private static void printFooter(File output,
                                        long nanoTime,
                                        int exitValue,
                                        long numbersUsed)
            throws IOException {
            final StringBuilder sb = new StringBuilder();
            sb.append(C).append(N);

            appendDate(sb, "End");

            sb.append(C).append("Exit value: ").append(exitValue).append(N);
            sb.append(C).append("Numbers used: ").append(numbersUsed)
                        .append(" >= 2^").append(log2(numbersUsed))
                        .append(" (").append(bytesToString(numbersUsed * 4)).append(')').append(N);
            sb.append(C).append(N);

            final double duration = nanoTime * 1e-9 / 60;
            sb.append(C).append("Test duration: ").append(duration).append(" minutes").append(N);

            sb.append(C).append(N);

            try (BufferedWriter w = new BufferedWriter(new FileWriter(output, true))) {
                w.write(sb.toString());
            }
        }

        /**
         * Append a comment with the current date to the {@link StringBuilder}.
         *
         * <pre>
         * # [prefix]: yyyy-MM-dd HH:mm:ss
         * #
         * </pre>
         *
         * @param sb the StringBuilder.
         * @param prefix the prefix used before the formatted date, e.g. "Start".
         */
        private static void appendDate(StringBuilder sb,
                                       String prefix) {
            // Use local date format. It is not thread safe.
            final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
            sb.append(C).append(prefix).append(": ").append(dateFormat.format(new Date())).append(N);
            sb.append(C).append(N);
        }

        /**
         * Convert bytes to a human readable string. Example output:
         *
         * <pre>
         *                              SI
         *                   0:        0 B
         *                  27:       27 B
         *                 999:      999 B
         *                1000:     1.0 kB
         *                1023:     1.0 kB
         *                1024:     1.0 kB
         *                1728:     1.7 kB
         *              110592:   110.6 kB
         *             7077888:     7.1 MB
         *           452984832:   453.0 MB
         *         28991029248:    29.0 GB
         *       1855425871872:     1.9 TB
         * 9223372036854775807:     9.2 EB   (Long.MAX_VALUE)
         * </pre>
         *
         * @param bytes the bytes
         * @return the string
         * @see <a
         * href="https://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java">How
         *      to convert byte size into human readable format in java?</a>
         */
        public static String bytesToString(final long bytes) {
          // When using the smallest unit no decimal point is needed, because it's the exact number.
          if (bytes < 1000) {
            return bytes + " " + SI_UNITS[0];
          }

          final int exponent = (int) (Math.log(bytes) / Math.log(SI_UNIT_BASE));
          final String unit = SI_UNITS[exponent];
          return String.format(Locale.US, "%.1f %s", bytes / Math.pow(SI_UNIT_BASE, exponent), unit);
        }

        /**
         * Return the log2 of a {@code long} value rounded down to a power of 2:
         *
         * @param x the value
         * @return {@code floor(log2(x))}
         */
        public static int log2(long x) {
          return 63 - Long.numberOfLeadingZeros(x);
        }
    }
}
