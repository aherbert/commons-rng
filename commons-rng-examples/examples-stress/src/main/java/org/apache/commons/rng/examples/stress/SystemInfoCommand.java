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

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.concurrent.Callable;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;

/**
 * Specification for the "system" command.
 *
 * <p>This command shows information about the current system including operating system
 * and hardware such as CPU and RAM.</p>
 *
 * @see System#getProperties()
 */
@Command(name = "system",
         description = "Show the system information.")
class SystemInfoCommand implements Callable<Void> {
    /** New line. */
    private static final String N = System.lineSeparator();

    /** The standard options. */
    @Mixin
    private StandardOptions reusableOptions;

    /** The flag to indicate a long output. */
    @Option(names = { "-l", "--long" }, 
            description = "Print a long output.")
    private boolean longOutput = false;

    /**
     * Validates the run command arguments, creates the list of generators and runs the
     * stress test tasks.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Override
    public Void call() throws IOException {
        final StringBuilder sb = new StringBuilder();
        if (longOutput) {
            printProperties(sb);
        } else {
            printSummaryProperties(sb);
        }
        System.out.print(sb.toString());
        return null;
    }

    /**
     * Prints the system properties to the {@link Appendable}.
     *
     * @param sb The appendable.
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private static void printProperties(Appendable sb) throws IOException {
        // Java information
        sb.append("Java: ").append(System.getProperty("java.version")).append(N);
        sb.append("Runtime: ").append(System.getProperty("java.runtime.version", "?")).append(N);
        sb.append("JVM: ").append(System.getProperty("java.vm.name"))
            .append(' ').append(System.getProperty("java.vm.version")).append(N);

        // Use SystemInfo from oshi.
        final SystemInfo si = new SystemInfo();

        final OperatingSystem os = si.getOperatingSystem();
        // This outputs more about the OS that can be found in the System properties:
        // os.name, os.version but misses os.arch so this is appended
        sb.append("OS: ").append(os.toString()).append(System.getProperty("os.arch")).append(N);

        final HardwareAbstractionLayer hal = si.getHardware();

        final ComputerSystem computerSystem = hal.getComputerSystem();
        sb.append("Manufacturer: ").append(computerSystem.getManufacturer()).append(N);
        sb.append("Model: ").append(computerSystem.getModel()).append(N);

        final CentralProcessor processor = hal.getProcessor();
        sb.append("Processor: ").append(processor.toString()).append(N);
        sb.append(' ').append(Integer.toString(processor.getPhysicalPackageCount())).append(" physical CPU package(s)").append(N);
        sb.append(' ').append(Integer.toString(processor.getPhysicalProcessorCount())).append(" physical CPU core(s)").append(N);
        sb.append(' ').append(Integer.toString(processor.getLogicalProcessorCount())).append(" logical CPU(s)").append(N);
        sb.append("Identifier: " + processor.getIdentifier()).append(N);

        final GlobalMemory memory = hal.getMemory();
        sb.append("Memory: ").append(FormatUtil.formatBytes(memory.getAvailable())).append('/')
            .append(FormatUtil.formatBytes(memory.getTotal())).append(N);
    }

    /**
     * Prints the summary system properties to the {@link Appendable}.
     *
     * @param sb The appendable.
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private static void printSummaryProperties(Appendable sb) throws IOException {
        // Java information
        sb.append("Java: ").append(System.getProperty("java.version"))
            .append(' ').append(System.getProperty("java.vm.name"))
            .append(' ').append(System.getProperty("java.vm.version")).append(N);

        // Use SystemInfo from oshi.
        final SystemInfo si = new SystemInfo();

        final OperatingSystem os = si.getOperatingSystem();
        // This outputs more about the OS that can be found in the System properties
        // os.name, os.version but misses os.arch so this is appended
        sb.append("OS: ").append(os.toString()).append(" (Arch ")
            .append(System.getProperty("os.arch")).append(')').append(N);

        final HardwareAbstractionLayer hal = si.getHardware();

        final CentralProcessor processor = hal.getProcessor();
        sb.append("Processor: ").append(processor.toString()).append(" (")
            .append(Integer.toString(processor.getPhysicalPackageCount())).append(" physical CPU, ")
            .append(Integer.toString(processor.getPhysicalProcessorCount())).append(" physical CPU core(s), ")
            .append(Integer.toString(processor.getLogicalProcessorCount())).append(" logical CPU(s))").append(N);

        final GlobalMemory memory = hal.getMemory();
        sb.append("Memory: ").append(FormatUtil.formatBytes(memory.getTotal())).append(N);
    }
}
