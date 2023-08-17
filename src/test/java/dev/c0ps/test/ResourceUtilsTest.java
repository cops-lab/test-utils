/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.c0ps.test;

import static dev.c0ps.test.ResourceUtils.getResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ResourceUtilsTest {

    @Test
    public void relativeFiles() {
        assertTrue(getResource("somefile.txt").exists());
    }

    @Test
    public void filesInSubfolders() {
        assertTrue(getResource("subfolder/otherfile.txt").exists());
    }

    @Test
    public void fileNotFound() {
        var e = assertThrows(IllegalArgumentException.class, () -> {
            assertTrue(getResource("doesNotExist.txt").exists());
        });
        assertEquals("Test resource not found: doesNotExist.txt", e.getMessage());
    }
}