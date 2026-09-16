package example;

import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;

/**
 * TODO(python): evaluating the conditions of the eager ({@code @Context}) beans of the provider
 * modules resolves the object storage beans, and with them the Python {@code BeanCreatedEventListener}
 * customizers, before the GraalPy {@code @Context} bean is initialized ("GraalPy context has not been
 * initialized"). The type converter registrars are created before the eager beans, so injecting the
 * GraalPy context into this registrar makes sure the runtime is installed before the first Python
 * bean is instantiated.
 */
@Singleton
public class PythonRuntimeInitializer implements TypeConverterRegistrar {

    public PythonRuntimeInitializer(@Named("python") Context graalPyContext) {
        // the injection of the GraalPy context is all that is needed
    }

    @Override
    public void register(MutableConversionService conversionService) {
        // nothing to register
    }
}
