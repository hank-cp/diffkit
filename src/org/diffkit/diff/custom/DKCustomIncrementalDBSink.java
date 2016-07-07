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

import org.apache.commons.lang.ArrayUtils;
import org.diffkit.db.DKDatabase;
import org.diffkit.db.DKSqlGenerator;
import org.diffkit.diff.engine.DKContext;
import org.diffkit.diff.engine.DKDiff;
import org.diffkit.diff.engine.DKSource;
import org.diffkit.diff.sns.DKDBSource;
import org.diffkit.util.DKSqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author hank_cp
 * modify by zhen 20160629
 */
public class DKCustomIncrementalDBSink extends DKAbstractCustomDBSink {

    public DKCustomIncrementalDBSink(DKDatabase database_,
                                     DKCustomTableComparison comparison_,
                                     String diffTableName_,
                                     String diffResultTableDDLExtra_,
                                     DKDBSource writeBackDataSource_,
                                     int writeBackKeyIndex_,
                                     DKDBSource lhsSource_,
                                     DKDBSource rhsSource_,
                                     String rowConsistenceWriteBackStatement_,
                                     String rowDiffWriteBackStatement_,
                                     String columnDiffWriteBackStatement_) throws SQLException {
        super(database_, comparison_, diffTableName_, diffResultTableDDLExtra_,
                writeBackDataSource_, writeBackKeyIndex_, lhsSource_, rhsSource_,
                rowConsistenceWriteBackStatement_, rowDiffWriteBackStatement_, columnDiffWriteBackStatement_);
    }

    @Override
    public void onRowConsistent(DKSource lhs, Object[] lhsData, DKSource rhs, Object[] rhsData) {
        super.onRowConsistent(lhs, lhsData, rhs, rhsData);
    }

    public void record(DKDiff diff_, Object[] lhsData, Object[] rhsData, DKContext context_) throws IOException {
        super.record(diff_, lhsData, rhsData, context_);
    }



}
