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

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Specification for the "stress" command.
 *
 * <p>This command loads a list of random generators and tests each generator by
 * piping the values returned by its {@link UniformRandomProvider#nextInt()}
 * method to a program that reads {@code int} values from its standard input and
 * writes an analysis report to standard output.
 */
@Command(name = "stress",
         description = { "Run repeat trials of random data generators using a provided test application.",
                         "Data is transferred to the application sub-process via standard input." })
class StressTestCommand implements Callable<Void> {
    /** The standard options. */
    @Mixin
    private StandardOptions reusableOptions;

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
            description = {"Output mode for existing files (default: ${DEFAULT-VALUE}).",
                           "Valid values: ${COMPLETION-CANDIDATES}"})
    private StressTestCommand.OutputMode outputMode = OutputMode.ERROR;

    /** The list of random generators. */
    @Option(names = {"-l", "--list"},
            description = {"List of random generators.",
                           "The default list is all known generators." },
            paramLabel = "<genList>")
    private File generatorsListFile;

    /** The number of trials to put in the template list of random generators. */
    @Option(names = {"-t", "--trials"},
            description = {"The number of trials for each random generator.",
                           "Used only for the default list (default: ${DEFAULT-VALUE})."})
    private int trials = 1;

    /** The number of concurrent tasks. */
    @Option(names = {"-n", "--tasks"},
            description = {"Number of concurrent tasks (default: ${DEFAULT-VALUE}).",
                           "Two threads are required per task." })
    private int taskCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    /** The output byte order of the binary data. */
    @Option(names = {"-b", "--byte-order"},
            description = {"Byte-order of the transferred data (default: ${DEFAULT-VALUE}).",
                           "Valid values: BIG_ENDIAN, LITTLE_ENDIAN." })
    private ByteOrder byteOrder = ByteOrder.nativeOrder();

    /** The output byte order of the binary data. */
    @Option(names = {"-r", "--reverse-bits"},
            description = {"Reverse the bits in the data (default: ${DEFAULT-VALUE}).",
                           "Note: Generators may fail tests for a reverse sequence " +
                           "when passing using the standard sequence." })
    private boolean reverseBits;

    /** The flag to indicate a dry run. */
    @Option(names = {"--dry-run"},
            description = "Perform a dry run where the generators and output files are created " +
                          "but the stress test is not executed.")
    private boolean dryRun = false;

    /**
     * The output mode for existing files.
     */
    enum OutputMode {
        /** Error if the files exists. */
        ERROR,
        /** Skip existing files. */
        SKIP,
        /** Append to existing files. */
        APPEND,
        /** Overwrite existing files. */
        OVERWRITE
    }

    /**
     * Validates the run command arguments, creates the list of generators and runs the
     * stress test tasks.
     */
    @Override
    public Void call() {
        LogUtils.setLogLevel(reusableOptions.logLevel);
        checkExecutable(executable);
        checkOutputDirectory(fileOutputPrefix);
        final Iterable<StressTestData> stressTestData = createStressTestData();
        printStressTestData(stressTestData);
        runStressTest(stressTestData);
        return null;
    }

    /**
     * Check the executable exists and has execute permissions.
     *
     * @param executable The executable.
     * @throws ApplicationException If the executable is invalid.
     */
    private static void checkExecutable(File executable) {
        if (!executable.exists() ||
            !executable.canExecute()) {
            throw new ApplicationException("Program is not executable: " + executable);
        }
    }

    /**
     * Check the output directory exists and has write permissions.
     *
     * @param fileOutputPrefix The file output prefix.
     * @throws ApplicationException If the output directory is invalid.
     */
    private static void checkOutputDirectory(File fileOutputPrefix) {
        final File reportDir = fileOutputPrefix.getAbsoluteFile().getParentFile();
        if (!reportDir.exists() ||
            !reportDir.isDirectory() ||
            !reportDir.canWrite()) {
            throw new ApplicationException("Invalid output directory: " + reportDir);
        }
    }

    /**
     * Creates the test data.
     *
     * <p>If the input file is null then a default list is created.
     *
     * @return the stress test data
     * @throws ApplicationException if an error occurred during the file read.
     */
    private Iterable<StressTestData> createStressTestData() {
        if (generatorsListFile == null) {
            return new StressTestDataList("", trials);
        }
        // Read data into a list
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(generatorsListFile), StandardCharsets.UTF_8))) {
            return ListCommand.readStressTestData(reader);
        } catch (final IOException ex) {
            throw new ApplicationException("Failed to read generators list: " + generatorsListFile, ex);
        }
    }

    /**
     * Prints the stress test data if the verbosity allows. This is used to debug the list
     * of generators to be tested.
     *
     * @param stressTestData List of generators to be tested.
     */
    private static void printStressTestData(Iterable<StressTestData> stressTestData) {
        if (!LogUtils.isLoggable(LogUtils.LogLevel.DEBUG)) {
            return;
        }
        try {
            final StringBuilder sb = new StringBuilder("Testing generators").append(System.lineSeparator());
            ListCommand.writeStressTestData(sb, stressTestData);
            LogUtils.debug(sb.toString());
        } catch (final IOException ex) {
            throw new ApplicationException("Failed to show list of generators", ex);
        }
    }

    /**
     * Creates the tasks and starts the processes.
     *
     * @param stressTestData List of generators to be tested.
     */
    private void runStressTest(Iterable<StressTestData> stressTestData) {
        final ArrayList<String> command = buildSubProcessCommand(executable, executableArguments);

        // Check existing output files before starting the tasks.
        final String basePath = fileOutputPrefix.getAbsolutePath();
        checkExistingOutputFiles(basePath, stressTestData);

        LogUtils.info("Running stress test ...");
        final ProgressTracker progressTracker = new ProgressTracker(countTrials(stressTestData));

        // Run tasks with parallel execution.
        final ExecutorService service = Executors.newFixedThreadPool(taskCount);
        final List<Future<?>> taskList = new ArrayList<>();
        for (final StressTestData testData : stressTestData) {
            submitTasks(service, taskList, command, basePath, testData, progressTracker);
        }

        // Wait for completion (ignoring return value).
        try {
            for (final Future<?> f : taskList) {
                try {
                    f.get();
                } catch (final ExecutionException ex) {
                    // Log the error. Do not re-throw as other tasks may be processing that
                    // can still complete successfully.
                    LogUtils.error(ex.getCause(), ex.getMessage());
                }
            }
        } catch (final InterruptedException ex) {
            // Restore interrupted state...
            Thread.currentThread().interrupt();
            throw new ApplicationException("Unexpected interruption: " + ex.getMessage(), ex);
        } finally {
            // Terminate all threads.
            service.shutdown();
        }
    }

    /**
     * Builds the command for the sub-process.
     *
     * @param executable The executable file.
     * @param executableArguments The executable arguments.
     * @return the command
     * @throws ApplicationException If the executable path cannot be resolved
     */
    private static ArrayList<String> buildSubProcessCommand(File executable,
                                                            List<String> executableArguments) {
        final ArrayList<String> command = new ArrayList<>();
        try {
            command.add(executable.getCanonicalPath());
        } catch (final IOException ex) {
            // Not expected to happen as the file has been tested to exist
           throw new ApplicationException("Cannot resolve executable path: " + ex.getMessage(), ex);
        }
        command.addAll(executableArguments);
        return command;
    }

    /**
     * Check for existing output files.
     *
     * @param basePath The base path to the output results files.
     * @param stressTestData List of generators to be tested.
     * @throws ApplicationException If an output file exists and the output mode is error
     */
    private void checkExistingOutputFiles(final String basePath,
                                          Iterable<StressTestData> stressTestData) {
        if (outputMode == StressTestCommand.OutputMode.ERROR) {
            for (final StressTestData testData : stressTestData) {
                for (int trial = 1; trial <= testData.getTrials(); trial++) {
                    // Create the output file
                    final File output = createOutputFile(basePath, testData, trial);
                    if (output.exists()) {
                        throw new ApplicationException(createExistingFileMessage(output));
                    }
                }
            }
        }
    }

    /**
     * Creates the named output file.
     *
     * @param basePath The base path to the output results files.
     * @param testData The test data.
     * @param trial The trial.
     * @return the file
     */
    private static File createOutputFile(final String basePath,
                                         final StressTestData testData,
                                         final int trial) {
        return new File(String.format("%s%s_%d", basePath, testData.getId(), trial));
    }

    /**
     * Creates the existing file message.
     *
     * @param output The output file.
     * @return the message
     */
    private static String createExistingFileMessage(File output) {
        return "Existing output file: " + output;
    }

    /**
     * Count the total number of trials.
     *
     * @param stressTestData List of generators to be tested.
     * @return the count
     */
    private static int countTrials(Iterable<StressTestData> stressTestData) {
        int count = 0;
        for (final StressTestData testData : stressTestData) {
            count += Math.max(0, testData.getTrials());
        }
        return count;
    }

    /**
     * Submit the tasks for the test data to the executor service. The output file for the
     * sub-process will be constructed using the base path, the test identifier and the
     * trial number.
     *
     * @param service The executor service.
     * @param taskList The list of submitted tasks.
     * @param command The command for the test application.
     * @param basePath The base path to the output results files.
     * @param testData The test data.
     * @param progressTracker The progress tracker.
     */
    private void submitTasks(final ExecutorService service,
                             final List<Future<?>> taskList,
                             final ArrayList<String> command,
                             final String basePath,
                             final StressTestData testData,
                             ProgressTracker progressTracker) {
        for (int trial = 1; trial <= testData.getTrials(); trial++) {
            // Create the output file
            final File output = createOutputFile(basePath, testData, trial);
            if (output.exists()) {
                // In case the file was created since the last check
                if (outputMode == StressTestCommand.OutputMode.ERROR) {
                    throw new ApplicationException(createExistingFileMessage(output));
                }
                // Log the decision
                LogUtils.info("%s existing output file: %s", outputMode, output);
                if (outputMode == StressTestCommand.OutputMode.SKIP) {
                    continue;
                }
            }
            // Create the generator
            UniformRandomProvider rng = testData.createRNG();
            if (byteOrder.equals(ByteOrder.LITTLE_ENDIAN)) {
                rng = createReverseBytesIntProvider(rng);
            }
            if (reverseBits) {
                rng = createReverseBitsIntProvider(rng);
            }
            // Run the test
            final Runnable r = new StressTestTask(rng, output, command, this, progressTracker);
            taskList.add(service.submit(r));
        }
    }

    /**
     * Wrap the random generator with an {@link IntProvider} that will reverse the byte order
     * of the {@code int}.
     *
     * @param rng The random generator.
     * @return the byte reversed random generator.
     * @see Integer#reverseBytes(int)
     */
    private static UniformRandomProvider createReverseBytesIntProvider(final UniformRandomProvider rng) {
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
                return "Byte-reversed " + rng.toString();
            }
        };
    }

    /**
     * Wrap the random generator with an {@link IntProvider} that will reverse the bits
     * of the {@code int}.
     *
     * @param rng The random generator.
     * @return the bit reversed random generator.
     * @see Integer#reverse(int)
     */
    private static UniformRandomProvider createReverseBitsIntProvider(final UniformRandomProvider rng) {
        return new IntProvider() {
            @Override
            public int next() {
                return Integer.reverse(rng.nextInt());
            }

            @Override
            public String toString() {
                return "Bit-reversed " + rng.toString();
            }
        };
    }

    /**
     * Class for reporting total progress to the console.
     */
    static class ProgressTracker {
        /** The reporting interval. */
        private static final long REPORT_INTERVAL= 100;
        /** The total. */
        private final int total;
        /** The count. */
        private int count;
        /** The timestamp of the last progress report. */
        private long timestamp;

        /**
         * Create a new instance.
         *
         * @param total The total progress.
         */
        ProgressTracker(int total) {
            this.total = total;
            showProgress();
        }

        /**
         * Increment the progress.
         */
        synchronized void incrementProgress() {
            count++;
            showProgress();
        }

        /**
         * Show the progress. This will occur incrementally based on the current time
         * or if the progress is complete.
         */
        private void showProgress() {
            // Edge case. This handles 0 / 0 as 100%.
            if (count >= total) {
                LogUtils.info("Progress %d / %d (100%%)", total, total);
                return;
            }
            final long current = System.currentTimeMillis();
            if (current - timestamp > REPORT_INTERVAL) {
                timestamp = current;
                LogUtils.info("Progress %d / %d (%.2f%%)", count, total, 100.0 * count / total);
            }
        }
    }

    /**
     * Pipes random numbers to the standard input of an analyzer executable.
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
        /** The stress test command. */
        private final StressTestCommand cmd;
        /** The progress tracker. */
        private final ProgressTracker progressTracker;

        /** The count of numbers used by the sub-process. */
        private long numbersUsed;

        /**
         * Creates the task.
         *
         * @param rng RNG to be tested.
         * @param output Output report file.
         * @param command The sub-process command to run.
         * @param cmd The run command.
         * @param progressTracker The progress tracker.
         */
        StressTestTask(UniformRandomProvider rng,
             File output,
             List<String> command,
             StressTestCommand cmd,
             ProgressTracker progressTracker) {
            this.rng = rng;
            this.output = output;
            this.command = command;
            this.cmd = cmd;
            this.progressTracker = progressTracker;
        }

        /** {@inheritDoc} */
        @Override
        public void run() {
            try {
                printHeader(output, rng, command, cmd.outputMode == StressTestCommand.OutputMode.APPEND);

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

            } catch (final IOException ex) {
                throw new ApplicationException("Failed to run task: " + ex.getMessage(), ex);
            } finally {
                progressTracker.incrementProgress();
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
            } catch (final IOException e) {
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
            final long startTime = System.currentTimeMillis();
            long remaining = TIMEOUT;

            while (remaining > 0) {
                try {
                    return process.exitValue();
                } catch (final IllegalThreadStateException ex) {
                    try {
                        Thread.sleep(Math.min(remaining + 1, 100));
                    } catch (final InterruptedException e) {
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
            for (final String s : command) {
                sb.append(s).append(' ');
            }
            sb.append(N);
            sb.append(C).append(N);

            appendDate(sb, "Start");

            write(sb, output, append);
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

            write(sb, output, true);
        }

        /**
         * Write the string builder to the output file.
         *
         * @param sb The string builder.
         * @param output The output file.
         * @param append Set to {@code true} to append to the file.
         * @throws IOException Signals that an I/O exception has occurred.
         */
        private static void write(StringBuilder sb,
                                  File output,
                                  boolean append) throws IOException {
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(output, append), StandardCharsets.UTF_8))) {
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
        static String bytesToString(final long bytes) {
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
        static int log2(long x) {
          return 63 - Long.numberOfLeadingZeros(x);
        }
    }
}
