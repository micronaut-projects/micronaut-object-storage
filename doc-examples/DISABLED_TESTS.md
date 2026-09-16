# Python Docs Disabled Test Inventory

This file tracks the Python documentation examples under `doc-examples/example-python` that are
disabled, reduced, or carry a workaround because the direct port of the Java example does not compile
or does not behave like the Java example yet. It is the bug-fixing task list for the Python compiler
(`micronaut-inject-python` / `micronaut-context-python`); every row references a `TODO(python)`
comment in the sources or the build.

The Python examples are compiled by every build and their tests run with
`./gradlew pythonCheck -Ppython-ci` (the "Python CI" GitHub workflow).

## Migration Rules

- Do not define local copies of Micronaut annotation helpers or custom annotation shims in docs
  snippets. Standard Micronaut and library annotations are imported from their Java package
  (`micronaut.objectstorage.request`, `micronaut.context.event`, `software.amazon.awssdk...`, ...).
- Do not add Java-style getters or setters to Python docs models. Prefer `@dataclass` models with
  idiomatic Python attributes; attribute names are used verbatim as JSON property names, so models
  use the same camelCase names as the Java examples.
- A Python test class is a `@MicronautTest` with `@Test` methods and plain `assert` statements.
- The main and test Python sources of the project are merged into one source root compiled with the
  tests (`compilePython` is disabled in `doc-examples/example-python/build.gradle.kts`), because two
  GraalPy virtual file systems on the same classpath shadow each other's generated shims and imports
  are only resolved within a source root.

## Active `@Disabled` Tests

None.

## Workarounds

| Location | Reason |
| --- | --- |
| `doc-examples/example-python/src/test/java/example/LocalStackTestConfigurer.java` | Micronaut Test calls `TestPropertyProvider.getProperties()` before the application context, and with it the GraalPy runtime, exists, so a Python test class cannot start the LocalStack container and provide its S3 endpoint and credentials. The Java `@ContextConfigurer` (`ApplicationContextConfigurer.configure(ApplicationContext)`, gated on the `ec2` environment) does it for the `ProfileServiceTest`, `UploadControllerTest` and `LogoServiceTest` Python tests. |
| `doc-examples/example-python/src/test/java/example/PythonRuntimeInitializer.java` | Evaluating the conditions of the eager (`@Context`) beans of the provider modules (for example `ManagedNettyHttpProvider` of micronaut-oraclecloud) resolves the object storage beans, and with them the Python `BeanCreatedEventListener` customizers, before the GraalPy `@Context` bean is initialized (`GraalPy context has not been initialized`). A Java `TypeConverterRegistrar` injecting the `@Named("python") Context` is created before the eager beans and installs the runtime first. |
| `doc-examples/example-python/src/main/python/example/oraclecloud/ObjectStorageClientBuilderCustomizer.py` | Class attributes without a type annotation (`CONNECTION_TIMEOUT_IN_MILLISECONDS = 25000`) make the stub generation of every class of the compilation fail with `NullPointerException: ... FieldElement.getType() is null`; the constants are declared as `CONNECTION_TIMEOUT_IN_MILLISECONDS: int = 25000`. |
| `doc-examples/example-python/src/test/python/example/ObjectStorageClientBuilderCustomizerTest.py` | The Groovy test reads the protected `ClientBuilderBase.configuration` field through Groovy's field access; the Python test reads it with Java reflection (`Class.forName(...).getDeclaredField(...)`). Not a compiler gap. |
| `doc-examples/example-python/src/test/python/example/BlobServiceClientBuilderCustomizerTest.py` | The no-op `HttpPipelinePolicy` of the Python customizer is a Python lambda; the policies come back to Python as the lambda itself, so the test looks for a `<lambda>` policy in the pipeline instead of a class named after the customizer. Not a compiler gap. |
