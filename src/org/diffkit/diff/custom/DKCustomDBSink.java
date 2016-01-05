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
import org.diffkit.db.DKDBColumn;
import org.diffkit.db.DKDBTable;
import org.diffkit.db.DKDBTableDataAccess;
import org.diffkit.db.DKDatabase;
import org.diffkit.diff.engine.*;
import org.diffkit.diff.sns.DKAbstractSink;
import org.diffkit.diff.sns.DKDBSource;
import org.diffkit.util.DKSqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.Connection;
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
    private final DKDBTableDataAccess _tableDataAccess;
    private final DKDBTable _diffTable;
    private transient Connection _connection;
    private final String[][] _displayColumnNames;
    private final Logger _log = LoggerFactory.getLogger(this.getClass());

    private int _consistentCount;
    private int _failedConsistentCount;

    public DKCustomDBSink(String summaryFilePath_, DKDatabase database_,
                          DKCustomTableComparison comparison_) throws SQLException {
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
        _tableDataAccess = new DKDBTableDataAccess(_database);
        _diffTable = this.generateDiffTable();
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
            _connection = _database.getConnection();
            ensureDiffTable();
        } catch (SQLException e_) {
            _log.error(null, e_);
            throw new RuntimeException(e_);
        }
        _log.info("_connection->{}", _connection);
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

        DKSqlUtil.close(_connection);
        _connection = null;
        super.close(context_);
    }

    @Override
    public void onRowConsistent(DKSource lhs, Object[] lhsData, DKSource rhs, Object[] rhsData) {
        DKDBSource dbSource = (DKDBSource) lhs;
        try {
            _consistentCount += 1;
            dbSource.getDatabase().executeUpdate("UPDATE `proc_stock_in_task` SET `STATUS`=3 WHERE `TASK_NUMBER`=" + lhsData[0]);
        } catch (SQLException e) {
            _failedConsistentCount += 1;
            _log.error("Update lhs table failed.", e);
        }
    }

    public void record(DKDiff diff_, Object[] lhsData, Object[] rhsData, DKContext context_) throws IOException {
        super.record(diff_, lhsData, rhsData, context_);
        try {
            diff_.getRowDisplayValues();

            Map<String, ?> row = this.createRow(diff_, lhsData, rhsData, context_);
            _database.insertRow(row, _diffTable);
        } catch (SQLException e_) {
            throw new RuntimeException(e_);
        }
    }

    private Map<String, ?> createRow(DKDiff diff_, Object[] lhsData, Object[] rhsData, DKContext context_) {
        Map<String, Object> row = new HashMap<>();
        for (int sideIdx=0; sideIdx<2; sideIdx++) {
            for (int columnIdx=0; columnIdx<_displayColumnNames[sideIdx].length; columnIdx++) {
                String columnName = _displayColumnNames[sideIdx][columnIdx];
                Object[] data = sideIdx == 0 ? lhsData : rhsData;
                row.put((sideIdx == 0 ? "lhs_" : "rhs_")+columnName, data != null ? data[columnIdx] : null);
            }
        }
        switch (diff_.getKind()) {
            case ROW_DIFF:
                row.put("DIFF", ((DKRowDiff)diff_).getSide() == DKSide.LEFT ? "L" : "R");
                break;
            case COLUMN_DIFF:
                row.put("DIFF", "U");
                break;
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
        return new DKDBTable(null, null, "diff_result", columnArray);
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
        builder.append(String.format("\"diff_rows\":%d,", getRowDiffCount()));
        builder.append(String.format("\"column_rows\":%d,", getColumnDiffCount()));
        builder.append(String.format("\"consistentCount\":%d,", _consistentCount));
        builder.append(String.format("\"failedUpdateConsistentCount\":%d", _failedConsistentCount));
        builder.append("}");
        return builder.toString();
    }
}
