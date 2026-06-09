package com.pfc;

import com.pfc.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class PfcApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads_withRealPostgresAndMigratedSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, "public", null, new String[]{"TABLE"})) {

            var tableNames = new java.util.ArrayList<String>();
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME").toLowerCase());
            }

            assertThat(tableNames).contains("account", "category", "transaction", "budget", "users", "flyway_schema_history");
        }
    }
}
