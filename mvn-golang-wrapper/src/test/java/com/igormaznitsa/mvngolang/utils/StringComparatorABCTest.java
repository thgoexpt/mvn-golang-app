/*
 * Copyright 2016 Igor Maznitsa.
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
package com.igormaznitsa.mvngolang.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

public class StringComparatorABCTest {

    @Test
    public void testSorting() {
        final List<String> list = Arrays.asList("b", "c", "1", "hello", "ABC", "A");
        Collections.sort(list, StringComparatorABC.getInstance());
        assertArrayEquals(new String[]{"1", "A", "ABC", "b", "c", "hello"}, list.toArray());
    }

}
