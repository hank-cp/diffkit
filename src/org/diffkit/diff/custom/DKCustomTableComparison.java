/**
 * Copyright 2010-2011 Joseph Panico
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.diffkit.diff.custom;

import org.apache.commons.collections.MapIterator;
import org.apache.commons.collections.OrderedMap;
import org.apache.commons.collections.map.LinkedMap;
import org.diffkit.diff.engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The instructions for how to carry out a complete comparison of one table
 * (lhs) to another table (rhs)
 *
 * @author jpanico
 */
public class DKCustomTableComparison extends DKStandardTableComparison {

    private final Logger _log = LoggerFactory.getLogger(this.getClass());

    public DKCustomTableComparison(DKTableModel lhs_, DKTableModel rhs_,
                                   DKDiff.Kind kind_, DKColumnComparison[] map_,
                                   int[] diffIndexes_, int[][] displayIndexes_,
                                   long maxDiffs_) {
        super(lhs_, rhs_, kind_, map_, diffIndexes_, displayIndexes_, maxDiffs_);
    }

    /**
     * @param lhs_
     *           row
     * @param rhs_
     *           row
     * @return OrderedMap ordered, as best as possible, according to
     *         _displayIndexes. keys are String; values are String
     */
    @SuppressWarnings("unchecked")
    public OrderedMap getRowDisplayValues(Object[] lhs_, Object[] rhs_) {

        OrderedMap lhDisplayValues = this.getRowDisplayValues(lhs_, DKSide.LEFT_INDEX);
        OrderedMap rhDisplayValues = this.getRowDisplayValues(rhs_, DKSide.RIGHT_INDEX);
        if (_log.isDebugEnabled()) {
            _log.debug("lhDisplayValues->{}", lhDisplayValues);
            _log.debug("rhDisplayValues->{}", rhDisplayValues);
        }
        MapIterator lhIterator = lhDisplayValues.orderedMapIterator();
        MapIterator rhIterator = rhDisplayValues.orderedMapIterator();
        OrderedMap result = new LinkedMap();
        while (true) {
            boolean lhHasNext = lhIterator.hasNext();
            boolean rhHasNext = rhIterator.hasNext();
            if ((!lhHasNext) && (!rhHasNext)) break;
            String lhKey = (lhHasNext ? (String) lhIterator.next() : null);
            String rhKey = (rhHasNext ? (String) rhIterator.next() : null);

            result.put(lhKey, lhDisplayValues.get(lhKey));
            result.put(rhKey, rhDisplayValues.get(rhKey));
        }
        return result;
    }

    @Override
    public OrderedMap getRowDisplayValues(Object[] row_, int sideIdx_) {
        if (row_ == null)
            return null;
        int[] displayIndexes = _displayIndexes[sideIdx_];
        DKColumnModel[] columns = _tableModels[sideIdx_].getColumns();
        OrderedMap result = new LinkedMap(_displayIndexes.length);
        for (int i = 0; i < displayIndexes.length; i++) {
            Object value = row_[displayIndexes[i]];
            String displayValue = (value == null ? "<null>" : value.toString());
            result.put((sideIdx_ == 0 ? "lhs_" : "rhs_")
                    + columns[displayIndexes[i]]._name, displayValue);
        }
        return result;
    }

}
