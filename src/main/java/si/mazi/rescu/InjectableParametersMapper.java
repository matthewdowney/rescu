package si.mazi.rescu;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores a map of parameter names and corresponding {@link Injector}s, which
 * return a value via a {@link Injector#get()} method which should be annotated
 * just as the injected param would.
 * 
 * @author matthew
 *
 */
public class InjectableParametersMapper {

  public interface Injector<T> {
    T get();
  }

  public static class InjectableParametersBuilder {
    private final HashMap<String, Injector<?>> injectors = new HashMap<>();
    private final Class<? extends RestInterface> clazz;

    /**
     * Create a builder for an {@link InjectableParametersMapper} intended for
     * the specified interface.
     * 
     * @param interfaceClass
     *          The interface in which parameters will be injected.
     */
    public InjectableParametersBuilder(Class<? extends RestInterface> interfaceClass) {
      this.clazz = interfaceClass;
    }

    /**
     * Provide instructions for injecting a parameter.
     * 
     * @param paramName
     *          The name of the parameter to inject (specified in the
     *          interface).
     * @param injector
     *          An instance of a class implementing {@link Injector}. The
     *          {@link Injector#get()} method should be annotated with the
     *          annotations described in the interface into which the parameter
     *          will be injected.
     * @return
     */
    public <T> InjectableParametersBuilder add(String paramName, Injector<T> injector) {
      injectors.put(paramName, injector);
      return this;
    }

    /**
     * Create an instance of {@link InjectableParametersMapper} based on the
     * {@link Injector}s that have been added.
     * 
     * @return
     */
    public InjectableParametersMapper build() {
      return new InjectableParametersMapper(injectors, clazz);
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(InjectableParametersMapper.class);
  private final HashMap<String, Injector<?>> injectors;

  // should only be called from the builder
  private InjectableParametersMapper(HashMap<String, Injector<?>> injectors,
      Class<? extends RestInterface> restInterface) {
    this.injectors = injectors;

    // Verify that each method has the injectors that it needs and do type
    // checking
    for (Method interfaceMethod : restInterface.getDeclaredMethods()) {
      checkMethodInjectors(interfaceMethod, restInterface);
    }
  }

  private void checkMethodInjectors(Method method, Class<? extends RestInterface> restInterface) {
    // The injectable parameters defined in the interface
    InjectableParam[] definedInjectables = AnnotationUtils.getInjectablesFromMethodAndClass(method);
    for (InjectableParam definedInjectable : definedInjectables) {
      // Verify that an injector exists
      if (!injectors.containsKey(definedInjectable.name())) {
        throw new IllegalArgumentException("The injectors supplied for the interface " + restInterface.getSimpleName()
            + " do not include one for " + definedInjectable.name());
      }

      // Make sure the get method for the injector corresponds to what the interface is expecting
      Injector<?> providedInjector = injectors.get(definedInjectable.name());
      try {
        Method providedInjectorGetter = providedInjector.getClass().getDeclaredMethod("get", new Class<?>[0]);
        checkInjectorGetter(providedInjectorGetter, definedInjectable, restInterface);
      } catch (NoSuchMethodException | SecurityException e) {
        throw new IllegalArgumentException(
            "Provided injector for " + definedInjectable.name() + " doesn't have an accessible get method");
      }
    }
  }

  private void checkInjectorGetter(Method getter, InjectableParam definedInjectable,
      Class<? extends RestInterface> restInterface) {
    // Verify that the return type matches
    String expectedName = definedInjectable.name();
    if (!definedInjectable.type().isAssignableFrom(getter.getReturnType())
        && !getter.getReturnType().isAssignableFrom(Object.class)) {
      throw new IllegalArgumentException("The injector for " + expectedName
          + " doesn't return a value that can be assigned to the one defined in " + restInterface.getSimpleName()
          + ".\n" + "(Interface asks for " + definedInjectable.type().getSimpleName() + " and the injector supplies "
          + getter.getReturnType().getSimpleName() + ").");
    }

    Annotation[] providedAnnotations = getter.getDeclaredAnnotations();
    for (Class<? extends Annotation> expectedAnnotationType : definedInjectable.annotations()) {
      // Verify that the annotation is present
      Optional<Annotation> expected = getAnnotationOfClass(expectedAnnotationType, providedAnnotations);
      if (!expected.isPresent()) {
        String annotationNames = Arrays.stream(providedAnnotations).map(a -> a.annotationType().getSimpleName())
            .reduce("[", (s1, s2) -> s1 + ", " + s2) + "]";
        throw new IllegalArgumentException(
            "Provided injector for " + expectedName + " doesn't have the required annotation (expected="
                + expectedAnnotationType.getSimpleName() + ", found=" + annotationNames + ")");
      }

      // If it's a HeaderParam, QueryParam, or PathParam, verify that the
      // name is correct
      Annotation providedAnnotation = expected.get();
      checkAnnotationName(expectedName, HeaderParam.class, HeaderParam::value, providedAnnotation);
      checkAnnotationName(expectedName, QueryParam.class, QueryParam::value, providedAnnotation);
      checkAnnotationName(expectedName, PathParam.class, PathParam::value, providedAnnotation);
    }
  }

  private <T> void checkAnnotationName(String expectedName, Class<T> clazz, Function<T, String> nameExtractor,
      Annotation providedAnnotation) {
    if (clazz.isInstance(providedAnnotation)) {
      String providedName = nameExtractor.apply((T) providedAnnotation);
      if (!providedName.equals(expectedName)) {
        throw new IllegalArgumentException("Provided injector for " + expectedName
            + " doesn't have the expected name (expected=" + expectedName + ", found=" + providedName + ")");
      }
    }
  }

  private <T> Optional<Annotation> getAnnotationOfClass(Class<T> clazz, Annotation[] providedAnnotations) {
    for (Annotation a : providedAnnotations) {
      if (clazz.isInstance(a)) {
        return Optional.of(a);
      }
    }
    return Optional.empty();
  }

  public Annotation[] getAnnotations(String name) {
    Injector<?> injector = injectors.get(name);
    try {
      return injector.getClass().getDeclaredMethod("get", new Class<?>[] {}).getAnnotations();
    } catch (NoSuchMethodException | SecurityException e) {
      logger.error("Failed to find annotations on injector's get method, returning empty array");
      return new Annotation[] {};
    }
  }

  public Object getParam(String name) {
    return injectors.get(name) == null ? null : injectors.get(name).get();
  }

}
