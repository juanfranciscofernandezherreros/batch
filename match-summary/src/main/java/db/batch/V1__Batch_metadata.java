package db.batch;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

public class V1__Batch_metadata extends BaseJavaMigration {
    @Override public void migrate(Context context) {
        ScriptUtils.executeSqlScript(context.getConnection(),
                new ClassPathResource("org/springframework/batch/core/schema-postgresql.sql"));
    }
}
