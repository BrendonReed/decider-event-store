package decider.event.store.config;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class PubSubConnectionConfig {
    @Value("${spring.r2dbc.url}")
    private String r2dbcUrl;

    @Value("${spring.r2dbc.username}")
    private String r2dbcUsername;

    @Value("${spring.r2dbc.password}")
    private String r2dbcPassword;

    @Bean
    public PubSubConnection pubSubBean() {
        URI uri = URI.create(r2dbcUrl.replace("r2dbc:postgresql", "postgresql"));
        String host = uri.getHost();
        int port = uri.getPort();

        var cf = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(host)
                .port(port)
                .username(r2dbcUsername)
                .password(r2dbcPassword)
                .database(uri.getPath().substring(1)) // Removes leading '/'
                // Additional configuration if needed
                .build());
        return new PubSubConnection(cf);
    }
}
