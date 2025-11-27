package org.sonar.server.platform.db.migration.version.v108;

import java.sql.SQLException;
import org.sonar.db.Database;
import org.sonar.server.platform.db.migration.def.BigIntegerColumnDef;
import org.sonar.server.platform.db.migration.sql.AddColumnsBuilder;
import org.sonar.server.platform.db.migration.step.DdlChange;
import static org.sonar.db.DatabaseUtils.tableColumnExists;


public class AddHotspotExpiryDateColumnToIssuesTable extends  DdlChange{
    public static final String TABLE_NAME = "issues";
    public static final String HOTSPOT_EXCEPTION_EXPIRY_COLUMN = "hotspot_exception_expires_at";

    public AddHotspotExpiryDateColumnToIssuesTable(Database db) {
        super(db);
    }

    @Override
    public void execute(Context context) throws SQLException {
        try (var connection = getDatabase().getDataSource().getConnection()) {
            if (!tableColumnExists(connection, TABLE_NAME, HOTSPOT_EXCEPTION_EXPIRY_COLUMN)) {
                var hotspotExceptionExpiry = BigIntegerColumnDef.newBigIntegerColumnDefBuilder()
                        .setColumnName(HOTSPOT_EXCEPTION_EXPIRY_COLUMN)
                        .setIsNullable(true)
                        .build();
                context.execute(
                        new AddColumnsBuilder(getDialect(), TABLE_NAME).addColumn(hotspotExceptionExpiry).build());
            }
        }
    }
}
