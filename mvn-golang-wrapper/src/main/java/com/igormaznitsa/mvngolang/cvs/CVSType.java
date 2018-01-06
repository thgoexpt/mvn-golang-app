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

package com.igormaznitsa.mvngolang.cvs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

public enum CVSType {
  UNKNOWN(new CvsNone()),
  GIT(new CvsGIT()),
  HG(new CvsHG()),
  SVN(new CvsSVN()),
  BAZAAR(new CvsBZR());

  private final AbstractRepo processor;

  CVSType(@Nonnull final AbstractRepo processor) {
    this.processor = processor;
  }

  @Nonnull
  public static CVSType investigateFolder(@Nullable final File folder) {
    CVSType result = UNKNOWN;
    if (folder != null && folder.isDirectory()) {
      for (final CVSType t : values()) {
        if (t.getProcessor().doesContainCVS(folder)) {
          result = t;
          break;
        }
      }
    }
    return result;
  }

  @Nonnull
  public AbstractRepo getProcessor() {
    return this.processor;
  }
}
