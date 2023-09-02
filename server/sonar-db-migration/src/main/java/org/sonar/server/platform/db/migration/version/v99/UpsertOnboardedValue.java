package org.sonar.server.platform.db.migration.version.v99;

import java.sql.SQLException;
import org.sonar.db.Database;
import org.sonar.server.platform.db.migration.step.DataChange;
import org.sonar.server.platform.db.migration.step.Upsert;

public class UpsertOnboardedValue extends DataChange {

    public UpsertOnboardedValue(Database db) {
        super(db);
    }

    @Override
    protected void execute(Context context) throws SQLException {
        Upsert upsert = context.prepareUpsert(
                "UPDATE users SET onboarded = ? where onboarded = false");
        upsert
                .setBoolean(1, true);
        upsert.execute();
        upsert.commit();
    }
}
