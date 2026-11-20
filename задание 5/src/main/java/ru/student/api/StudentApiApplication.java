package ru.student.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StudentApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(StudentApiApplication.class, args);
    }
    @org.springframework.context.annotation.Bean
    public org.springframework.boot.ApplicationRunner databaseSettings(javax.sql.DataSource source) {
        return args -> {
            try (var connection = source.getConnection()) {
                var h2 = connection.unwrap(org.h2.jdbc.JdbcConnection.class);
                var session = (org.h2.engine.SessionLocal) h2.getSession();
                session.getDatabase().setWriteDelay(0);
            }
        };
    }
}
