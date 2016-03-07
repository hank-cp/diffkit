package org.diffkit.diff.custom;

import org.diffkit.common.DKValidate;
import org.diffkit.db.DKDatabase;
import org.diffkit.diff.engine.DKExcludeConfig;
import org.diffkit.util.DKSqlUtil;

import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author hank_cp
 */
public class DKDBExcludeConfig implements DKExcludeConfig {

    private final DKDatabase _database;
    private final String _excludeConfigTableName;
    private final String _excludeConfigColumnName;
    private Set<String> _excludeKeys;

    public DKDBExcludeConfig(DKDatabase database_,
                             String excludeConfigTableName_,
                             String excludeConfigColumn_) {
        _database = database_;
        _excludeConfigTableName = excludeConfigTableName_;
        _excludeConfigColumnName = excludeConfigColumn_;

        DKValidate.notNull(_database, _excludeConfigColumnName);
    }

    @Override
    public Set<String> getExcludeKeyList() {
        if (_excludeKeys != null) return _excludeKeys;
        _excludeKeys = new HashSet<>();

        try {
            ResultSet rs = DKSqlUtil.executeQuery(String.format("SELECT %s FROM %s", _excludeConfigColumnName, _excludeConfigTableName),
                    _database.getConnection());
            List<Map<String, ?>> result = DKSqlUtil.readRows(rs);
            for (Map<String, ?> row : result) {
                _excludeKeys.add(row.get(_excludeConfigColumnName).toString());
            }
            return _excludeKeys;
        } catch (Exception e) {
            throw new RuntimeException("Fetch excludeConfig failed.", e);
        }
    }
}
