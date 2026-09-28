from jakarta.inject import Singleton
from java.io import InputStream
from micronaut.core.io import ResourceResolver


# tag::class[]
@Singleton
class LogoService:

    def __init__(self, resource_resolver: ResourceResolver):  # <1>
        self.resource_resolver = resource_resolver

    def retrieve_logo(self) -> InputStream | None:
        return self.resource_resolver.getResourceAsStream("pictures://avatars/logo.png").orElse(None)  # <2>
# end::class[]
