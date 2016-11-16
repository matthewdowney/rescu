/*
 * Copyright (C) 2013 Matija Mazi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package si.mazi.rescu;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Matija Mazi <br>
 */
public final class AnnotationUtils {

    private AnnotationUtils() throws InstantiationException {
        throw new InstantiationException("This class is not for instantiation");
    }

    static <T extends Annotation> String getValueOrNull(Class<T> annotationClass, Annotation ann) {

        if (!annotationClass.isInstance(ann)) {
            return null;
        }
        try {
            return (String) ann.getClass().getMethod("value").invoke(ann);
        } catch (Exception e) {
            throw new RuntimeException("Can't access element 'value' in  " + annotationClass + ". This is probably a bug in rescu.", e);
        }
    }

    static <A extends Annotation> A getFromMethodOrClass(Method method, Class<A> annotationClass) {

        A methodAnn = method.getAnnotation(annotationClass);
        if (methodAnn != null) {
            return methodAnn;
        }
        for (Class<?> cls = method.getDeclaringClass(); cls != null; cls = cls.getSuperclass()) {
            if (cls.isAnnotationPresent(annotationClass)) {
                return cls.getAnnotation(annotationClass);
            }
        }
        for (Class<?> intf : method.getDeclaringClass().getInterfaces()) {
            if (intf.isAnnotationPresent(annotationClass)) {
                return intf.getAnnotation(annotationClass);
            }
        }
        return null;
    }
    
    static <A extends Annotation> A[] getAllFromMethodAndClass(Method method, Class<A> annotationClass) {
      A[] annotations = method.getDeclaredAnnotationsByType(annotationClass);
      for (Class<?> cls = method.getDeclaringClass(); cls != null; cls = cls.getSuperclass()) {
        annotations = Utils.arrayConcat(annotations, cls.getDeclaredAnnotationsByType(annotationClass));
      }
      for (Class<?> intf : method.getDeclaringClass().getInterfaces()) {
        annotations = Utils.arrayConcat(annotations, intf.getDeclaredAnnotationsByType(annotationClass));
      }
      return annotations;
    }

    /**
     * @return a map of annotations on the method that belong to the given collection.
     */
    static Map<Class<? extends Annotation>, Annotation> getMethodAnnotationMap(Method method, Collection<Class<? extends Annotation>> annotationClasses) {
        Annotation[] methodAnnotations = method.getAnnotations();
        Map<Class<? extends Annotation>, Annotation> methodAnnotationMap = new HashMap<>();
        for (Annotation methodAnnotation : methodAnnotations) {
            methodAnnotationMap.put(methodAnnotation.annotationType(), methodAnnotation);
        }
        methodAnnotationMap.keySet().retainAll(annotationClasses);
        return methodAnnotationMap;
    }

    public static InjectableParam[] getUniqueInjectablesInClass(Class<? extends RestInterface> restInterface) {
      // Get all injectables in the class
      InjectableParam[] injectables = restInterface.getAnnotationsByType(InjectableParam.class);
      for (Method m : restInterface.getMethods()) {
        InjectableParam[] methodInjectables = m.getAnnotationsByType(InjectableParam.class);
        injectables = Utils.arrayConcat(injectables, methodInjectables);
      }

      // Guarantee that any repeated names will be right next to each other
      Arrays.sort(injectables, (i1, i2) -> i1.name().compareTo(i2.name()));

      // Remove duplicates
      InjectableParam[] uniqueInjectablesByName = new InjectableParam[injectables.length];
      int j = 0;
      for (int i = 0; i < injectables.length; ++i) {
        while (i+1 < injectables.length && injectables[i+1].name().equals(injectables[i].name())) ++i;
        uniqueInjectablesByName[j++] = injectables[i];
      }
      uniqueInjectablesByName = Arrays.copyOfRange(uniqueInjectablesByName, 0, j);
      return uniqueInjectablesByName;
    }
}
