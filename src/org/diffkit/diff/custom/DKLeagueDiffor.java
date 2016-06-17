/**
 * Copyright 2010-2011 Joseph Panico
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.diffkit.diff.custom;

import org.apache.commons.lang.ClassUtils;
import org.diffkit.diff.diffor.DKTextDiffor;
import org.diffkit.diff.engine.DKContext;
import org.diffkit.diff.engine.DKDiffor;

import java.util.Objects;

/**
 * @author hank_cp
 */
public class DKLeagueDiffor implements DKDiffor {

   private static final DKLeagueDiffor INSTANCE = new DKLeagueDiffor();

   public static DKLeagueDiffor getInstance() {
      return INSTANCE;
   }

   public boolean isDiff(Object lhs_, Object rhs_, DKContext context_) {
      String lhs = lhs_.toString();
      String rhs = rhs_.toString();

      if ("-".equals(lhs) && (rhs == null || rhs.length() == 0)) {
         return false;
      } else {
         return !Objects.equals(lhs, rhs);
      }
   }

   public String toString() {
      return ClassUtils.getShortClassName(this.getClass());
   }
}
