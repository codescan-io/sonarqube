package org.sonar.server.platform.db.migration.version.v99;

import com.google.common.annotations.VisibleForTesting;
import java.sql.Connection;
import java.sql.SQLException;
import org.sonar.db.Database;
import org.sonar.db.DatabaseUtils;
import org.sonar.server.platform.db.migration.def.BooleanColumnDef;
import org.sonar.server.platform.db.migration.def.VarcharColumnDef;
import org.sonar.server.platform.db.migration.sql.AddColumnsBuilder;
import org.sonar.server.platform.db.migration.step.DdlChange;

public class AddOnboardedColumnToUsersTable extends DdlChange {

    @VisibleForTesting
    static final String TABLE_NAME = "users";
    @VisibleForTesting
    static final String COLUMN_NAME = "onboarded";

    public AddOnboardedColumnToUsersTable(Database db) {
        super(db);
    }

    @Override
    public void execute(Context context) throws SQLException {
        try (Connection c = getDatabase().getDataSource().getConnection()) {
            if (!DatabaseUtils.tableColumnExists(c, TABLE_NAME, COLUMN_NAME)) {
                context.execute(new AddColumnsBuilder(getDialect(), TABLE_NAME)
                        .addColumn(BooleanColumnDef.newBooleanColumnDefBuilder().setColumnName(COLUMN_NAME)
                                .setIsNullable(false).setDefaultValue(false).build())
                        .build());
            }
        }
    }
}
