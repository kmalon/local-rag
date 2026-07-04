package pl.km;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.km.config.ChunkingProperties;
import pl.km.config.QueryProperties;

@SpringBootApplication
@EnableConfigurationProperties({ChunkingProperties.class, QueryProperties.class})
public class LocalRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalRagApplication.class, args);
    }
}
