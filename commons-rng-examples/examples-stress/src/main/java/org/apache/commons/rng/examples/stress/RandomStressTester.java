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

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.core.source32.IntProvider;

/**
 * Class that can be used for testing a generator by piping the values
 * returned by its {@link UniformRandomProvider#nextInt()} method to a
 * program that reads {@code int} values from its standard input and
 * writes an analysis report to standard output.
 *
 * The <a href="http://www.phy.duke.edu/~rgb/General/dieharder.php">
 * "dieharder"</a> test suite is such a software.
 *
 * Example of command line, assuming that "examples.jar" specifies this
 * class as the "main" class (see {@link #main(String[]) main} method):
 * <pre><code>
 *  $ java -jar examples.jar \
 *    report/dh_ \
 *    4 \
 *    org.apache.commons.rng.examples.stress.GeneratorsList \
 *    LE \
 *    /usr/bin/dieharder -a -g 200 -Y 1 -k 2
 * </code></pre>
 */
public class RandomStressTester {
    /** Comment prefix. */
    private static final String C = "# ";
    /** New line. */
    private static final String N = System.lineSeparator();
    /** The date format. */
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    /** Command line. */
    private final List<String> cmdLine;
    /** Output prefix. */
    private final String fileOutputPrefix;

    /**
     * Creates the application.
     *
     * @param cmd Command line.
     * @param outputPrefix Output prefix for file reports.
     */
    private RandomStressTester(List<String> cmd,
                               String outputPrefix) {
        final File exec = new File(cmd.get(0));
        if (!exec.exists() ||
            !exec.canExecute()) {
            throw new IllegalArgumentException("Program is not executable: " + exec);
        }

        cmdLine = new ArrayList<String>(cmd);
        fileOutputPrefix = outputPrefix;

        final File reportDir = new File(fileOutputPrefix).getAbsoluteFile().getParentFile();
        if (!reportDir.exists() ||
            !reportDir.isDirectory() ||
            !reportDir.canWrite()) {
            throw new IllegalArgumentException("Invalid output directory: " + reportDir);
        }
    }

    /**
     * Program's entry point.
     *
     * @param args Application's arguments.
     * The order is as follows:
     * <ol>
     *  <li>Output prefix: Filename prefix where the output of the analysis will
     *   written to.  The appended suffix is the index of the instance within the
     *   list of generators to be tested.</li>
     *  <li>Number of threads to use concurrently: One thread will process one of
     *    the generators to be tested.</li>
     *  <li>Name of a class that implements {@code Iterable<UniformRandomProvider>}
     *   (and defines a default constructor): Each generator of the list will be
     *   tested by one instance of the analyzer program</li>
     *  <li>Endianness: LE for little-endian (outout bytes will be reversed) or BE for big endian
     *   (uses standard network byte order for transmission).</li>
     *  <li>Path to the executable: this is the analyzer software that reads 32-bits
     *   integers from stdin.</li>
     *  <li>All remaining arguments are passed to the executable.</li>
     * </ol>
     */
    public static void main(String[] args) {
        if (args.length < 5) {
            printUsageAndExit();
        }
        final String output = args[0];
        final int numThreads = Integer.parseInt(args[1]);

        final Iterable<UniformRandomProvider> rngList = createGeneratorsList(args[2]);

        final boolean littleEndian = parseEndianness(args[3]);

        final List<String> cmdLine = new ArrayList<String>();
        cmdLine.addAll(Arrays.asList(Arrays.copyOfRange(args, 4, args.length)));

        final RandomStressTester app = new RandomStressTester(cmdLine, output);

        // Throws runtime exceptions
        app.run(rngList, numThreads, littleEndian);
    }

    /**
     * Prints the program usage and exit.
     */
    private static void printUsageAndExit() {
        // @CHECKSTYLE: stop all
        System.out.println(RandomStressTester.class.getSimpleName());
        System.out.println("Program to pipe randomly generated bit data to a subprocess");
        System.out.println("Arguments:");
        System.out.println("path: Output filename prefix");
        System.out.println("GeneratorsList class: Fully qualified class name of a provider");
        System.out.println("                      for the supported random generators");
        System.out.println("endianness: The platform endianess (LE, BE)");
        System.out.println("executable: The test tool");
        System.out.println("...: Arguments for the test tool");
        // @CHECKSTYLE: resume all
        System.exit(1);
    }

    /**
     * Parses the endianness from the {@link String} value. Recognises {@code LE} for little
     * and {@code BE} for big-endian. Parsing is case insensitive.
     *
     * @param value the value.
     * @return {@code true} if little-endian, {@code false} if big-endian.
     * @throws IllegalArgumentException if the string does not contain a parsable endianness.
     */
    private static boolean parseEndianness(String value) {
        if ("LE".equalsIgnoreCase(value)) {
            return true;
        }
        if ("BE".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Unknown endianness: " + value);
    }

    /**
     * Creates the tasks and starts the processes.
     *
     * @param generators List of generators to be analyzed.
     * @param numConcurrentTasks Number of concurrent tasks.
     * Twice as many threads will be started: one thread for the RNG and one
     * for the analyzer.
     * @param littleEndian Set to {@code true} to reverse the output bytes to little-endian.
     */
    private void run(Iterable<UniformRandomProvider> generators,
                     int numConcurrentTasks,
                     boolean littleEndian) {
        // Parallel execution.
        final ExecutorService service = Executors.newFixedThreadPool(numConcurrentTasks);

        // Placeholder (output will be "null").
        final List<Future<?>> execOutput = new ArrayList<Future<?>>();

        // Run tasks.
        int count = 0;
        for (UniformRandomProvider rng : generators) {
            if (littleEndian) {
                rng = createReverseIntProvider(rng);
            }
            final File output = new File(fileOutputPrefix + (++count));
            final Runnable r = new Task(rng, output);
            execOutput.add(service.submit(r));
        }

        // Wait for completion (ignoring return value).
        try {
            for (Future<?> f : execOutput) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    System.err.println(e.getCause().getMessage());
                }
            }
        } catch (InterruptedException ignored) {}

        // Terminate all threads.
        service.shutdown();
    }

    /**
     * Creates the list of generators to be tested.
     *
     * @param name Name of the class that contains the generators to be
     * analyzed.
     * @return the list of generators.
     * @throws IllegalStateException if an error occurs during instantiation.
     */
    @SuppressWarnings("unchecked")
    private static Iterable<UniformRandomProvider> createGeneratorsList(String name) {
        try {
            return (Iterable<UniformRandomProvider>) Class.forName(name).newInstance();
        } catch (ClassNotFoundException|
                 InstantiationException|
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
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
        };
    }

    /**
     * Pipes random numbers to the standard input of an analyzer.
     */
    private class Task implements Runnable {
        /** The timeout to wait for the process exit value in milliseconds. */
        private final long TIMEOUT = 1000000L;
        /** The default exit value to use when the process has not terminated. */
        private final int DEFAULT_EXIT_VALUE = -808080;
        /** Directory for reports of the tester processes. */
        private final File output;
        /** RNG to be tested. */
        private final UniformRandomProvider rng;

        /**
         * Creates the task.
         *
         * @param random RNG to be tested.
         * @param report Report file.
         */
        Task(UniformRandomProvider random,
             File report) {
            rng = random;
            output = report;
        }

        /** {@inheritDoc} */
        @Override
        public void run() {
            try {
                // Write header.
                printHeader(output, rng);

                // Start test suite.
                final ProcessBuilder builder = new ProcessBuilder(cmdLine);
                builder.redirectOutput(ProcessBuilder.Redirect.appendTo(output));
                builder.redirectErrorStream(true);
                final Process testingProcess = builder.start();
                final DataOutputStream sink = new DataOutputStream(
                    new BufferedOutputStream(testingProcess.getOutputStream()));

                final long startTime = System.nanoTime();

                try {
                    while (true) {
                        sink.writeInt(rng.nextInt());
                    }
                } catch (IOException e) {
                    // Hopefully getting here when the analyzing software terminates.
                }

                final long endTime = System.nanoTime();

                // Get the exit value
                final int exitValue = getExitValue(testingProcess);

                // Write footer.
                printFooter(output, endTime - startTime, exitValue);

            } catch (IOException e) {
                throw new RuntimeException("Failed to start task: " + e.getMessage());
            }
        }

        /**
         * Get the exit value from the process, waiting at most for 1 second, otherwise kill the process
         * and return a dummy value.
         *
         * @param process the process.
         * @return the exit value.
         * @see Process#destroy()
         */
        private int getExitValue(Process process) {
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
    }

    /**
     * @param output File.
     * @param rng Generator being tested.
     * @param cmdLine
     * @throws IOException if there was a problem opening or writing to
     * the {@code output} file.
     */
    private void printHeader(File output,
                             UniformRandomProvider rng)
        throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append(C).append(N);
        sb.append(C).append("RNG: ").append(rng.toString()).append(N);
        sb.append(C).append(N);
        sb.append(C).append("Java: ").append(System.getProperty("java.version")).append(N);
        sb.append(C).append("Runtime: ").append(System.getProperty("java.runtime.version", "?")).append(N);
        sb.append(C).append("JVM: ").append(System.getProperty("java.vm.name"))
            .append(" ").append(System.getProperty("java.vm.version")).append(N);
        sb.append(C).append("OS: ").append(System.getProperty("os.name"))
            .append(" ").append(System.getProperty("os.version"))
            .append(" ").append(System.getProperty("os.arch")).append(N);
        sb.append(C).append(N);

        sb.append(C).append("Analyzer: ");
        for (String s : cmdLine) {
            sb.append(s).append(" ");
        }
        sb.append(N);
        sb.append(C).append(N);

        appendDate(sb, "Start");

        try (BufferedWriter w = new BufferedWriter(new FileWriter(output, true))) {
            w.write(sb.toString());
        }
    }

    /**
     * @param output File.
     * @param nanoTime Duration of the run.
     * @param exitValue The process exit value.
     * @throws IOException if there was a problem opening or writing to
     * the {@code output} file.
     */
    private void printFooter(File output,
                             long nanoTime,
                             int exitValue)
        throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append(C).append(N);

        appendDate(sb, "End");

        sb.append(C).append("Exit value: ").append(exitValue).append(N);
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
    private void appendDate(StringBuilder sb,
                            String prefix) {
        // Use local date format. It is not thread safe.
        final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        sb.append(C).append(prefix).append(": ").append(dateFormat.format(new Date())).append(N);
        sb.append(C).append(N);
    }
}
