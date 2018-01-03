/*
 * Copyright 2017 Igor Maznitsa.
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

import com.igormaznitsa.meta.annotation.MayContainNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Auxiliary class to collect methods for work with IO.
 *
 * @since 2.1.7
 */
public final class IOUtils {

  private IOUtils() {

  }

  /**
   * Make file path appropriate for current OS.
   *
   * @param files files which will be added in the path
   * @return joined file path with OS file separator
   * @since 2.1.7
   */
  @Nonnull
  public static String makeOsFilePathWithoutDuplications(@Nonnull @MayContainNull final File[] files) {
    final StringBuilder result = new StringBuilder();
    final Set<File> alreadyAdded = new HashSet<>();

    for (final File f : files) {
      if (f == null || alreadyAdded.contains(f)) {
        continue;
      }
      alreadyAdded.add(f);
      if (result.length() > 0) {
        result.append(File.pathSeparatorChar);
      }
      result.append(f.getAbsolutePath());
    }

    return result.toString();
  }


  /**
   * Make file path from provided strings
   * @param paths path elements
   * @return joined file path with OS file separator
   * @since 2.1.7
   */
  @Nonnull
  public static String makeOsFilePathWithoutDuplications(@Nonnull @MayContainNull final String... paths) {
    final StringBuilder result = new StringBuilder();
    final Set<String> alreadyAdded = new HashSet<>();

    for (final String s : paths) {
      if (s != null && !s.isEmpty() && !alreadyAdded.contains(s)) {
        alreadyAdded.add(s);
        if (result.length() > 0) {
          result.append(File.pathSeparatorChar);
        }
        result.append(s);
      }
    }
    return result.toString();
  }

  /**
   * Close a closeable object quietly, added because such method in APACHE-IO
   * has been deprecated
   *
   * @param closeable object to be closed
   */
  public static void closeSilently(@Nullable final Closeable closeable) {
    try {
      if (closeable != null) {
        closeable.close();
      }
    } catch (final IOException ex) {
    }
  }
}
