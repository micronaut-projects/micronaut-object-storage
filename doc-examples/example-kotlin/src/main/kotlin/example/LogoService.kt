package example

import io.micronaut.core.io.ResourceResolver
import jakarta.inject.Singleton
import java.io.InputStream
import java.util.Optional

//tag::class[]
@Singleton
open class LogoService(private val resourceResolver: ResourceResolver) { // <1>

    open fun retrieveLogo(): Optional<InputStream> {
        return resourceResolver.getResourceAsStream("pictures://avatars/logo.png") // <2>
    }
}
//end::class[]
