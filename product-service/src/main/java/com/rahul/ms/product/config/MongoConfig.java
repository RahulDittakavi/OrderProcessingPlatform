package com.rahul.ms.product.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MongoConfig {

    @Bean
    @Primary
    public MongoClient mongoClient(
            @Value("${MONGODB_URI:}") String configuredUri,
            @Value("${spring.data.mongodb.host:localhost}") String host,
            @Value("${spring.data.mongodb.port:27017}") int port,
            @Value("${spring.data.mongodb.database:product-service}") String database,
            @Value("${spring.data.mongodb.username:root}") String username,
            @Value("${spring.data.mongodb.password:password}") String password,
            @Value("${spring.data.mongodb.authentication-database:admin}") String authenticationDatabase) {
        String uri = configuredUri.isBlank()
                ? "mongodb://" + username + ":" + password + "@" + host + ":" + port + "/" + database
                    + "?authSource=" + authenticationDatabase
                : configuredUri;

        ConnectionString connectionString = new ConnectionString(uri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();

        return MongoClients.create(settings);
    }
}
