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
import org.apache.commons.lang.StringUtils;
import org.diffkit.common.DKRuntime;
import org.diffkit.common.DKValidate;
import org.diffkit.db.DKDBColumn;
import org.diffkit.db.DKDBTable;
import org.diffkit.db.DKDatabase;
import org.diffkit.diff.engine.*;
import org.diffkit.diff.sns.DKAbstractSink;
import org.diffkit.diff.sns.DKDBSource;
import org.diffkit.util.DKSqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author hank_cp
 */
public class DKCustomDBSink extends DKAbstractSink {

    private final File _summaryFile;
    private final Writer _summaryWriter;
    private final DKDatabase _database;
    private final DKDBTable _diffTable;
    private final String[][] _displayColumnNames;
    private final String _diffTableName;
    private final String _diffResultTableDDLExtra;

    private final DKDBSource _writeBackDataSource;
    private final int _writeBackKeyIndex;
    private final String _rowConsistenceWriteBackStatement;
    private final String _rowDiffWriteBackStatement;
    private final String _columnDiffWriteBackStatement;

    private final DKDBExcludeConfig _excludeConfig;

    private int _consistentCount;
    private int _failedUpdateCount;
    private Long _previousRowStep;

    private final Logger _log = LoggerFactory.getLogger(this.getClass());

    public DKCustomDBSink(String summaryFilePath_, DKDatabase database_,
                          DKCustomTableComparison comparison_, String diffTableName_,
                          String diffResultTableDDLExtra_,
                          DKDBSource writeBackDataSource_,
                          int writeBackKeyIndex_,
                          String rowConsistenceWriteBackStatement_,
                          String rowDiffWriteBackStatement_,
                          String columnDiffWriteBackStatement_,
                          DKDBExcludeConfig excludeConfig_) throws SQLException {
        super(null);
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

        _database = database_;
        _displayColumnNames = comparison_.getDisplayColumnNames();
        _diffTableName = diffTableName_;
        _diffResultTableDDLExtra = diffResultTableDDLExtra_;
        _diffTable = this.generateDiffTable();

        _writeBackDataSource = writeBackDataSource_;
        _writeBackKeyIndex = writeBackKeyIndex_;
        _rowConsistenceWriteBackStatement = rowConsistenceWriteBackStatement_;
        _rowDiffWriteBackStatement = rowDiffWriteBackStatement_;
        _columnDiffWriteBackStatement = columnDiffWriteBackStatement_;

        _excludeConfig = excludeConfig_;

        DKValidate.notNull(_database, _diffTable);
    }

    @Override
    public Kind getKind() {
        return Kind.FILE;
    }

    public File getFile() {
        return _summaryFile;
    }

    @Override
    public void open(DKContext context_) throws IOException {
        super.open(context_);
        try {
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
        try {
            Map<String, ?> row = this.createRow(null, lhsData, rhsData, _context);
            _database.insertRow(row, _diffTable);

            _consistentCount += 1;
            if (_writeBackDataSource != null && StringUtils.isNotEmpty(_rowConsistenceWriteBackStatement)) {
                _writeBackDataSource.getDatabase().executeUpdate(String.format(_rowConsistenceWriteBackStatement, lhsData[_writeBackKeyIndex]));
            }
        } catch (SQLException e) {
            _failedUpdateCount += 1;
            _log.error("Update lhs table failed.", e);
        }
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
            if (hit) onRowConsistent(context_._lhs, lhsData, context_._rhs, rhsData);
            return;
        }

        super.record(diff_, lhsData, rhsData, context_);
        try {
            Map<String, ?> row = this.createRow(diff_, lhsData, rhsData, context_);
            if (row != null) _database.insertRow(row, _diffTable);

            try {
                if (diff_.getKind() == DKDiff.Kind.ROW_DIFF
                        && _writeBackDataSource != null
                        && StringUtils.isNotEmpty(_rowDiffWriteBackStatement)) {
                    _writeBackDataSource.getDatabase().executeUpdate(String.format(_rowDiffWriteBackStatement,
                            ((DKRowDiff)diff_).getSide() == DKSide.LEFT ? lhsData[_writeBackKeyIndex] : rhsData[_writeBackKeyIndex]));

                } else if (diff_.getKind() == DKDiff.Kind.COLUMN_DIFF
                        && _writeBackDataSource != null
                        && StringUtils.isNotEmpty(_columnDiffWriteBackStatement)) {
                    _writeBackDataSource.getDatabase().executeUpdate(String.format(_columnDiffWriteBackStatement, lhsData[_writeBackKeyIndex]));
                }
            } catch (SQLException e) {
                _failedUpdateCount += 1;
                _log.error("Update lhs table failed.", e);
            }

        } catch (SQLException e_) {
            throw new RuntimeException(e_);
        }
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

    private Map<String, ?> createRow(DKDiff diff_, Object[] lhsData, Object[] rhsData, DKContext context_) {
        Map<String, Object> row = new HashMap<>();

        if (diff_ != null) {
            switch (diff_.getKind()) {
                case ROW_DIFF:
                    row.put("DIFF", ((DKRowDiff)diff_).getSide() == DKSide.LEFT ? "2" : "1");
                    break;
                case COLUMN_DIFF:
                    if (_previousRowStep == null) {
                        // first column
                        _previousRowStep = context_._rowStep;
                    } else if (_previousRowStep != context_._rowStep) {
                        // walk to next row, reset recorded row step
                        _previousRowStep = context_._rowStep;
                    } else {
                        // still in same row, skip to record
                        return null;
                    }

                    row.put("DIFF", "3");
                    break;
            }
        } else row.put("DIFF", "0");

        for (int sideIdx=0; sideIdx<2; sideIdx++) {
            for (int columnIdx=0; columnIdx<_displayColumnNames[sideIdx].length; columnIdx++) {
                String columnName = _displayColumnNames[sideIdx][columnIdx];
                Object[] data = sideIdx == 0 ? lhsData : rhsData;
                row.put((sideIdx == 0 ? "lhs_" : "rhs_")+columnName, data != null ? data[columnIdx] : null);
            }
        }

        return row;
    }

    private void ensureDiffTable() throws SQLException {
        if (!_database.executeUpdate("DROP TABLE IF EXISTS " + _diffTable.getTableName())) {
            throw new RuntimeException(String.format("couldn't drop _diffTable->%s",
                    _diffTable));
        }

        if (_database.createTable(_diffTable) == null)
            throw new RuntimeException(String.format("couldn't create _diffTable->%s",
                    _diffTable));
    }

    private DKDBTable generateDiffTable() throws SQLException {
        List<DKDBColumn> columns = new ArrayList<>();
        for (int sideIdx=0; sideIdx<2; sideIdx++) {
            for (int columnIdx=0; columnIdx<_displayColumnNames[sideIdx].length; columnIdx++) {
                String columnName = _displayColumnNames[sideIdx][columnIdx];
                DKDBColumn column = new DKDBColumn((sideIdx == 0 ? "lhs_" : "rhs_")+columnName,
                        columnIdx+(_displayColumnNames[sideIdx].length*sideIdx)+1, "VARCHAR", 255, true);
                columns.add(column);
            }
        }

        columns.add(new DKDBColumn("DIFF", columns.size(), "VARCHAR", 128, true));
        DKDBColumn[] columnArray = columns.toArray(new DKDBColumn[columns.size()]);
        return new DKDBTable(null, null, _diffTableName, columnArray, null,
                _diffResultTableDDLExtra);
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
        builder.append(String.format("\"diff_rows\":%d,", getColumnDiffCount()));
        builder.append(String.format("\"consistent_rows\":%d,", _consistentCount));
        builder.append(String.format("\"failed_update_rows\":%d", _failedUpdateCount));
        builder.append("}");
        return builder.toString();
    }
}
