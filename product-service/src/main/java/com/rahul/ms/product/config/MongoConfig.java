package com.rahul.ms.product.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MongoConfig {

    @Bean
    @Primary
    public MongoClient mongoClient() {
        String uri = System.getenv().getOrDefault(
                "MONGODB_URI",
                "mongodb://root:password@localhost:27017/product-service?authSource=admin"
        );

        ConnectionString connectionString = new ConnectionString(uri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();

        return MongoClients.create(settings);
    }
}
