package ru.student.api.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component
@Profile("init")
public class DatabaseInit implements CommandLineRunner {
    private final DataSource source;
    private final Environment env;
    public DatabaseInit(DataSource source, Environment env) {
        this.source = source;
        this.env = env;
    }
    public void run(String... args) throws Exception {
        String user = env.getRequiredProperty("DB_USER");
        String password = env.getRequiredProperty("DB_PASSWORD");
        if (!user.matches("[a-zA-Z][a-zA-Z0-9_]*") || user.equalsIgnoreCase("sa") || password.isBlank()) {
            throw new IllegalArgumentException("Укажите отдельного пользователя и непустой пароль");
        }
        try (Connection connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE USER IF NOT EXISTS " + user + " PASSWORD '" + password.replace("'", "''") + "'");
                for (String table : new String[]{"users", "recycling_types", "collection_points", "point_types", "recycling_batches", "bonus_entries", "app_lock"}) {
                    statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + table + " TO " + user);
                }
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM seed_marker")) {
                    rows.next();
                    if (rows.getInt(1) == 0) {
                        ScriptUtils.executeSqlScript(connection, new ClassPathResource("seed.sql"));
                        statement.execute("INSERT INTO seed_marker VALUES(1)");
                    }
                }
            }
        }
        System.out.println("База подготовлена");
        System.exit(0);
    }
}
