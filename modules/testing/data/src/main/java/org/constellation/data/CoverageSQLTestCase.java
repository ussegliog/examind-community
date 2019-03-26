/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2014 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.constellation.data;

// J2SE dependencies

import org.geotoolkit.nio.IOUtilities;
import org.junit.BeforeClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.*;

// JUnit dependencies


/**
 * Initialize the data set of images into the temporary directory of the user.
 *
 * @version $Id$
 *
 * @author Cédric Briançon (Geomatys)
 * @since 0.3
 */
public abstract class CoverageSQLTestCase {
    /**
     * The temporary directory on a computer, according to the environment.
     */
    private static final Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));

    /**
     * Initialize the image folder into the temporary directory, and copy in it the
     * image in resources, if it is not already present.
     *
     * @throws IOException
     */
    @BeforeClass
    public static void init() throws IOException {
        // Creation of the temporary directory for Constellation data if not already created.
        final Path sstDir = tmpDir.resolve("Constellation/images/Monde/SST");
        final boolean fileNeedsToBeWritten = createDirectory(sstDir);

        // Write the image in resources into the temporary directory previously created.
        if (fileNeedsToBeWritten) {
            writeFile(sstDir, "SSTMDE200305.png");
            writeFile(sstDir, "SSTMDE200305.prj");
            writeFile(sstDir, "SSTMDE200305.tfw");
        }
    }
    
    private static void writeFile(final Path sstDir, String fileName) throws IOException {
        try (final InputStream in = CoverageSQLTestCase.class.getResourceAsStream(fileName)) {
            try (final OutputStream os = Files.newOutputStream(sstDir.resolve(fileName), CREATE, WRITE, TRUNCATE_EXISTING)) {
                IOUtilities.copy(in, os);
            }
        }
    }

    /**
     * Creates a directory in the temporary folder if it is allowed by the writing permissions.
     *
     * @param dir One or several subdirectories to generate in the temporary folder.
     * @return True if it has created something, false if nothing has been written.
     *
     * @throws IOException if the writing permissions do not allow to create that folder.
     */
    private static boolean createDirectory(final Path dir) throws IOException {
        if (!Files.exists(dir)) {
            if (!Files.isWritable(tmpDir)) {
                throw new IOException("Unable to write into the directory: "+ tmpDir.toString() +
                                      ". Please check writing permissions.");
            } else {
                Files.createDirectories(dir);
                return true;
            }
        }
        return false;
    }
}
