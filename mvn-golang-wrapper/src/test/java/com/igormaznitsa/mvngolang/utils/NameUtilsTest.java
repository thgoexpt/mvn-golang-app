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
import static org.junit.Assert.*;

public class NameUtilsTest {
  
  @Test
  public void makePackageNameFromDependency_NoVersion() {
    assertEquals("github.com/gizak/termui",NameUtils.makePackageNameFromDependency("github.com","gizak.termui", ""));
  }
  
  @Test
  public void makePackageNameFromDependency_Version() {
    assertEquals("github.com/gizak/termui/1.0",NameUtils.makePackageNameFromDependency("github.com","gizak.termui", "1.0"));
  }
  
}
