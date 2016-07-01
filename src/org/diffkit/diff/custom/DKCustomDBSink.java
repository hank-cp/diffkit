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

import org.apache.commons.lang.ClassUtils;
import org.diffkit.common.DKRuntime;
import org.diffkit.common.DKValidate;
import org.diffkit.db.DKDatabase;
import org.diffkit.diff.engine.*;
import org.diffkit.diff.sns.DKDBSource;
import org.diffkit.util.DKSqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

/**
 * @author hank_cp
 */
public class DKCustomDBSink extends DKAbstractCustomDBSink {

    private final File _summaryFile;
    private final Writer _summaryWriter;

    private final DKDBExcludeConfig _excludeConfig;

    private int _consistentCount;
    private int _columnDiffRowCount;

    private final Logger _log = LoggerFactory.getLogger(this.getClass());

    public DKCustomDBSink(String summaryFilePath_, DKDatabase database_,
                          DKCustomTableComparison comparison_, String diffTableName_,
                          String diffResultTableDDLExtra_,
                          DKDBSource writeBackDataSource_,
                          int writeBackKeyIndex_,
                          DKDBSource lhsSource_,
                          DKDBSource rhsSource_,
                          String rowConsistenceWriteBackStatement_,
                          String rowDiffWriteBackStatement_,
                          String columnDiffWriteBackStatement_,
                          DKDBExcludeConfig excludeConfig_) throws SQLException {
        super(database_, comparison_, diffTableName_, diffResultTableDDLExtra_, writeBackDataSource_,
              writeBackKeyIndex_, lhsSource_, rhsSource_, rowConsistenceWriteBackStatement_,
                rowDiffWriteBackStatement_, columnDiffWriteBackStatement_);

        File previousFile = new File(summaryFilePath_);
        if (previousFile.exists()) {
            previousFile.renameTo(new File(summaryFilePath_ + "." + System.currentTimeMillis()));
        }

        _summaryFile = new File(summaryFilePath_);
        try {
            _summaryWriter = new BufferedWriter(new FileWriter(_summaryFile));
        } catch (IOException e) {
            throw new RuntimeException("Open diff file to write failed", e);
        }
        _excludeConfig = excludeConfig_;
        DKValidate.notNull(_database, _diffTable);
    }

    public File getFile() {
        return _summaryFile;
    }

    @Override
    public void open(DKContext context_) throws IOException {
        super.open(context_);
        try {
            cleanDiffTable();
            ensureDiffTable();
        } catch (SQLException e_) {
            _log.error(null, e_);
            throw new RuntimeException(e_);
        }
        _log.info("_sinkDatabase->{}", _database.toString());
    }

    public String toString() {
        if (DKRuntime.getInstance().getIsTest())
            return _summaryFile.getName();
        return String.format("%s@%x[%s]", ClassUtils.getShortClassName(this.getClass()),
                System.identityHashCode(this), _summaryFile.getPath());
    }

    @Override
    public void close(DKContext context_) throws IOException {
        _summaryWriter.write(getSummary(_context));
        _summaryWriter.close();

        try {
            DKSqlUtil.close(_database.getConnection());
        } catch (SQLException e_) {
            _log.error(null, e_);
            throw new RuntimeException(e_);
        }

        super.close(context_);
    }

    @Override
    public void onRowConsistent(DKSource lhs, Object[] lhsData, DKSource rhs, Object[] rhsData) {
        _consistentCount += 1;
        super.onRowConsistent(lhs, lhsData, rhs, rhsData);
    }

    public void record(DKDiff diff_, Object[] lhsData, Object[] rhsData, DKContext context_) throws IOException {
        if (_excludeConfig != null) {
            // filter by exclude config
            boolean hit = false;
            for (String key : _excludeConfig.getExcludeKeyList()) {
                if (getDiffKeyValue(diff_, lhsData, rhsData, context_).startsWith(key)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                onRowConsistent(context_._lhs, lhsData, context_._rhs, rhsData);
                return;
            }
        }
        if (diff_.getKind() == DKDiff.Kind.COLUMN_DIFF) _columnDiffRowCount += 1;
        super.record(diff_, lhsData, rhsData, context_);
    }

    public String getDiffKeyValue(DKDiff diff_, Object[] lhsData, Object[] rhsData, DKContext context_) {
        if (diff_.getKind() == DKDiff.Kind.COLUMN_DIFF) {
            return context_._lhs.getModel().getKeyValues(lhsData)[0].toString();

        } else if (diff_.getKind() == DKDiff.Kind.ROW_DIFF
                &&  ((DKRowDiff)diff_).getSide() == DKSide.LEFT) {
            return context_._lhs.getModel().getKeyValues(lhsData)[0].toString();

        } else if (diff_.getKind() == DKDiff.Kind.ROW_DIFF
                &&  ((DKRowDiff)diff_).getSide() == DKSide.RIGHT) {
            return context_._rhs.getModel().getKeyValues(rhsData)[0].toString();
        }

        throw new RuntimeException("Resolve key value failed.");
    }

    public String getSummary(DKContext context_) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        String startTimeString = DKRuntime.getInstance().getIsTest() ? "xxx"
                : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(context_.getDiffStartTime());
        builder.append(String.format("\"diff_start\":\"%s\",", startTimeString));
        String elapsedTimeString = DKRuntime.getInstance().getIsTest() ? "xxx"
                : context_.getElapsedTimeString();
        builder.append(String.format("\"diff_elapsed_time\":\"%s\",", elapsedTimeString));
        builder.append(String.format("\"rows\":%d,", context_._rowStep - 1));
        builder.append(String.format("\"left_only\":%d,", getLeftOnlyRowCount()));
        builder.append(String.format("\"right_only\":%d,", getRightOnlyRowCount()));
        builder.append(String.format("\"diff_rows\":%d,", _columnDiffRowCount));
        builder.append(String.format("\"consistent_rows\":%d,", _consistentCount));
        builder.append(String.format("\"failed_update_rows\":%d", _failedUpdateCount));
        builder.append("}");
        return builder.toString();
    }
}
