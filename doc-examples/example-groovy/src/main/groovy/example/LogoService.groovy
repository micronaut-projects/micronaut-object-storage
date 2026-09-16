package example

import io.micronaut.core.io.ResourceResolver
import jakarta.inject.Singleton

//tag::class[]
@Singleton
class LogoService {

    private final ResourceResolver resourceResolver

    LogoService(ResourceResolver resourceResolver) { // <1>
        this.resourceResolver = resourceResolver
    }

    Optional<InputStream> retrieveLogo() {
        resourceResolver.getResourceAsStream("pictures://avatars/logo.png") // <2>
    }
}
//end::class[]
