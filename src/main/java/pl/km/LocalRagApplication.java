package pl.km;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.km.config.ChunkingProperties;
import pl.km.config.QueryProperties;
import pl.km.config.RerankerProperties;

@SpringBootApplication
@EnableConfigurationProperties({ChunkingProperties.class, QueryProperties.class, RerankerProperties.class})
public class LocalRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalRagApplication.class, args);
    }
}
