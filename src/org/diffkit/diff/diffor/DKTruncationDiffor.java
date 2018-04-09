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
package org.diffkit.diff.diffor;

import org.apache.commons.lang.ClassUtils;
import org.diffkit.diff.engine.DKContext;
import org.diffkit.diff.engine.DKDiffor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hank CP
 */
public class DKTruncationDiffor implements DKDiffor {

   private final Logger _log = LoggerFactory.getLogger(this.getClass());
   private final boolean _isDebugEnabled = _log.isDebugEnabled();

   private final int length;

   private DKTruncationDiffor(int length) {
      this.length = length;
   }

   /**
    * @see DKDiffor#isDiff(Object,
    *      Object, DKContext)
    */
   public boolean isDiff(Object lhs_, Object rhs_, DKContext context_) {
      if (_isDebugEnabled) {
         _log.debug("lhs_->{} lhs_.class->{}", lhs_,
            (lhs_ == null ? null : lhs_.getClass()));
         _log.debug("rhs_->{} rhs_.class->{}", rhs_,
            (rhs_ == null ? null : rhs_.getClass()));
      }
      boolean lhsNull = (lhs_ == null);
      boolean rhsNull = (rhs_ == null);
      if (lhsNull && rhsNull)
         return false;
      if (lhsNull || rhsNull)
         return true;
      String lhsStr = lhs_.toString();
      String rhsStr = rhs_.toString();
      if (lhsStr.equals(rhsStr)) return true;

      if (lhsStr.length() > rhsStr.length()) {
         return !lhsStr.startsWith(rhsStr);
      } else if (lhsStr.length() < rhsStr.length()) {
         return !rhsStr.startsWith(lhsStr);
      }
      return false;
   }

   public String toString() {
      return String.format("%s", ClassUtils.getShortClassName(this.getClass()));
   }
}
