package com.rahul.ms.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductServiceApplicationTests {

	@Value("${spring.data.mongodb.uri}")
	private String mongoUri;

	@Test
	void contextLoads() {
	}

	@Test
	void mongoUriMustUseAuthenticatedAdminConnection() {
		assertThat(mongoUri)
				.contains("root:password")
				.contains("@localhost:27017")
				.contains("authSource=admin");
	}

}
