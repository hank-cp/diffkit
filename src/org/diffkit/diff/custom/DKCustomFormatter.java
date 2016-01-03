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

import org.apache.commons.collections.OrderedMap;
import org.diffkit.common.annot.NotThreadSafe;
import org.diffkit.diff.engine.*;
import org.diffkit.diff.sns.DKDiffFormatter;

/**
 * @author jpanico
 */
@NotThreadSafe
public class DKCustomFormatter implements DKDiffFormatter {

   private static final DKCustomFormatter INSTANCE = new DKCustomFormatter();
   private DKColumnDiffRow _runningRow;
   private OrderedMap _rowValueMap;

   public static DKCustomFormatter getInstance() {
      return INSTANCE;
   }

   public String format(DKDiff diff_, DKContext context_) {
      if (diff_ == null)
         return null;

      switch (diff_.getKind()) {
      case ROW_DIFF:
         return this.formatRowDiff((DKRowDiff) diff_, context_);
      case COLUMN_DIFF:
         return this.formatColumnDiff((DKColumnDiff) diff_, context_);

      default:
         throw new IllegalArgumentException(String.format("unrecognized kind->%s",
            diff_.getKind()));
      }
   }

   private String formatRowDiff(DKRowDiff diff_, DKContext context_) {
      StringBuilder builder = new StringBuilder();
      DKSide side = diff_.getSide();
      OrderedMap rowValues = diff_.getRowDisplayValues();
      rowValues.put("diff", diff_.getSide() == DKSide.LEFT ? "L" : "R");
      builder.append(GsonUtil.toJson(rowValues)).append(",");
      return builder.toString();
   }

   private String formatColumnDiff(DKColumnDiff diff_, DKContext context_) {
      StringBuilder builder = new StringBuilder();
      DKColumnDiffRow row = diff_.getRow();
      if (row != _runningRow) {
         _runningRow = row;
         OrderedMap rowValues = row.getRowDisplayValues();
         rowValues.put("diff", "U");
         builder.append(GsonUtil.toJson(rowValues)).append(",");
      }
      return builder.toString();
   }
}
