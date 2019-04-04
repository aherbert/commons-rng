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

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * Specification for the "properties" command.
 *
 * <p>This command shows the known Java system properties.</p>
 *
 * @see System#getProperties()
 */
@Command(name = "properties",
         description = "List the current Java properties.")
class PropertiesCommand implements Callable<Void> {
    /**
     * The list of standard java properties.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/7/docs/api/java/lang/System.html#getProperties()">Java
     * 1.7 System.getProperties</a>
     */
    private static final List<String> STANDARD_PROPERTIES = Arrays.asList(
        "java.version",
        "java.vendor",
        "java.vendor.url",
        "java.home",
        "java.vm.specification.version",
        "java.vm.specification.vendor",
        "java.vm.specification.name",
        "java.vm.version",
        "java.vm.vendor",
        "java.vm.name",
        "java.specification.version",
        "java.specification.vendor",
        "java.specification.name",
        "java.class.version",
        "java.class.path",
        "java.library.path",
        "java.io.tmpdir",
        "java.compiler",
        "java.ext.dirs",
        "os.name",
        "os.arch",
        "os.version",
        "file.separator",
        "path.separator",
        "line.separator",
        "user.name",
        "user.home",
        "user.dir"
    );

    /** The escape map for string output. */
    private static final Map<Character, String> ESCAPE_MAP;

    static {
        ESCAPE_MAP = new HashMap<>();
        ESCAPE_MAP.put('\"', "\\\"");
        ESCAPE_MAP.put('\\', "\\\\");
        ESCAPE_MAP.put('\b', "\\b");
        ESCAPE_MAP.put('\n', "\\n");
        ESCAPE_MAP.put('\t', "\\t");
        ESCAPE_MAP.put('\f', "\\f");
        ESCAPE_MAP.put('\r', "\\r");
    }

    /** The standard options. */
    @Mixin
    private StandardOptions reusableOptions;

    /** The flag to indicate a dry run. */
    @Option(names = { "--standard" }, description = "Print only the standard system properties.")
    private boolean standard = false;

    /** The flag to indicate a dry run. */
    @Option(names = { "--max" }, description = { "The maximum length for a property.",
        "Properties longer than this are truncated" })
    private int maxLength = 50;

    /**
     * Validates the run command arguments, creates the list of generators and runs the
     * stress test tasks.
     */
    @Override
    public Void call() {
        printProperties(System.out);
        return null;
    }

    /**
     * Prints the properties.
     *
     * @param out The output stream.
     */
    private void printProperties(PrintStream out) {
        final Properties properties = System.getProperties();

        out.println("-- Standard properties --");
        printProperties(out, properties, STANDARD_PROPERTIES);

        if (standard) {
            // Only show standard properties
            return;
        }

        // Get all properties and remove the standard ones
        final HashSet<String> keys = new HashSet<>(properties.stringPropertyNames());
        keys.removeAll(STANDARD_PROPERTIES);

        if (keys.isEmpty()) {
            // None left
            return;
        }

        List<String> list = Arrays.asList(keys.toArray(new String[0]));
        Collections.sort(list);

        out.println("-- Platform properties --");
        printProperties(out, properties, list);
    }

    /**
     * Prints the properties for the specified keys.
     *
     * @param out The output stream.
     * @param properties The properties.
     * @param keys The keys.
     */
    private void printProperties(PrintStream out, Properties properties, List<String> keys) {
        final int keyLength = maxLength(keys);
        String format = String.format("%%-%ds = \"%%s\"%%n", keyLength);
        for (String key : keys) {
            // Escape the strings.
            String value = escapeJava(String.valueOf(properties.get(key)));
            // Check the length
            if (value.length() > maxLength) {
                value = value.substring(0, maxLength) + "...";
            }
            out.printf(format, key, value);
        }
    }

    /**
     * Get the max {@link String#length()} of the collection.
     *
     * @param collection The collection.
     * @return the max length
     */
    private static int maxLength(Collection<String> collection) {
        int max = 0;
        for (String item : collection) {
            max = Math.max(max, item.length());
        }
        return max;
    }

    /**
     * Escapes the characters in a String using Java String rules.
     *
     * <p>This only deals with the following characters:
     * {@code \", \\, \b, \n, \t, \f, \r}.</p>
     *
     * @param input the input
     * @return the string
     */
    private static String escapeJava(String input) {
        // This could be replaced by Commons Text StringEscapeUtils::escapeJava

        // Java control characters
        final StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            final char ch = input.charAt(i);
            final String mapped = ESCAPE_MAP.get(ch);
            if (mapped != null) {
                sb.append(mapped);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
