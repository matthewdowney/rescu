package si.mazi.rescu;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.FormParam;
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
public class InjectableParametersMapper<T extends RestInterface> {

  public static interface Injector<T> {
    T get();
  }

  public static class InjectableParametersBuilder<T extends RestInterface> {
    private final HashMap<String, Injector<?>> injectors = new HashMap<>();
    private final Class<T> clazz;

    /**
     * Create a builder for an {@link InjectableParametersMapper} intended for
     * the specified interface.
     * 
     * @param interfaceClass
     *          The interface in which parameters will be injected.
     */
    public InjectableParametersBuilder(Class<T> interfaceClass) {
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
    public <K> InjectableParametersBuilder<T> add(String paramName, Injector<K> injector) {
      injectors.put(paramName, injector);
      return this;
    }

    /**
     * Create an instance of {@link InjectableParametersMapper} based on the
     * {@link Injector}s that have been added.
     * 
     * @return
     */
    public InjectableParametersMapper<T> build() {
      return new InjectableParametersMapper<T>(injectors, clazz);
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(InjectableParametersMapper.class);
  private final HashMap<String, Injector<?>> injectors;

  // should only be called from the builder
  private InjectableParametersMapper(HashMap<String, Injector<?>> injectors,
      Class<T> restInterface) {
    this.injectors = injectors;
    
    // Make sure there aren't superfluous injectors
    InjectableParam[] definedInjectables = AnnotationUtils.getUniqueInjectablesInClass(restInterface);
    List<String> names = Arrays.stream(definedInjectables).map(InjectableParam::name).collect(Collectors.toList());
    for (String name : injectors.keySet()) {
      if (names.stream().noneMatch(n -> n.equals(name))) {
        throw new IllegalArgumentException("Supplied injector with name=" + name + " doesn't correspond to an " + InjectableParam.class.getSimpleName() + " defined in " + restInterface.getSimpleName());
      }
    }
    
    // Verify that each method has the injectors that it needs and do type
    // checking
    for (Method interfaceMethod : restInterface.getDeclaredMethods()) {
      checkMethodInjectors(interfaceMethod, restInterface);
    }
  }

  private void checkMethodInjectors(Method method, Class<T> restInterface) {
    // The injectable parameters defined at the interface level and from the method
    InjectableParam[] definedInjectables = AnnotationUtils.getAllFromMethodAndClass(method, InjectableParam.class);

    // Make sure the InjectableParams for this method (and those inherited from the class) have unique names
    String[] names = Arrays.stream(definedInjectables).map(InjectableParam::name).sorted().toArray(String[]::new);
    String prevName = "";
    for (String name : names) {
      if (name.equals(prevName)) {
        throw new IllegalArgumentException("The interface " + restInterface.getSimpleName() + 
            " defines multiple " + InjectableParam.class.getSimpleName() + 
            "s with the same name (" + name + ") for the method " + method.getName() + ". These names should be unique.");
      }
      prevName = name;
    }

    for (InjectableParam definedInjectable : definedInjectables) {
      // Verify that an injector exists
      if (!injectors.containsKey(definedInjectable.name())) {
        throw new IllegalArgumentException("The injectors supplied for the interface " + restInterface.getSimpleName()
            + " do not include one for " + definedInjectable.name());
      }

      // Make sure the get method for the injector corresponds to what the
      // interface is expecting
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
      Class<T> restInterface) {
    Annotation[] providedAnnotations = getter.getDeclaredAnnotations();

    // Verify that the return type matches
    String expectedName = definedInjectable.name();
    if (!definedInjectable.type().isAssignableFrom(getter.getReturnType())
        && !getter.getReturnType().isAssignableFrom(Object.class)) {
      throw new IllegalArgumentException("The injector for " + expectedName
          + " doesn't return a value that can be assigned to the one defined in " + restInterface.getSimpleName()
          + ".\n" + "(Interface asks for " + definedInjectable.type().getSimpleName() + " and the injector supplies "
          + getter.getReturnType().getSimpleName() + ").");
    }
    
    // Verify that all annotations are present and have the expected names/values
    for (Class<? extends Annotation> expectedAnnotationType : definedInjectable.annotations()) {
      // Verify that the annotation is present
      Optional<Annotation> expected = getAnnotationOfClass(expectedAnnotationType, providedAnnotations);
      if (!expected.isPresent()) {
        throw new IllegalArgumentException(
            "Provided injector for " + expectedName + " doesn't have the required annotation (expected="
                + expectedAnnotationType.getSimpleName() + ", found=" + annotationsToString(providedAnnotations) + ")");
      }

      // If it's a HeaderParam, QueryParam, PathParam, or FormParam, verify that the
      // name is correct
      Annotation providedAnnotation = expected.get();
      checkAnnotationName(expectedName, HeaderParam.class, HeaderParam::value, providedAnnotation);
      checkAnnotationName(expectedName, QueryParam.class, QueryParam::value, providedAnnotation);
      checkAnnotationName(expectedName, PathParam.class, PathParam::value, providedAnnotation);
      checkAnnotationName(expectedName, FormParam.class, FormParam::value, providedAnnotation);
    }
    
    // Verify that only the annotations defined in the interface are provided
    if (providedAnnotations.length > definedInjectable.annotations().length) {
      throw new IllegalArgumentException(
          "Extra annotations provided! Expected=" + annotationClassesToString(definedInjectable.annotations())
              + " but found=" + annotationsToString(providedAnnotations));
    }
  }

  private String annotationsToString(Annotation[] providedAnnotations) {
    return "[" + Arrays.stream(providedAnnotations).map(Annotation::annotationType).map(Class::getSimpleName).reduce(
        (s1, s2) -> s1 + ", " + s2).orElse("").trim() + "]";
  }

  private String annotationClassesToString(Class<? extends Annotation>[] annotationClasses) {
    return "[" + Arrays.stream(annotationClasses).map(Class::getSimpleName).reduce(
        (s1, s2) -> s1 + ", " + s2).orElse("").trim() + "]";
  }

  private <K> void checkAnnotationName(String expectedName, Class<K> clazz, Function<K, String> nameExtractor,
      Annotation providedAnnotation) {
    if (clazz.isInstance(providedAnnotation)) {
      String providedName = nameExtractor.apply(clazz.cast(providedAnnotation));
      if (!providedName.equals(expectedName)) {
        throw new IllegalArgumentException("Provided injector for " + expectedName
            + " doesn't have the expected name (expected=" + expectedName + ", found=" + providedName + ")");
      }
    }
  }

  private <K> Optional<Annotation> getAnnotationOfClass(Class<K> clazz, Annotation[] providedAnnotations) {
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
