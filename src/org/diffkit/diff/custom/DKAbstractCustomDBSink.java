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
import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.StringUtils;
import org.diffkit.common.DKValidate;
import org.diffkit.db.DKDBColumn;
import org.diffkit.db.DKDBTable;
import org.diffkit.db.DKDatabase;
import org.diffkit.db.DKSqlGenerator;
import org.diffkit.diff.engine.*;
import org.diffkit.diff.sns.DKAbstractSink;
import org.diffkit.diff.sns.DKDBSource;
import org.diffkit.util.DKSqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author hank_cp
 * modify by zhen 20160629
 */
public class DKAbstractCustomDBSink extends DKAbstractSink {

    protected final DKDatabase _database;
    protected final DKDBTable _diffTable;
    protected final String[][] _displayColumnNames;
    private final String _diffTableName;
    private final String _diffResultTableDDLExtra;

    private final DKDBSource _writeBackDataSource;
    private final int _writeBackKeyIndex;
    private final DKDBSource _lhsSource;
    private final DKDBSource _rhsSource;
    private final String _rowConsistenceWriteBackStatement;
    private final String _rowDiffWriteBackStatement;
    private final String _columnDiffWriteBackStatement;

    private final DKSqlGenerator _sqlGenerator;

    protected int _failedUpdateCount;
    private Long _previousRowStep;

    protected String colDiffPosition = "";//record the diff column position

    private final Logger _log = LoggerFactory.getLogger(this.getClass());

    public DKAbstractCustomDBSink(DKDatabase database_,
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
        super(null);
        _database = database_;
        _displayColumnNames = comparison_.getDisplayColumnNames();
        _diffTableName = diffTableName_;
        _diffResultTableDDLExtra = diffResultTableDDLExtra_;

        _writeBackDataSource = writeBackDataSource_;
        _writeBackKeyIndex = writeBackKeyIndex_;
        _lhsSource = lhsSource_;
        _rhsSource = rhsSource_;
        _rowConsistenceWriteBackStatement = rowConsistenceWriteBackStatement_;
        _rowDiffWriteBackStatement = rowDiffWriteBackStatement_;
        _columnDiffWriteBackStatement = columnDiffWriteBackStatement_;

        _diffTable = this.generateDiffTable();
        _sqlGenerator = new DKSqlGenerator(database_);
        DKValidate.notNull(_database, _diffTable);
    }

    @Override
    public Kind getKind() {
        return Kind.DB;
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
        return String.format("%s@%x", ClassUtils.getShortClassName(this.getClass()),
                System.identityHashCode(this));
    }

    @Override
    public void close(DKContext context_) throws IOException {
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
            deleteRow(lhsData,rhsData);//ensure just have one record
            _database.insertRow(row, _diffTable);

            if (_writeBackDataSource != null && StringUtils.isNotEmpty(_rowConsistenceWriteBackStatement)) {
                _writeBackDataSource.getDatabase().executeUpdate(String.format(_rowConsistenceWriteBackStatement, lhsData[_writeBackKeyIndex]));
            }
        } catch (SQLException e) {
            _failedUpdateCount += 1;
            _log.error("Update lhs table failed.", e);
        }
    }

    public void record(DKDiff diff_, Object[] lhsData, Object[] rhsData, DKContext context_) throws IOException {
        super.record(diff_, lhsData, rhsData, context_);
        try {
            Map<String, ?> row = this.createRow(diff_, lhsData, rhsData, context_);
            if (row != null) {
                deleteRow(lhsData,rhsData);//ensure just have one record
                _database.insertRow(row, _diffTable);
            }

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

    protected Map<String, ?> createRow(DKDiff diff_, Object[] lhsData, Object[] rhsData, DKContext context_) {
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
                        colDiffPosition = "";//change row,set to empty
                    } else {
                        // still in same row, skip to record
                        //FIXME continue to compare, to find all diff column
                        //return null;
                    }
                    colDiffPosition += diff_.getColumnStep() + ",";
                    //at this position ,record first time col diff
                    row.put("DIFF_COLUMN_POSITION", colDiffPosition);
                    row.put("DIFF", "3");
                    break;
            }
        } else row.put("DIFF", "0");
        //createDate
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = sdf.format(new Date());
        row.put("RECORD_DATE", timestamp);

        for (int sideIdx=0; sideIdx<2; sideIdx++) {
            for (int columnIdx=0; columnIdx<_displayColumnNames[sideIdx].length; columnIdx++) {
                String columnName = _displayColumnNames[sideIdx][columnIdx];
                Object[] data = sideIdx == 0 ? lhsData : rhsData;
                row.put((sideIdx == 0 ? "lhs_" : "rhs_")+columnName, data != null ? data[columnIdx] : null);
            }
        }

        return row;
    }

    protected void cleanDiffTable() throws SQLException {
        if (!_database.executeUpdate("DROP TABLE IF EXISTS " + _diffTable.getTableName())) {
            throw new RuntimeException(String.format("couldn't drop _diffTable->%s",
                    _diffTable));
        }
    }

    protected void ensureDiffTable() throws SQLException {
        if (!DKSqlUtil.judgeTableIfExist(_diffTable, _database.getConnection())) {
            if (_database.createTable(_diffTable) == null)
                throw new RuntimeException(String.format("couldn't create _diffTable->%s",
                        _diffTable));
        }
    }

    protected DKDBTable generateDiffTable() throws SQLException {
        List<DKDBColumn> columns = new ArrayList<>();
        for (int sideIdx=0; sideIdx<2; sideIdx++) {
            for (int columnIdx=0; columnIdx<_displayColumnNames[sideIdx].length; columnIdx++) {
                String columnName = _displayColumnNames[sideIdx][columnIdx];
                DKDBSource source = sideIdx == 0 ? _lhsSource : _rhsSource;
                DKDBColumn sourceColumn = source.getTable().getColumn(columnName);
                DKColumnModel sourceColumnModal = source.getModel().getColumn(columnName);
                DKDBColumn column = new DKDBColumn((sideIdx == 0 ? "lhs_" : "rhs_")+columnName,
                        columnIdx+(_displayColumnNames[sideIdx].length*sideIdx)+1,
                        (sourceColumnModal._type == DKColumnModel.Type.DATE
                                || sourceColumnModal._type == DKColumnModel.Type.TIME
                                || sourceColumnModal._type == DKColumnModel.Type.TIMESTAMP
                                ? "DATETIME" : "VARCHAR"),
                        sourceColumn.getSize(), true);
                columns.add(column);
            }
        }

        columns.add(new DKDBColumn("DIFF", columns.size(), "VARCHAR", 128, true));
        columns.add(new DKDBColumn("DIFF_COLUMN_POSITION", columns.size(), "VARCHAR", 16, true));
        //record createDate
        columns.add(new DKDBColumn("RECORD_DATE", columns.size(), "DATETIME", 128, true));

        DKDBColumn[] columnArray = columns.toArray(new DKDBColumn[columns.size()]);
        return new DKDBTable(null, null, _diffTableName, columnArray, null,
                _diffResultTableDDLExtra);
    }

    /**
     * add by zhen 20160629
     * before insert diff result,delete the record by lhs/rhs specify key
     * to ensure the diff result table just have one proof record
     * **/
    private boolean deleteRow(Object[] lhsData, Object[] rhsData) throws SQLException {
        String deleteSql = generateDeleteSQL(lhsData, rhsData);
        Connection connection = _database.getConnection();
        boolean delete = DKSqlUtil.executeUpdate(deleteSql, connection);
        // DKSqlUtil.close(connection);
        return delete;
    }

    /**
     * add by zhen 20160629
     * delete the record by lhs/rhs specify key
     * **/
    private String generateDeleteSQL(Object[] lhsData, Object[] rhsData)
            throws SQLException {
        if (ArrayUtils.isEmpty(lhsData) && ArrayUtils.isEmpty(rhsData)) return null;
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("DELETE FROM %s\n",
                _sqlGenerator.generateQualifiedTableIdentifierString(_diffTable.getSchema(), _diffTable.getTableName())));

        String lhsSpecifyKey = "lhs_" + _context.getLhs().getModel().getKeyColumn().getName();
        String rhsSpecifyKey = "rhs_" + _context.getRhs().getModel().getKeyColumn().getName();

        String lhsKeyValue = null;
        String rhsKeyValue = null;

        for (int sideIdx=0; sideIdx<2; sideIdx++) {
            for (int columnIdx=0; columnIdx<_displayColumnNames[sideIdx].length; columnIdx++) {
                String columnName = _displayColumnNames[sideIdx][columnIdx];
                if (!Objects.equals(columnName, sideIdx == 0
                        ? _context.getLhs().getModel().getKeyColumn().getName()
                        : _context.getRhs().getModel().getKeyColumn().getName())) continue;
                Object[] data = sideIdx == 0 ? lhsData : rhsData;
                if (sideIdx == 0) {
                    lhsKeyValue = data != null ? data[columnIdx].toString() : null;
                } else {
                    rhsKeyValue = data != null ? data[columnIdx].toString() : null;
                }
            }
        }

        builder.append(" WHERE ").append(lhsSpecifyKey).append(" = '").append(Optional.ofNullable(lhsKeyValue).orElse("")).append("'")
                .append(" OR ").append(rhsSpecifyKey).append(" = '").append(Optional.ofNullable(rhsKeyValue).orElse("")).append("'");
        return builder.toString();
    }
}
