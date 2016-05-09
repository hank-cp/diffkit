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
import org.diffkit.diff.engine.DKContext;
import org.diffkit.diff.engine.DKDiffor;

/**
 * @author jpanico
 */
public class DKStatusDiffor implements DKDiffor {

   private static final DKStatusDiffor INSTANCE = new DKStatusDiffor();

   public static DKStatusDiffor getInstance() {
      return INSTANCE;
   }

   /**
    */
   public boolean isDiff(Object lhs_, Object rhs_, DKContext context_) {
      Integer lhs = (Integer) lhs_;
      String rhs = (String) rhs_;

      if (lhs != null && lhs == 0) {
         return !"Y".equals(rhs) && !"y".equals(rhs);
      } else if (lhs != null && lhs == 1) {
         return !"N".equals(rhs);
      }

      return false;
   }

   public String toString() {
      return ClassUtils.getShortClassName(this.getClass());
   }
}
