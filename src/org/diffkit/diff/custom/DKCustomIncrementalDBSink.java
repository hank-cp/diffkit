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

    private final Logger _log = LoggerFactory.getLogger(this.getClass());

    private final DKSqlGenerator _sqlGenerator;

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
        _sqlGenerator = new DKSqlGenerator(database_);
    }

    @Override
    public void onRowConsistent(DKSource lhs, Object[] lhsData, DKSource rhs, Object[] rhsData) {
        try {
            deleteRow(lhsData, rhsData);
        } catch (SQLException e) {
            _log.error("Delete diff table record failed.", e);
        }
        super.onRowConsistent(lhs, lhsData, rhs, rhsData);
    }

    public void record(DKDiff diff_, Object[] lhsData, Object[] rhsData, DKContext context_) throws IOException {
        try {
            deleteRow(lhsData, rhsData);
        } catch (SQLException e) {
            _log.error("Delete diff table record failed.", e);
        }
        super.record(diff_, lhsData, rhsData, context_);
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
