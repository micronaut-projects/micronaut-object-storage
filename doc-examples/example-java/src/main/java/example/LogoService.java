package example;

import io.micronaut.core.io.ResourceResolver;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.util.Optional;

//tag::class[]
@Singleton
public class LogoService {

    private final ResourceResolver resourceResolver;

    public LogoService(ResourceResolver resourceResolver) { // <1>
        this.resourceResolver = resourceResolver;
    }

    public Optional<InputStream> retrieveLogo() {
        return resourceResolver.getResourceAsStream("pictures://avatars/logo.png"); // <2>
    }
}
//end::class[]
